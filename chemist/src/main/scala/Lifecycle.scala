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

  def stream(queueName: String)(sqs: AmazonSQS): Process[Task, Throwable \/ Action] = {
    for {
      a <- SQS.subscribe(queueName)(sqs)
      b <- Process.emitAll(a)
      c <- Process.emit(parseMessage(b).map(eventToAction))
      _ <- SQS.deleteMessages(queueName, a)(sqs)
    } yield c
  }

  def toSink(act: Action)(shards: Shards): Sink[Task, Action] =
    Process.emit {
      case AddCapacity(id)  => Task.delay { shards.putIfAbsent(id, Set.empty); () }
      case Redistribute(id) => Task.delay { reshard(id)(shards) }
      case NoOp             => Task.now( () )
    }

  def run(queueName: String, sqs: AmazonSQS, shards: Shards): Sink[Task, Action] = {
    stream(queueName)(sqs).flatMap {
      case -\/(fail) => Process.halt
      case \/-(win)  => toSink(win)(shards)
    }
  }

  private def reshard(id: InstanceID)(shards: Shards): Unit =
    for {
      urls <- Option(shards.get(id))
      _    <- Option(shards.remove(id))
    } yield distributeWorkToShards(urls)(shards)

  def distributeWorkToShards(work: Set[URL])(shards: Shards) = {
    println("::::::::::::::::::::::::::: DISTRIBUTING")
    // val flasks = shards.keySet.toSet

    // Stream.continually(instances).flatten.zip(
    //   Stream.continually(work).flatten).take(
    //     work.size.max(instances.size)).toList
  }



}