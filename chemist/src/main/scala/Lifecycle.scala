package oncue.svc.funnel.chemist

import java.net.URL
import scalaz.\/
import scalaz.concurrent.Task
import scalaz.stream.{Process,Sink}
import com.amazonaws.services.sqs.model.Message

object Lifecycle {
  import Decoder._
  import argonaut._, Argonaut._

  case class MessageParseException(override val getMessage: String) extends RuntimeException

  def parseMessage(msg: Message): Throwable \/ AutoScalingEvent =
    Parse.decodeEither[AutoScalingEvent](msg.getBody).leftMap(MessageParseException(_))

  def eventToAction(event: AutoScalingEvent): Action = event.kind match {
    case Launch                       => AddCapacity(event.instanceId)
    case Terminate                    => Redistribute(event.instanceId)
    case LaunchError | TerminateError => NoOp
    case TestNotification | Unknown   => NoOp
  }

  def processor: Message => Action =
    m => parseMessage(m).map(eventToAction
          ).fold(err => NoOp, a => runAction(a))

  def runAction(act: Action)(shards: Shards): Sink[Task, Action] =
    Process.emit {
      case AddCapacity(id)  => Task.delay { shards.putIfAbsent(id, Set.empty); () }
      case Redistribute(id) => Task.delay { reshard(id)(shards) }
      case NoOp             => Task.now( () )
    }

  private def reshard(id: InstanceID)(shards: Shards): Unit =
    for {
      urls <- Option(shards.get(id))
      _    <- Option(shards.remove(id))
    } yield distributeWorkToShards(urls)(shards)

  import collection.JavaConversions._

  def distributeWorkToShards(work: Set[URL])(shards: Shards) = {
    ()
    // val flasks = shards.keySet.toSet

    // Stream.continually(instances).flatten.zip(
    //   Stream.continually(work).flatten).take(
    //     work.size.max(instances.size)).toList
  }



}