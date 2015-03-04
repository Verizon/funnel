package funnel
package elastic

import scalaz.stream._
import scalaz._
import syntax.monad._
import syntax.kleisli._
import Kleisli._
import \/._
import Mapping._
import knobs.IORef
import java.util.Date
import java.text.SimpleDateFormat
import dispatch._, Defaults._

/* Elastic Event format:
{
  "cluster": "imqa-maestro-1-0-279-F6Euts",  #This allows for a Kibana search, cluster: x
  "host": "ec2-107-22-118-178.compute-1.amazonaws.com",
  "jvm": {
    "memory": {
      "heap": {
        "committed": {
          "last": 250.99763712000001,
          "mean": 250.99763712000001,
          "standard_deviation": 0.0
        },
        "usage": {
          "last": 0.042628084023299997,
          "mean": 0.042445506024100001,
          "standard_deviation": 0.00018257799924300001
        }
      }
    }
  }
}
*/

case class ElasticCfg(url: String,
                      indexName: String,
                      typeName: String,
                      dateFormat: String,
                      http: dispatch.Http)

object ElasticCfg {
  def apply(
    url: String,
    indexName: String,
    typeName: String,
    dateFormat: String,
    connectionTimeoutMs: Int = 5000
  ): ElasticCfg = {
    val driver: Http = Http.configure(
      _.setAllowPoolingConnection(true)
       .setConnectionTimeoutInMs(connectionTimeoutMs))
    ElasticCfg(url, indexName, typeName, dateFormat, driver)
  }
}

