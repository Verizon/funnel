package funneltest

import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}

abstract trait Target {
  def port: Int
  import scala.concurrent.duration.DurationInt
  import funnel._

  val W = 30.seconds
  val M = Monitoring.instance
  val I = new Instruments(W, M)
  Clocks.instrument(I)
  JVM.instrument(I)
}

class SpecMultiJvmTarget1 extends FlatSpec with Target with Matchers {
  import funnel.http._
  val port = 2001
  MonitoringServer.start(M, port)
  Thread.sleep(75000)
}

class SpecMultiJvmTarget2 extends FlatSpec with Target with Matchers {
  import funnel.http._
  val port = 2002
  MonitoringServer.start(M, port)
  Thread.sleep(75000)
}

class SpecMultiJvmTarget3 extends FlatSpec with Target with Matchers {
  import funnel.http._
  val port = 2003
  MonitoringServer.start(M, port)
  Thread.sleep(75000)
}

class SpecMultiJvmTarget4 extends FlatSpec with Target with Matchers {
  import funnel.http._
  val port = 2004
  MonitoringServer.start(M, port)
  Thread.sleep(75000)
}


class SpecMultiJvmFlask1 extends FlatSpec with Matchers {
  import funnel._
  import funnel.http._
  import funnel.flask._
  import java.io.File
  import scalaz.concurrent.{Task,Strategy}
  import knobs.{ ClassPathResource, Config, Required }
  import scala.concurrent.duration.DurationInt

  val config: Task[Config] = knobs.loadImmutable(List(Required(ClassPathResource("oncue/flask.cfg"))))

  val options = funnel.Options(Some("flask1"), Some("cluster1"), None, None, 6775, telemetryPort = 7391)

  val I = new funnel.Instruments(1.minute)
  val app = new funnel.flask.Flask(options, I)
  MonitoringServer.start(Monitoring.default, 5775)
  app.run(Array())
  Thread.sleep(70000)
}

class SpecMultiJvmChemist extends FlatSpec with Matchers {
  import scalaz.concurrent.{Actor,Strategy}
  import funnel.chemist._
  import java.net.URI
  import PlatformEvent._
  import scalaz.stream.{Process, async}
  import dispatch._

  val signal = async.signalOf(true)
  val repo = new StatefulRepository
  val lifecycleActor: Actor[PlatformEvent] = Actor[PlatformEvent](a => repo.platformHandler(a).run)(Strategy.Executor(Chemist.serverPool))
  repo.lifecycle()
  val http = Http()
  (repo.repoCommands to Process.constant(Sharding.handleRepoCommand(repo, EvenSharding, new HttpFlask(http, repo, signal)) _)).run.runAsync(_ => ())

  Thread.sleep(6000)

  lifecycleActor ! NewTarget(Target("test", new URI("http://localhost:2001/stream/previous"), false))
  lifecycleActor ! NewTarget(Target("test", new URI("http://localhost:2002/stream/previous"), false))
  lifecycleActor ! NewTarget(Target("test", new URI("http://localhost:2003/stream/previous"), false))
  lifecycleActor ! NewTarget(Target("test", new URI("http://localhost:2004/stream/previous"), false))
  lifecycleActor ! NewFlask(Flask(FlaskID("flask1"), Location.localhost, Location.telemetryLocalhost))

  Thread.sleep(100000)
}
