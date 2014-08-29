package oncue.svc.funnel.chemist

import scalaz.{\/,-\/,\/-}
import scalaz.concurrent.Task
import scalaz.stream.{Process,Sink}
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.AmazonSQS
import oncue.svc.funnel.aws.{SQS,SNS}

/**
 * The purpose of this object is to manage all the "lifecycle" events
 * associated with subordinate Flask instances. Whenever they start,
 * stop, etc the SQS event will come in and be presented on the stream.
 *
 * The design here is that incoming events are translated into a lifecycle
 * algebra which is then acted upon. This is essentially interpreter pattern.
 */
object Lifecycle {
  import JSON._
  import argonaut._, Argonaut._
  import journal.Logger

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

  //////////////////////////// I/O Actions ////////////////////////////

  def sink: Sink[Task,Action] =
    Process.emit {
      case _ => Task.now(())
    }

  def sink(r: Repository)(implicit log: Logger): Sink[Task,Action] =
    Process.emit {
      case AddCapacity(id) =>
        r.increaseCapacity(id).map(_ => ())

      case Redistribute(id) =>
        for {
          targets <- r.assignedTargets(id)
          _       <- r.decreaseCapacity(id)
          _       <- Sharding.distribute(targets)(r)
        } yield ()

      case NoOp =>
        Task.now( () )
    }

  def run(queueName: String, sqs: AmazonSQS, sink: Sink[Task, Action])(implicit log: Logger): Sink[Task, Action] = {
    stream(queueName)(sqs).flatMap {
      case -\/(fail) => Process.halt
      case \/-(win)  => sink
    }
  }
}



