package oncue.svc.funnel.chemist

import scalaz.{\/,-\/,\/-}
import scalaz.syntax.traverse._
import scalaz.concurrent.Task
import scalaz.stream.{Process,Sink}
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import oncue.svc.funnel.aws.{SQS,SNS,ASG}

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
  import scala.collection.JavaConverters._

  private implicit val log = Logger[Lifecycle.type]

  case class MessageParseException(override val getMessage: String) extends RuntimeException
  case object LowPriorityScalingException extends RuntimeException {
    override val getMessage: String = "The specified scaling event does not affect flask capactiy"
  }

  def parseMessage(msg: Message): Throwable \/ AutoScalingEvent =
    Parse.decodeEither[AutoScalingEvent](msg.getBody).leftMap(MessageParseException(_))

  def stream(queueName: String)(r: Repository, sqs: AmazonSQS, asg: AmazonAutoScaling): Process[Task, Throwable \/ Action] = {
    for {
      a <- SQS.subscribe(queueName)(sqs)
      _ <- Process.eval(Task(log.debug(s">> a = $a")))

      b <- Process.emitAll(a)
      _ <- Process.eval(Task(log.debug(s">> b = $b")))

      c <- Process.eval(parseMessage(b).traverseU(interpreter(_)(r, asg)))
      _ <- Process.eval(Task(log.debug(s">> c = $c")))

      _ <- SQS.deleteMessages(queueName, a)(sqs)
    } yield c
  }

  //////////////////////////// I/O Actions ////////////////////////////

  // im really not sure what to say about this monster...
  // sorry reader.
  def interpreter(e: AutoScalingEvent)(r: Repository, asg: AmazonAutoScaling): Task[Action] = {
    log.debug(s"event: $e")

    def isFlask: Task[Boolean] =
      ASG.lookupByName(e.asgName)(asg).flatMap { a =>
        log.debug(s"Found ASG from the EC2 lookup: $a")

        a.getTags.asScala.map(t => t.getKey -> t.getValue).toMap.find { case (k,v) =>
          k.trim == "type" && v.startsWith("flask")
        }.fold(Task.now(false))(_ => Task.now(true))
      }

    e match {
      case AutoScalingEvent(_,Launch,_,_,_,_,_,_,_,_,_,_,id) =>
        isFlask.flatMap(bool =>
          if(bool){
            log.debug(s"Adding capactiy $id")
            r.increaseCapacity(id).map(_ => NoOp)
          } else
            Task.now(NoOp) // do something more meaningful here to start monitoring remotes
        )

      case AutoScalingEvent(_,Terminate,_,_,_,_,_,_,_,_,_,_,id) => {
        r.isFlask(id).flatMap(bool =>
          if(bool){
            log.info(s"Terminating and rebalancing the flask cluster. Downing $id")
            for {
              t <- r.assignedTargets(id)
              _  = log.debug(s"sink, targets= $t")
              _ <- r.decreaseCapacity(id)
              m <- Sharding.locateAndAssignDistribution(t,r)
            } yield Redistributed(m)
          } else {
            log.info(s"Terminating the monitoring of a non-flask service instance with id = $id")
            Task.now(NoOp)
          }
        )
      }

      case _ => Task.now(NoOp)
    }
  }

  // not sure if this is really needed
  def sink: Sink[Task,Action] =
    Process.emit {
      case Redistributed(seq) => Sharding.distribute(seq).map(_ => ())
      case _ => Task.now( () )
    }

  def run(queueName: String, s: Sink[Task, Action])(r: Repository, sqs: AmazonSQS, asg: AmazonAutoScaling): Task[Unit] = {
    stream(queueName)(r,sqs,asg).flatMap {
      case -\/(fail) => Process.halt
      case \/-(win)  => s
    }.run
  }
}



