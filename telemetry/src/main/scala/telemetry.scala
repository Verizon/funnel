package funnel
package telemetry

import scodec._
import scodec.bits._
import zeromq._
import sockets._
import scalaz.stream._
import scalaz.stream.async.mutable.{Signal,Queue}
import scalaz.concurrent.{Actor,Task}
import scalaz.{-\/,\/,\/-, Either3}
import java.net.URI
import journal.Logger

object Telemetry extends TelemetryCodecs {
  private lazy val log = Logger[Telemetry]

  implicit val transportedTelemetry = Transportable[Telemetry] { t =>
    t match {
      case e @ Error(_) =>
        val t = Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("error")), errorCodec.encode(e).require.toByteArray)
        t
      case NewKey(key) =>
        val bytes = keyEncode.encode(key).require.toByteArray
        val t = Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("key")), bytes)
        t
      case Monitored(i) =>
       Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("monitor")), uriCodec.encode(i).require.toByteArray)

      case Unmonitored(i) =>
        Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("unmonitor")), uriCodec.encode(i).require.toByteArray)

      case Problem(i, msg) =>
        Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("exception")), uriCodec.encode(i).require.toByteArray)
    }
  }

  def telemetryPublishEndpoint(uri: URI): Endpoint = Endpoint.unsafeApply(publish &&& bind, uri)

  def telemetrySubscribeEndpoint(uri: URI): Endpoint = Endpoint.unsafeApply(subscribe &&& (connect ~ topics.all), uri)

  private def countSentMessages: Sink[Task, Telemetry] = sink.lift { _ =>
    import metrics.MessagesSent
    Task.delay(MessagesSent.increment)
  }

  def telemetryPublishSocket(uri: URI, signal: Signal[Boolean], telemetry: Process[Task,Telemetry]): Task[Unit] = {
    val e = telemetryPublishEndpoint(uri)
    Ø.link(e)(signal) { socket =>
      telemetry observe countSentMessages through Ø.write(socket)
    }.run
  }

  private def countReceivedMessages: Sink[Task, Transported] = sink.lift { _ =>
    import metrics.MessagesReceived
    Task.delay(MessagesReceived.increment)
  }

  def telemetrySubscribeSocket(uri: URI, signal: Signal[Boolean],
                               keys: Actor[(URI, Set[Key[Any]])],
                               errors: Actor[Error],
                               lifecycle: Actor[Either3[URI, URI, (URI,String)]]
                               ): Task[Unit] = {
    val endpoint = telemetrySubscribeEndpoint(uri)
    Ø.link(endpoint)(signal) { socket =>
      log.info(s"connected to telemetry socket on $uri")
      (Ø.receive(socket) observe countReceivedMessages to fromTransported(uri, keys, errors, lifecycle))
    }.run
  }

  def fromTransported(id: URI, keys: Actor[(URI, Set[Key[Any]])], errors: Actor[Error], lifecycleSink: Actor[Either3[URI, URI, (URI, String)]]): Sink[Task, Transported] = {
    val currentKeys = collection.mutable.Set.empty[Key[Any]]

    Process.constant { x =>
      val y = {
        x match {
          case Transported(_, Versions.v1, _, Some(Topic("error")), bytes) =>
          errorCodec.decode(BitVector(bytes)) match {
            case Attempt.Failure(err) => Task.delay(log.error(s"Error parsing error from telemetry $err"))
            case Attempt.Successful(DecodeResult(err, _)) => Task.delay(errors ! err)
          }
          case Transported(_, Versions.v1, _, Some(Topic("key")), bytes) =>
          keyDecode.decode(BitVector(bytes)) match {
            case Attempt.Failure(err) => Task.delay(log.error(s"Error parsing keys from telemetry $err"))
            case Attempt.Successful(DecodeResult(k,_)) =>
              currentKeys += k
              Task.delay(keys ! id -> currentKeys.toSet)
          }
          case Transported(_, Versions.v1, _, Some(Topic("monitor")), bytes) =>
            uriCodec.decode(BitVector(bytes)) match {
              case Attempt.Failure(err) => Task.delay(log.error(s"Error parsing monitor from telemetry $err"))
              case Attempt.Successful(DecodeResult(uri,_)) => Task.delay(lifecycleSink ! Either3.middle3(uri))
            }
          case Transported(_, Versions.v1, _, Some(Topic("unmonitor")), bytes) =>
            uriCodec.decode(BitVector(bytes)) match {
              case Attempt.Failure(err) => Task.delay(log.error(s"Error parsing unmonitor from telemetry $err"))
              case Attempt.Successful(DecodeResult(uri,_)) => Task.delay(lifecycleSink ! Either3.left3(uri))
            }

          case Transported(_, Versions.v1, None, Some(Topic("exception")), bytes) =>
            uriCodec.decode(BitVector(bytes)) map {
              case DecodeResult(uri,_) => uri -> ""
            } match {
              case Attempt.Failure(err) => Task.delay(log.error(s"Error parsing exception from telemetry $err"))
              case Attempt.Successful((uri, msg)) => Task.delay(lifecycleSink ! Either3.right3(uri -> msg))
            }

          case x => log.error("unexpected message from telemetry: " + x)
            Task.now(())
        }
      }
      y
    }
  }

  import annotation.tailrec

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
  implicit lazy val errorCodec = implicitly[Codec[Names]].xmap[Error](Error(_), _.names)
}
