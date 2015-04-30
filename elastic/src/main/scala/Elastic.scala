package funnel
package elastic

import java.net.URI
import scala.concurrent.duration._
import scalaz.stream._
import scalaz._
import concurrent.Strategy.Executor
import syntax.monad._
import syntax.kleisli._
import Kleisli._
import \/._
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
                      templateName: String,
                      templateLocation: Option[String],
                      http: dispatch.Http,
                      groups: List[String],
            		      subscriptionTimeout: FiniteDuration
)

object ElasticCfg {
  def apply(
    url: String,
    indexName: String,
    typeName: String,
    dateFormat: String,
    templateName: String,
    templateLocation: Option[String],
    groups: List[String],
    subscriptionTimeout: FiniteDuration = 10.minutes,
    connectionTimeoutMs: Duration = 5000.milliseconds
  ): ElasticCfg = {
    val driver: Http = Http.configure(
      _.setAllowPoolingConnection(true)
       .setConnectionTimeoutInMs(connectionTimeoutMs.toMillis.toInt))
    ElasticCfg(url, indexName, typeName, dateFormat,
               templateName, templateLocation, driver, groups, subscriptionTimeout)
  }
}

case class Elastic(M: Monitoring) {
  type SourceURL = String
  type Window = String
  type Path = List[String]

  /** Data points grouped by mirror URL and key */
  type ESGroup[A] = Map[(String, Option[SourceURL]), Map[Path, Datapoint[A]]]

  import Process._
  import scalaz.concurrent.Task
  import scalaz.Tree

  /**
   * Groups data points by key, mirror URL, and custom grouping from config.
   * Emits when it receives a key/mirror where the key is already in the group for the mirror.
   * That is, emits as few times as possible without duplicates
   * and without dropping any data.
   */
  def elasticGroup[A](groups: List[String]): Process1[Option[Datapoint[A]], ESGroup[A]] = {
    def go(sawDatapoint: Boolean, m: ESGroup[A]): Process1[Option[Datapoint[A]], ESGroup[A]] =
      await1[Option[Datapoint[A]]].flatMap {
        case Some(pt) =>
          val name = pt.key.name
          val source = pt.key.attributes.get(AttributeKeys.source)
          val group = groups.find(name startsWith _) getOrElse ""
          val k = name.drop(group.length).split("/").toList.filterNot(_ == "")
          val host = (group, source)
          m.get(host) match {
            case Some(g) => g.get(k) match {
              case Some(_) =>
                emit(m) ++ go(true, Map(host -> Map(k -> pt)))
              case None =>
                go(true, m + (host -> (g + (k -> pt))))
            }
            case None =>
              go(true, m + (host -> Map(k -> pt)))
          }
        case None =>				// No Datapoint this time
          if (sawDatapoint) {			// Saw one last time
            go(false, m)			// Keep going with current Map
          } else {				// Didn't see one last time, either
            emit(m) ++ go(false, Map())		// Publish current Map
          }
      }
    go(false, Map())
  }

  import argonaut._
  import Argonaut._
  import http.JSON._

  /**
   * Emits one JSON document per mirror URL and window type, on the right,
   * first emitting the ES mapping properties for their keys, on the left.
   * Once grouped by `elasticGroup`, this process emits one document per
   * URL/window with all the key/value pairs that were seen for that mirror
   * in the group for that period.
   */
  def elasticUngroup[A](flaskName: String, flaskCluster: String): Process1[ESGroup[A], Json] =
    await1[ESGroup[A]].flatMap { g =>
      emitAll(g.toSeq.map { case (name, m) =>
        ("uri" := name._2.getOrElse(flaskName)) ->:
        ("host" := name._2.map(u => (new URI(u)).getHost)) ->:
        ("@timestamp" :=
          new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ").format(new Date)) ->:
          m.toList.foldLeft(("group" := name._1) ->: jEmptyObject) {
            case (o, (ps, dp)) =>
              val attrs = dp.key.attributes
              val kind = attrs.get(AttributeKeys.kind)
              val clust = ("cluster" :=
                attrs.get(AttributeKeys.cluster).getOrElse(flaskCluster)) ->: jEmptyObject
              clust deepmerge (o deepmerge (ps ++ kind).foldRight((dp.asJson -| "value").get)(
                (a, b) => (a := b) ->: jEmptyObject))
          }
      })
    }.repeat

  import Events._
  import scala.concurrent.duration._
  import scalaz.\/
  import scala.concurrent.{Future,ExecutionContext}
  import java.io.File

  def fromScalaFuture[A](a: => Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatchThrowable[A,Throwable](t.get)) }}

  val getConfig: ES[ElasticCfg] = ask[Task, ElasticCfg]

  type ES[A] = Kleisli[Task, ElasticCfg, A]

  def elasticString(req: Req): ES[String] =
    getConfig.flatMapK(c => fromScalaFuture(c.http(req OK as.String)))

  // Not in Scalaz until 7.2 so duplicating here
  def lower[M[_]:Monad,A,B](k: Kleisli[M,A,B]): Kleisli[M,A,M[B]] =
    Kleisli(a => Monad[M].pure(k(a)))

  def elasticJson(req: Req, json: Json): ES[Unit] =
    elastic(req, json.nospaces)

  def elastic(req: Req, json: String): ES[Unit] = for {
    es <- getConfig
    ta <- lower(elasticString(req << json))
    _  <- lift(ta.attempt.map(_.fold(
      e => {M.log.error(s"Unable to send document to elastic search due to '$e'.")
            M.log.error(s"Configuration was $es. Document was: \n ${json}")},
      _ => ())))
  } yield ()

  // Returns true if the index was created. False if it already existed.
  def ensureIndex(url: Req): ES[Boolean] = ensureExists(url, createIndex(url))

  def ensureExists(url: Req, action: ES[Unit]): ES[Boolean] = for {
    s   <- elasticString(url.HEAD).mapK(_.attempt)
    b   <- s.fold(
             e => e.getCause match {
               case StatusCode(404) => action *> lift(Task.now(true))
               case _ => lift(Task.fail(e)) // SPLODE!
             },
             _ => lift(Task.now(false)))
  } yield b

  def createIndex(url: Req): ES[Unit] = for {
    _   <- elasticJson(url.PUT, Json("settings" := Json("index.cache.query.enable" := true)))
  } yield ()

  def indexURL: Reader[ElasticCfg, Req] = Reader { es =>
    val date = new SimpleDateFormat(es.dateFormat).format(new Date)
    url(s"${es.url}/${es.indexName}-$date")
  }

  def esURL: Reader[ElasticCfg, Req] = Reader { es =>
    (indexURL(es) / es.typeName).setContentType("application/json", "UTF-8")
  }

  def lift: Task ~> ES = new (Task ~> ES) {
    def apply[A](t: Task[A]) = t.liftKleisli
  }

  def ensureTemplate: ES[Unit] = for {
    cfg <- getConfig
    template <- Task.delay(
      cfg.templateLocation.map(scala.io.Source.fromFile) getOrElse
        scala.io.Source.fromInputStream(
          getClass.getResourceAsStream("/oncue/elastic-template.json"))).liftKleisli
    json <- Task.delay(template.mkString).liftKleisli
    turl = url(s"${cfg.url}") / "_template" / cfg.templateName
    _ <- ensureExists(turl, elastic(turl.PUT, json))
  } yield ()

  def duration: Reader[ElasticCfg, FiniteDuration] = Reader { es => es.subscriptionTimeout }

  /**
   * Publishes to an ElasticSearch URL at `esURL`.
   */
  def publish(flaskName: String, flaskCluster: String): ES[Unit] = for {
      _   <- ensureTemplate
      cfg <- getConfig
      ref <- lift(IORef(Set[Key[Any]]()))
      d   <- duration.lift[Task]
      timeout = Process.awakeEvery(d)(Executor(Monitoring.serverPool), Monitoring.schedulingPool).map(_ => Option.empty[Datapoint[Any]])
      subscription = Monitoring.subscribe(M)(k => cfg.groups.exists(g => k.startsWith(g))).map(Option.apply)
      -   <- (timeout.wye(subscription)(wye.merge).translate(lift) |>
              elasticGroup(cfg.groups) |> elasticUngroup(flaskName, flaskCluster)).evalMap(
                json  => esURL.lift[Task] >>= (r => elasticJson(r.POST, json))
              ).run
    } yield ()
}

