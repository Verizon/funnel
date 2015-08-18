package funnel
package elastic

import java.net.URI
import scala.util.control.NonFatal
import scala.concurrent.duration._
import scalaz.concurrent.Strategy
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

case class ElasticCfg(
  url: String,                         // Elastic URL
  indexName: String,                   // Name prefix of elastic index
  typeName: String,                    // Name of the metric type in ES
  dateFormat: String,                  // Date format for index suffixes
  templateName: String,                // Name of ES index template
  templateLocation: Option[String],    // Path to index template to use
  http: dispatch.Http,                 // HTTP driver
  groups: List[String],                // Subscription groups to publish to ES
  subscriptionTimeout: FiniteDuration, // Maximum interval for publishing to ES
  bufferSize: Int                      // Size of circular buffer in front of ES
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
    connectionTimeoutMs: Duration = 5000.milliseconds,
    bufferSize: Int = 4096
  ): ElasticCfg = {
    val driver: Http = Http.configure(
      _.setAllowPoolingConnection(true)
       .setConnectionTimeoutInMs(connectionTimeoutMs.toMillis.toInt))
    ElasticCfg(url, indexName, typeName, dateFormat,
               templateName, templateLocation, driver, groups, subscriptionTimeout, bufferSize)
  }
}

case class Elastic(M: Monitoring) {
  type SourceURL = String
  type ExperimentID = String
  type GroupID = String
  type Window = String
  type Path = List[String]

  /**
   * Data points grouped by mirror URL, experiment ID, experiment group,
   * and grouping key from config
   */
  type ESGroup[A] = Map[(String,
                         Option[SourceURL],
                         Option[ExperimentID],
                         Option[GroupID]), Map[Path, Datapoint[A]]]

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
          val experimentID = pt.key.attributes.get(AttributeKeys.experimentID)
          val experimentGroup = pt.key.attributes.get(AttributeKeys.experimentGroup)
          val grouping = groups.find(name startsWith _) getOrElse ""
          val k = name.drop(grouping.length).split("/").toList.filterNot(_ == "")
          val host = (grouping, source, experimentID, experimentGroup)
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
   *
   * For the fixed fields `uri` and `host`, if we do not have a meaningful
   * value for this, we fallback to assuming this is coming from the local
   * monitoring instance, so just use the supplied flask name.
   */
  def elasticUngroup[A](flaskName: String, flaskCluster: String): Process1[ESGroup[A], Json] =
    await1[ESGroup[A]].flatMap { g =>
      emitAll(g.toSeq.map { case (name, m) =>
        ("uri" := name._2.getOrElse(flaskName)) ->:
        ("host" := name._2.map(u => (new URI(u)).getHost).getOrElse(flaskName)) ->:
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

  /** sadly required as dispatch has a very naieve error handler by default,
      and in this case we're looking to output the body of the response to the log
      in order to help with debugging in the event documents were not able to be
      submitted to the backend. Should function exactly like the default impl
      with the addition of the logging. */
  val handler = new FunctionHandler[String]({ resp =>
    val status = resp.getStatusCode
    if((status / 100) == 2) resp.getResponseBody
    else {
      M.log.error(s"Backend returned a code ${resp.getStatusCode} failure. Body response was: ${resp.getResponseBody}")
      throw StatusCode(status)
    }
  })

  def elasticString(req: Req): ES[String] = {
    getConfig.flatMapK(c => fromScalaFuture(c.http(req > handler)))
  }

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

  // Retries any non-HTTP errors with exponential backoff
  def retry[A](task: Task[A]): Task[A] = {
    val schedule = Stream.iterate(2)(_ * 2).take(30).map(_.seconds)
    val t = task.attempt.flatMap(_.fold(
      e => e match {
        case StatusCode(_) => Task.fail(e)
        case _ =>
          Task.delay(M.log.error(s"Error contacting ElasticSearch: ${e}.\nRetrying...")) >>=
          (_ => Task.fail(e))
      },
      a => Task.now(a)
    ))
    t.retry(schedule, (e: Throwable) => e.getCause match {
      case StatusCode(_) => false
      case NonFatal(_) => true
      case _ => false
    })
  }

  /**
   * Publishes to an ElasticSearch URL at `esURL`.
   */
  def publish(flaskName: String, flaskCluster: String): ES[Unit] = {
    val E = Executor(Monitoring.defaultPool)
    def doPublish(de: Process[Task, Json], cfg: ElasticCfg): Process[Task, Unit] =
      de to (constant((json: Json) => for {
        r <- Task.delay(esURL(cfg))
        _ <- retry(elasticJson(r.POST, json)(cfg))
      } yield ()))
    for {
      _   <- ensureTemplate
      cfg <- getConfig
      buffer = async.circularBuffer[Json](cfg.bufferSize)(E)
      ref <- lift(IORef(Set[Key[Any]]()))
      d   <- duration.lift[Task]
      timeout = time.awakeEvery(d)(
        E,
        Monitoring.schedulingPool).map(_ => Option.empty[Datapoint[Any]])
      subscription = Monitoring.subscribe(M)(k =>
        cfg.groups.exists(g => k.startsWith(g))).map(Option.apply)
      // Reads from the monitoring instance and posts to the publishing queue
      read = timeout.wye(subscription)(wye.merge)(E) |>
               elasticGroup(cfg.groups) |>
               elasticUngroup(flaskName, flaskCluster) to
               buffer.enqueue
      // Reads from the publishing queue and writes to ElasticSearch
      write  = doPublish(buffer.dequeue, cfg)
      _ <- lift(Task.reduceUnordered[Unit, Unit](Seq(read.run, write.run), true))
    } yield ()
  }
}

