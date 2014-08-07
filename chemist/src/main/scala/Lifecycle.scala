package oncue.svc.funnel.chemist

import java.net.URL
import scalaz.{\/,-\/,\/-}
import scalaz.concurrent.Task
import scalaz.stream.{Process,Sink}
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.AmazonSQS
import oncue.svc.funnel.aws.{SQS,SNS}

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

  def stream(queueName: String)(sqs: AmazonSQS, shards: Shards): Sink[Task, Action] = {
    def go(m: Message): Sink[Task, Action] =
      parseMessage(m).map(eventToAction) match {
        case -\/(fail) => Process.halt
        case \/-(win)  => runAction(win)(shards)
      }

    for {
      a <- SQS.subscribe(queueName)(sqs)
      b <- Process.emitSeq(a)
      c <- go(b)  //Process.emitSeq(List(1,2,3))  //Process.eval(myFunc(a.map(_.getMessageId)))
      _ <- SQS.deleteMessages(queueName, a)(sqs)
    } yield c
  }

  // import collection.JavaConversions._

  def distributeWorkToShards(work: Set[URL])(shards: Shards) = {
    println("::::::::::::::::::::::::::: DISTRIBUTING")
    // val flasks = shards.keySet.toSet

    // Stream.continually(instances).flatten.zip(
    //   Stream.continually(work).flatten).take(
    //     work.size.max(instances.size)).toList
  }



}