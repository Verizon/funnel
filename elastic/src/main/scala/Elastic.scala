package funnel
package elastic

import argonaut._
import journal.Logger
import java.util.Date
import scalaz.concurrent.Task
import java.text.SimpleDateFormat
import scala.util.control.NonFatal
import scalaz.{\/,Kleisli,Monad,~>,Reader}
import scala.concurrent.{Future,ExecutionContext}

object Elastic {
  import Argonaut._
  import Kleisli.ask
  import scalaz.syntax.kleisli._
  import scalaz.syntax.monad._ // pulls in *>
  import concurrent.duration._

  type SourceURL = String
  type ExperimentID = String
  type GroupID = String
  type Window = String
  type Path = List[String]
  type ES[A] = Kleisli[Task, ElasticCfg, A]

  private[this] val log = Logger[Elastic.type]

  /**
   *
   */
  def fromScalaFuture[A](a: => Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatchThrowable[A,Throwable](t.get)) }}

  /**
   *
   */
  val getConfig: ES[ElasticCfg] = ask[Task, ElasticCfg]

  /**
   * Not in Scalaz until 7.2 so duplicating here.
   */
  def lower[M[_]:Monad,A,B](k: Kleisli[M,A,B]): Kleisli[M,A,M[B]] =
    Kleisli(a => Monad[M].pure(k(a)))

  /**
   *
   */
  def lift: Task ~> ES = new (Task ~> ES) {
    def apply[A](t: Task[A]) = t.liftKleisli
  }

  /**
   *
   */
  def duration: Reader[ElasticCfg, FiniteDuration] =
    Reader { es => es.subscriptionTimeout }

  /****************************** http i/o ******************************/

  import dispatch._, Defaults._

  /**
   * sadly required as dispatch has a very naïve error handler by default,
   * and in this case we're looking to output the body of the response to the log
   * in order to help with debugging in the event documents were not able to be
   * submitted to the backend. Should function exactly like the default impl
   * with the addition of the logging.
   */
  val handler = new FunctionHandler[String]({ resp =>
    val status = resp.getStatusCode
    if((status / 100) == 2) resp.getResponseBody
    else {
      log.error(s"Backend returned a code ${resp.getStatusCode} failure. Body response was: ${resp.getResponseBody}")
      throw StatusCode(status)
    }
  })

  /**
   * given a request, and a payload, compresses the payload into a single-line
   * string and POSTs it to elastic search.
   */
  def elasticJson(req: Req, json: Json): ES[Unit] =
    elastic(req, json.nospaces)

  /**
   *
   */
  def elasticString(req: Req): ES[String] = {
    getConfig.flatMapK(c => fromScalaFuture(c.http(req > handler)))
  }

  /**
   *
   */
  def elastic(req: Req, json: String): ES[Unit] = for {
    es <- getConfig
    ta <- lower(elasticString(req << json))
    _  <- lift(ta.attempt.map(_.fold(
      e => {log.error(s"Unable to send document to elastic search due to '$e'.")
            log.error(s"Configuration was $es. Document was: \n ${json}")},
      _ => ())))
  } yield ()

  /**
   * retries any non-HTTP errors with exponential backoff
   */
  def retry[A](task: Task[A]): Task[A] = {
    val schedule = Stream.iterate(2)(_ * 2).take(30).map(_.seconds)
    val t = task.attempt.flatMap(_.fold(
      e => e match {
        case StatusCode(_) => Task.fail(e)
        case _ =>
          Task.delay(log.error(s"Error contacting ElasticSearch: ${e}.\nRetrying...")) >>=
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

  /****************************** indexing ******************************/

  /**
   *
   */
  def indexURL: Reader[ElasticCfg, Req] = Reader { es =>
    val date = new SimpleDateFormat(es.dateFormat).format(new Date)
    url(s"${es.url}/${es.indexName}-$date")
  }

  /**
   *
   */
  def esURL: Reader[ElasticCfg, Req] = Reader { es =>
    (indexURL(es) / es.typeName).setContentType("application/json", "UTF-8")
  }

  /**
   * returns true if the index was created. False if it already existed.
   */
  def ensureIndex(url: Req): ES[Boolean] =
    ensureExists(url, createIndex(url))

  /**
   * ensure the index we are trying to send documents too (defined in the config)
   * exists in the backend elastic search cluster.
   */
  def ensureExists(url: Req, action: ES[Unit]): ES[Boolean] = for {
    s   <- elasticString(url.HEAD).mapK(_.attempt)
    b   <- s.fold(
             e => e.getCause match {
               case StatusCode(404) => action *> lift(Task.now(true))
               case _ => lift(Task.fail(e)) // SPLODE!
             },
             _ => lift(Task.now(false)))
  } yield b

  /**
   * create an index based on the
   */
  def createIndex(url: Req): ES[Unit] = for {
    _   <- elasticJson(url.PUT, Json("settings" := Json("index.cache.query.enable" := true)))
  } yield ()

  /**
   *
   */
  def ensureTemplate: ES[Unit] = for {
    cfg <- getConfig
    template <- Task.delay(
      cfg.templateLocation.map(scala.io.Source.fromFile) getOrElse
        sys.error("no index mapping template specified.")).liftKleisli
    json <- Task.delay(template.mkString).liftKleisli
    turl = url(s"${cfg.url}") / "_template" / cfg.templateName
    _ <- ensureExists(turl, elastic(turl.PUT, json))
  } yield ()

}
