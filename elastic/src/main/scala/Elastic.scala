package intelmedia.ws.funnel
package elastic

import scalaz.stream._

/* Elastic Event format:
{
  "@timestamp": "2014-08-22T17:37:54.201855",
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
        val (name, host) = pt.key.name.split(":::") match {
          case Array(n, h) => (n, Some(h))
          case _ => (pt.key.name, None)
        }
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
  import java.util.Date
  import java.text.SimpleDateFormat

  /**
   * Emits one JSON document per mirror URL.
   * Once grouped by `elasticGroup`, this process emits one document per URL
   * with all the key/value pairs that were seen for that mirror in the group.
   */
  def elasticUngroup[A](flaskName: String): Process1[ESGroup[A], Json] =
    await1[ESGroup[A]].flatMap { g =>
      emitAll(g.toSeq.map { case (name, m) =>
        ("host" := name.getOrElse(flaskName)) ->:
        ("_timestamp" := new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ").format(new Date)) ->:
        m.toList.foldLeft(jEmptyObject) {
          case (o, (path, dp)) =>
            o deepmerge path.foldRight((dp.asJson -| "value").get)((a, b) =>
              (a := b) ->: jEmptyObject)
        }
      })
    }.repeat

  import Events._
  import scala.concurrent.duration._
  import dispatch._, Defaults._
  import scalaz.\/
  import concurrent.{Future,ExecutionContext}

  implicit def fromScalaFuture[A](a: Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatch(t.get)) }}

  /**
   * Publishes to an ElasticSearch URL at `esURL`.
   */
  def publish(M: Monitoring, esURL: String, flaskName: String)(
    implicit log: String => Unit): Task[Unit] =
      (Monitoring.subscribe(M)(x => !x.name.startsWith("now/") && !x.name.startsWith("sliding/")) |>
        elasticGroup |> elasticUngroup(flaskName)).evalMap { json =>
          val req = url(esURL).setContentType("application/json", "UTF-8") << json.nospaces
          fromScalaFuture(Http(req OK as.String)).attempt.map(
            _.fold(e => log("error in:\n" + json.nospaces), _ => ()))
        }.run
}

