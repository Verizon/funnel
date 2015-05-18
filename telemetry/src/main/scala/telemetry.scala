package funnel
package telemetry

import scodec._
import scodec.bits._
import zeromq._
import sockets._
import scalaz.stream._
import scalaz.stream.async.mutable.{Signal,Queue}
import scalaz.concurrent.{Actor,Task}
import scalaz.{-\/,\/,\/-}
import java.net.URI
import journal.Logger

object Telemetry extends TelemetryCodecs {
  private lazy val log = Logger[Telemetry]

  implicit val transportedTelemetry = Transportable[Telemetry] { t =>
    t match {
      case e @ Error(_) =>
        val t = Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("error")), errorCodec.encodeValid(e).toByteArray)
        t
      case NewKey(key) =>
        val bytes = keyEncode.encodeValid(key).toByteArray
        val t = Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("key")), bytes)
        t
      case Monitored(i) =>
        log.info("MONITOR")
        Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("monitor")), i.toString.getBytes())
      case Unmonitored(i) =>
        log.info("UNMONITOR")
        Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("unmonitor")), i.toString.getBytes())
    }
  }

  def telemetryPublishEndpoint(uri: URI): Endpoint = Endpoint.unsafeApply(publish &&& bind, uri)

  def telemetrySubscribeEndpoint(uri: URI): Endpoint = Endpoint.unsafeApply(subscribe &&& (connect ~ topics.all), uri)

  def telemetryPublishSocket(uri: URI, signal: Signal[Boolean], telemetry: Process[Task,Telemetry]): Task[Unit] = {
    val e = telemetryPublishEndpoint(uri)
    Ø.link(e)(signal) { socket =>
      (Process.emit(Error(Names("hello", "there", new URI("http://localhost")))) ++ telemetry) through Ø.write(socket)
    }.run
  }



  def telemetrySubscribeSocket(uri: URI, signal: Signal[Boolean],
                               keys: Actor[(URI, Set[Key[Any]])],
                               errors: Actor[Error],
                               lifecycle: Actor[URI \/ URI]
                               ): Task[Unit] = {
    val endpoint = telemetrySubscribeEndpoint(uri)
    Ø.link(endpoint)(signal) { socket =>
      log.info(s"connected to telemetry socket on $uri")
      (Ø.receive(socket) to fromTransported(uri, keys, errors, lifecycle))
    }.run
  }

  def fromTransported(id: URI, keys: Actor[(URI, Set[Key[Any]])], errors: Actor[Error], lifecycleSink: Actor[URI \/ URI]): Sink[Task, Transported] = {
    val currentKeys = collection.mutable.Set.empty[Key[Any]]

    Process.constant { x =>
      log.error("FROMTRANSPORTED: " + x)
      x match {
        case Transported(_, Versions.v1, _, Some(Topic("error")), bytes) =>
          Task.delay(errors ! errorCodec.decodeValidValue(BitVector(bytes)))
        case Transported(_, Versions.v1, _, Some(Topic("key")), bytes) =>
          Task(currentKeys += keyDecode.decodeValidValue(BitVector(bytes))).flatMap { k =>
            Task.delay(keys ! id -> k.toSet)
          }
        case Transported(_, Versions.v1, _, Some(Topic("monitor")), bytes) => Task.delay(lifecycleSink ! \/.right(uriCodec.decodeValidValue(BitVector(bytes))))
        case Transported(_, Versions.v1, _, Some(Topic("unmonitor")), bytes) => Task.delay(lifecycleSink ! \/.left(uriCodec.decodeValidValue(BitVector(bytes))))
        case x => log.error("unexpected message from telemetry: " + x)
          Task.now(())
      }
    }
  }

  val keyChanges: Process1[Set[Key[Any]], NewKey] = {
    import Process._
    def go(old: Option[Set[Key[Any]]]): Process1[Set[Key[Any]], NewKey] = receive1 { current =>
      val toEmit = old match {
        case None => current
        case Some(oldKeys) => (current -- oldKeys)
      }
      if(toEmit.isEmpty) {
        go(Some(current))
      } else {
        emitAll(toEmit.toSeq.map(NewKey.apply)) ++ go(Some(current))
      }
    }
    go(None)
  }
}


trait TelemetryCodecs extends KeyCodecs {

  implicit val uriCodec: Codec[URI] = utf8.xmap[URI](new URI(_), _.toString)
  implicit lazy val errorCodec = Codec.derive[Names].xmap[Error](Error(_), _.names)
}
