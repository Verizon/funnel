package intelmedia.ws.funnel
package elastic

import scalaz.stream._

/* Elastic Event format:
{
  "@timestamp": "2014-08-20T21:50:51.292Z",
  "cluster": "improd-instanceeval-2-0-269-JJcxfuI",
  "host": "foo1.com",
  "http": {
    "get": {
      "foo": {
        "last": 123,
        "mean": 122,
        "standard_deviation": 1.2222
      },
      "bar": {
        "last": 456,
        "mean": 678,
        "standard_deviation": 1.2222
      }
    }
  }
}
*/

object Elastic {
  type Name = String
  type Path = List[String]
  type ESGroup[A] = Map[Option[Name], Map[Path, Datapoint[A]]]

  import Process._
  import scalaz.concurrent.Task
  import scalaz.Tree

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

  def elasticUngroup[A](flaskName: String): Process1[ESGroup[A], Json] =
    await1[ESGroup[A]].flatMap { g =>
      emitAll(g.toSeq.map { case (name, m) =>
        ("host" := name.getOrElse(flaskName)) ->:
        m.toList.foldLeft(jEmptyObject) {
          case (o, (path, dp)) =>
            o deepmerge path.foldRight((dp.asJson -| "value").get)((a, b) => (a := b) ->: jEmptyObject)
        }
      })
    }.repeat

  import Events._
  import scala.concurrent.duration._

  def publish(M: Monitoring, esURL: String,
    retries: Event = Events.every(1 minutes))(
    implicit log: String => Unit): Task[Unit] =
      (Monitoring.subscribe(M)(_ => true) |> elasticGroup).evalMap(m => Task(println(m))).run

}
