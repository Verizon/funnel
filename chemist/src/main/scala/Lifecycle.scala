package oncue.svc.funnel.chemist

import scalaz.{\/,-\/,\/-}
import scalaz.syntax.traverse._
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

  private implicit val log = Logger[Lifecycle.type]

  case class MessageParseException(override val getMessage: String) extends RuntimeException

  def parseMessage(msg: Message): Throwable \/ AutoScalingEvent =
    Parse.decodeEither[AutoScalingEvent](msg.getBody).leftMap(MessageParseException(_))

  def eventToAction(event: AutoScalingEvent): Task[Action] = event.kind match {
    case Launch                       => Task.now(AddCapacity(event.instanceId))
    case Terminate                    => Task.now(Redistribute(event.instanceId))
    case LaunchError | TerminateError => Task.now(NoOp)
    case TestNotification | Unknown   => Task.now(NoOp)
  }

  def stream(queueName: String)(sqs: AmazonSQS): Process[Task, Throwable \/ Action] = {
    for {
      a <- SQS.subscribe(queueName)(sqs)
      b <- Process.emitAll(a)
      c <- Process.eval(parseMessage(b).traverseU(eventToAction))
      _ <- Process.eval(Task(println("=========================================== sdfsdfsdfs")))
      _ <- SQS.deleteMessages(queueName, a)(sqs)
    } yield c
  }

  //////////////////////////// I/O Actions ////////////////////////////

  // kinda horrible, but works for now.
  // seems like there should be a more pure solution to this
  // that results in a less-janky coupling
  def transform(a: Action, r: Repository): Task[Action] =
    a match {
      case AddCapacity(id) => {
        log.debug(s"Adding capactiy $id")
        r.increaseCapacity(id).map(_ => NoOp)
      }
      case Redistribute(id) =>
        for {
          t <- r.assignedTargets(id)
          _  = log.debug(s"sink, targets= $t")
          _ <- r.decreaseCapacity(id)
          m <- Sharding.locateAndAssignDistribution(t,r)
        } yield Redistributed(m)

      case a => Task.now(NoOp)
    }

  def sink(r: Repository): Sink[Task,Action] =
    Process.emit {
      case Redistributed(seq) =>
        Sharding.distribute(seq).map(_ => ())

      case _ => Task.now( () )
    }

  def run(queueName: String, sqs: AmazonSQS, sink: Sink[Task, Action]): Sink[Task, Action] = {
    stream(queueName)(sqs).flatMap {
      case -\/(fail) => Process.halt
      case \/-(win)  => sink
    }
  }
}



