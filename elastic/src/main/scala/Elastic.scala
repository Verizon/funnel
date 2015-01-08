package oncue.svc.funnel
package elastic

import scalaz.stream._
import scalaz.\/
import \/._
import Mapping._
import knobs.IORef

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
                      ttl: Option[Int] = None)

object Elastic {
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
  import dispatch._, Defaults._
  import scalaz.\/
  import concurrent.{Future,ExecutionContext}

  implicit def fromScalaFuture[A](a: Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatchThrowable[A,Throwable](t.get)) }}

  def initMapping(es: ElasticCfg, keys: List[Key[Any]])(
    implicit log: String => Unit): Task[Unit] = {
    val json = Json(
      es.typeName := jObjectAssocList(List(
        "_timestamp" -> Json("enabled" := true),
        "properties" -> Properties(List(
          Field("host", StringField),
          Field("cluster", StringField)) ++ (keys map keyField)).asJson) ++
      (es.ttl.map(t => List(
          "_ttl" -> Json("enabled" := true, "default" := s"${t}d"))) getOrElse Nil)))
    elasticPut(mappingURL(es), json, es)
  }

  def elasticPut(req: Req, json: Json, es: ElasticCfg)(
    implicit log: String => Unit): Task[Unit] =
    fromScalaFuture(Http((req << json.nospaces) OK as.String)).attempt.map(
      _.fold(e => {log(s"Unable to send document to elastic search due to '$e'.")
                   log(s"Configuration was $es. Document was: \n ${json.nospaces}")}, _ => ()))

  def updateMapping(ps: Properties, es: ElasticCfg)(
    implicit log: String => Unit): Task[Unit] = {
    val json = Mappings(List(Mapping(es.typeName, ps))).asJson
    elasticPut(mappingURL(es), json, es)
  }

  def keyField(k: Key[Any]): Field = {
    import Reportable._
    val path = k.name.split("/")
    val last = path.last
    val init = path.init
    init.foldRight(
      Field(last, k typeOf match {
        case B => BoolField
        case D => DoubleField
        case S => StringField
        case Stats =>
          ObjectField(Properties(List(
            "last", "mean", "count", "variance", "skewness", "kurtosis"
          ).map(Field(_, DoubleField))))
      }))((a, r) => Field(a, ObjectField(Properties(List(r)))))
  }

  def mappingURL(es: ElasticCfg): Req =
    url(s"${es.url}/${es.indexName}/_mapping/${es.typeName}").
      setContentType("application/json", "UTF-8")

  def esURL(es: ElasticCfg): Req =
    url(s"${es.url}/${es.indexName}/${es.typeName}").
      setContentType("application/json", "UTF-8")

  /**
   * Publishes to an ElasticSearch URL at `esURL`.
   */
  def publish(M: Monitoring, es: ElasticCfg, flaskName: String)(
    implicit log: String => Unit): Task[Unit] = for {
      _   <- M.keys.continuous.once.evalMap(x => initMapping(es, x)).run
      ref <- IORef(Set[Key[Any]]())
      -   <- (Monitoring.subscribe(M)(k => k.attributes.contains("host") ||
                                    k.name.matches("previous/.*")) |>
               elasticGroup |> elasticUngroup(flaskName)).evalMap(_.fold(
                 props => updateMapping(props, es),
                 json => elasticPut(esURL(es), json, es)
               )).run
    } yield ()
}