case class Elastic(M: Monitoring) {
  type Name = String
  type Path = List[String]

  /** Data points grouped by mirror URL and key */
  type ESGroup[A] = Map[Option[Name], Map[Path, Datapoint[A]]]

  import Process._
  import scalaz.concurrent.Task
  import scalaz.Tree

  /**
   * Groups data points by key and mirror URL.
   * Emits when it receives a key/mirror where the key is already in the group for the mirror.
   * That is, emits as few times as possible without duplicates
   * and without dropping any data.
   */
  def elasticGroup[A]: Process1[Datapoint[A], ESGroup[A]] = {
    def go(m: ESGroup[A]):
    Process1[Datapoint[A], ESGroup[A]] =
      await1[Datapoint[A]].flatMap { pt =>
        val name = pt.key.name
        val host = pt.key.attributes.get("url")
        val t = name.split("/").toList
        m.get(host) match {
          case Some(g) => g.get(t) match {
            case Some(_) =>
              emit(m) ++ go(Map(host -> Map(t -> pt)))
            case None =>
              go(m + (host -> (g + (t -> pt))))
          }
          case None =>
            go(m + (host -> Map(t -> pt)))
        }
      }
    go(Map())
  }

  import argonaut._
  import Argonaut._
  import http.JSON._

  /**
   * Emits one JSON document per mirror URL, on the right,
   * first emitting the ES mapping properties for their keys, on the left.
   * Once grouped by `elasticGroup`, this process emits one document per URL
   * with all the key/value pairs that were seen for that mirror in the group.
   */
  def elasticUngroup[A](flaskName: String): Process1[ESGroup[A], Properties \/ Json] =
    await1[ESGroup[A]].flatMap { g =>
      emit(left(Properties(g.values.flatMap(_.values.map(p =>
          keyField(p.key))).toList))) ++
      emitAll(g.toSeq.map { case (name, m) =>
        ("host" := name.getOrElse(flaskName)) ->:
        ("@timestamp" :=
          new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ").format(new Date)) ->:
          m.toList.foldLeft(jEmptyObject) {
            case (o, (ps, dp)) =>
              dp.key.attributes.get("bucket").map(x => ("cluster" := x) ->: jEmptyObject).
                getOrElse(jEmptyObject) deepmerge
                  (o deepmerge ps.foldRight((dp.asJson -| "value").get)((a, b) =>
                    (a := b) ->: jEmptyObject))
          }
      }).map(right(_))
    }.repeat

  import Events._
  import scala.concurrent.duration._
  import scalaz.\/
  import scala.concurrent.{Future,ExecutionContext}

  def fromScalaFuture[A](a: => Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatchThrowable[A,Throwable](t.get)) }}

  val getConfig: ES[ElasticCfg] = ask[Task, ElasticCfg]

  type ES[A] = Kleisli[Task, ElasticCfg, A]

  def putMapping(json: Json): ES[Unit] = for {
    r <- mappingURL.lift[Task]
    _ <- elastic(r.PUT, json)
  } yield ()

  def initMapping(keys: Set[Key[Any]]): ES[Unit] = for {
    es <- getConfig
    json = Json(
      es.typeName := jObjectAssocList(List(
        "_timestamp" -> Json("enabled" := true, "store" := true),
        "properties" -> Properties(List(
          Field("host", StringField),
          Field("cluster", StringField)) ++
          (keys map keyField)).asJson)
      ))
    _ <- ensureIndex
    _ <- putMapping(json)
  } yield ()



  def elasticString(req: Req): ES[String] =
    getConfig.flatMapK(c => fromScalaFuture(c.http(req OK as.String)))

  // Not in Scalaz until 7.2 so duplicating here
  def lower[M[_]:Monad,A,B](k: Kleisli[M,A,B]): Kleisli[M,A,M[B]] =
    Kleisli(a => Monad[M].pure(k(a)))

  def elastic(req: Req, json: Json): ES[Unit] = for {
    es <- getConfig
    ta <- lower(elasticString(req << json.nospaces))
    _  <- lift(ta.attempt.map(_.fold(
      e => {M.log(s"Unable to send document to elastic search due to '$e'.")
            M.log(s"Configuration was $es. Document was: \n ${json.nospaces}")},
      _ => ())))
  } yield ()

  def updateMapping(ps: Properties): ES[Unit] = for {
    es <- getConfig
    json = Mappings(List(Mapping(es.typeName, ps))).asJson
    _  <- putMapping(json)
  } yield ()

  def keyField(k: Key[Any]): Field = {
    import Reportable._
    val path = k.name.split("/")
    val last = path.last
    val init = path.init
    val z = Field(last,
                  k typeOf match {
                    case B => BoolField
                    case D => DoubleField
                    case S => StringField
                    case Stats =>
                      ObjectField(Properties(List(
                        "last", "mean", "count", "variance", "skewness", "kurtosis"
                      ).map(Field(_, DoubleField))))
                  }, Json("units" := k.units, "description" := k.description))
    init.foldRight(z) { (a, r) =>
      Field(a, ObjectField(Properties(List(r))))
    }
  }

  def ensureIndex: ES[Unit] = for {
    url <- indexURL.lift[Task]
    s   <- elasticString(url.HEAD).mapK(_.attempt)
    _   <- s.fold(
             e => e.getCause match {
               case StatusCode(404) => createIndex
               case _ => lift(Task.fail(e)) // SPLODE!
             },
             _ => lift(Task.now(())))
  } yield ()

  def createIndex: ES[Unit] = for {
    url <- indexURL.lift[Task]
    _   <- elastic(url.PUT, Json("settings" := Json("index.cache.query.enable" := true)))
  } yield ()

  def indexURL: Reader[ElasticCfg, Req] = Reader { es =>
    val date = new SimpleDateFormat(es.dateFormat).format(new Date)
    url(s"${es.url}/${es.indexName}-$date")
  }

  def mappingURL: Reader[ElasticCfg, Req] = Reader { es =>
    (indexURL(es) / "_mapping" / s"${es.typeName}").
      setContentType("application/json", "UTF-8")
  }

  def esURL: Reader[ElasticCfg, Req] = Reader { es =>
    (indexURL(es) / es.typeName).setContentType("application/json", "UTF-8")
  }

  def lift: Task ~> ES = new (Task ~> ES) {
    def apply[A](t: Task[A]) = t.liftKleisli
  }

  /**
   * Publishes to an ElasticSearch URL at `esURL`.
   */
  def publish(flaskName: String): ES[Unit] = for {
      _   <- M.keys.continuous.once.translate(lift).evalMap(initMapping).run
      ref <- lift(IORef(Set[Key[Any]]()))
      -   <- (Monitoring.subscribe(M)(k =>
                k.attributes.contains("host") ||
                k.name.matches("previous/.*")).translate(lift) |>
              elasticGroup |> elasticUngroup(flaskName)).evalMap(_.fold(
                props => updateMapping(props),
                json => esURL.lift[Task] >>= (r => elastic(r.POST, json))
              )).run
    } yield ()
}

