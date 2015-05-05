package funnel
package messages

import scodec._
import scodec.bits._
import zeromq._
import sockets._
import scalaz.stream._
import scalaz.stream.async.mutable.{Signal,Queue}
import scalaz.stream.async.{signalOf,unboundedQueue}
import scalaz.concurrent.{Actor,Task}
import scalaz.{-\/,\/,\/-}
import java.net.URI

sealed trait Telemetry

final case class Error(names: Names) extends Telemetry {
  override def toString = s"${names.mine} gave up on ${names.kind} server ${names.theirs}"
}

final case class NewKey(key: Key[_]) extends Telemetry
final case class Monitored(i: InstanceID) extends Telemetry
final case class Unmonitored(i: InstanceID) extends Telemetry

object Telemetry extends TelemetryCodecs {

  implicit val transportedTelemetry = Transportable[Telemetry] { t =>
    t match {
      case e @ Error(_) => Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("error")), errorCodec.encodeValid(e).toByteArray)
      case NewKey(key) =>
        val bytes = keyEncode.encodeValid(key).toByteArray
        Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("key")), bytes)
      case Monitored(i) => Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("monitor")), i.getBytes())
      case Unmonitored(i) => Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("unmonitor")), i.getBytes())
    }
  }

  def telemetryPublishEndpoint(uri: URI): Endpoint = Endpoint.unsafeApply(publish &&& bind, uri)

  def telemetrySubscribeEndpoint(uri: URI): Endpoint = Endpoint.unsafeApply(subscribe &&& (connect ~ topics.all), uri)

  def telemetryPublishSocket(uri: URI, signal: Signal[Boolean], telemetry: Process[Task,Telemetry]): Task[Unit] = {
    val e = telemetryPublishEndpoint(uri)
    Ø.link(e)(signal) { socket =>
      telemetry  through Ø.write(socket)
    }.run
  }



  def telemetrySubscribeSocket(uri: URI, signal: Signal[Boolean],
                               id: InstanceID,
                               keys: Actor[(InstanceID, Set[Key[Any]])],
                               errors: Actor[Error],
                               lifecycle: Actor[String \/ String]
                               ): Task[Unit] = {
    val endpoint = telemetrySubscribeEndpoint(uri)
    Ø.link(endpoint)(signal) { socket =>
      (Ø.receive(socket) to fromTransported(id, keys, errors, lifecycle))
    }.run
  }

  def fromTransported(id: InstanceID, keys: Actor[(InstanceID, Set[Key[Any]])], errors: Actor[Error], lifecycleSink: Actor[String \/ String]): Sink[Task, Transported] = {
    val currentKeys = collection.mutable.Set.empty[Key[Any]]

    Process.constant { x =>
      x match {
        case Transported(_, Versions.v1, _, Some(Topic("error")), bytes) =>
          Task.delay(errors ! errorCodec.decodeValidValue(BitVector(bytes)))
        case Transported(_, Versions.v1, _, Some(Topic("key")), bytes) =>
          Task(currentKeys += keyDecode.decodeValidValue(BitVector(bytes))).flatMap { k =>
            Task.delay(keys ! id -> k.toSet)
          }
        case Transported(_, Versions.v1, _, Some(Topic("monitor")), bytes) => Task.delay(lifecycleSink ! \/.right(new String(bytes)))
        case Transported(_, Versions.v1, _, Some(Topic("unmonitor")), bytes) => Task.delay(lifecycleSink ! \/.left(new String(bytes)))
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
  implicit lazy val errorCodec = Codec.derive[Names].xmap[Error](Error(_), _.names)
}
