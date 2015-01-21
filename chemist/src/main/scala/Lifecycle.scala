package oncue.svc.laboratory

import scalaz.{\/,-\/,\/-}
import scalaz.syntax.traverse._
import scalaz.concurrent.Task
import scalaz.stream.{Process,Sink}
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.autoscaling.AmazonAutoScaling

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

  def stream(queueName: String, resources: Seq[String])(r: Repository, sqs: AmazonSQS, asg: AmazonAutoScaling, ec2: AmazonEC2): Process[Task, Throwable \/ Action] = {
    for {
      a <- SQS.subscribe(queueName)(sqs)(Chemist.defaultPool, Chemist.schedulingPool)
      _ <- Process.eval(Task(log.debug(s"stream, number messages recieved: ${a.length}")))

      b <- Process.emitAll(a)
      _ <- Process.eval(Task(log.debug(s"stream, raw message recieved: $b")))

      c <- Process.eval(parseMessage(b).traverseU(interpreter(_, resources)(r, asg, ec2)))
      _ <- Process.eval(Task(log.debug(s"stream, computed action: $c")))

      _ <- SQS.deleteMessages(queueName, a)(sqs)
    } yield c
  }

  //////////////////////////// I/O Actions ////////////////////////////

  // im really not sure what to say about this monster...
  // sorry reader.
  def interpreter(e: AutoScalingEvent, resources: Seq[String])(r: Repository, asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Action] = {
    log.debug(s"interpreting event: $e")

    // terrible side-effect but we want to track the events
    // that chemist actually sees
    r.addEvent(e).run // YIKES!

    def isFlask: Task[Boolean] =
      ASG.lookupByName(e.asgName)(asg).flatMap { a =>
        log.debug(s"Found ASG from the EC2 lookup: $a")

        a.getTags.asScala.find(t =>
          t.getKey.trim == "type" &&
          t.getValue.trim.startsWith("flask")
        ).fold(Task.now(false))(_ => Task.now(true))
      }

    e match {
      case AutoScalingEvent(_,Launch,_,_,_,_,_,_,_,_,_,_,id) =>
        isFlask.flatMap(bool =>
          if(bool){
            // if its a flask, update our internal list of avalible shards
            log.debug(s"Adding capactiy $id")
            r.increaseCapacity(id).map(_ => NoOp)
          } else {
            // in any other case, its something new to monitor
            for {
              i <- Deployed.lookupOne(id)(ec2)
              _  = log.debug(s"Found instance metadata from remote: $i")

              _ <- r.addInstance(i)
              _  = log.debug(s"Adding service instance '$id' to known entries")

              t  = Sharding.Target.fromInstance(resources)(i)
              m <- Sharding.locateAndAssignDistribution(t, r)
              _  = log.debug("Computed and set a new distribution for the addition of host...")
            } yield Redistributed(m)
          }
        )

      case AutoScalingEvent(_,Terminate,_,_,_,_,_,_,_,_,_,_,id) => {
        r.isFlask(id).flatMap(bool =>
          if(bool){
            log.info(s"Terminating and rebalancing the flask cluster. Downing $id")
            (for {
              t <- r.assignedTargets(id)
              _  = log.debug(s"sink, targets= $t")
              _ <- r.decreaseCapacity(id)
              m <- Sharding.locateAndAssignDistribution(t,r)
            } yield Redistributed(m)) or Task.now(NoOp)
          } else {
            log.info(s"Terminating the monitoring of a non-flask service instance with id = $id")
            Task.now(NoOp)
          }
        )
      }

      case _ => Task.now(NoOp)
    }
  }

  // not sure if this is really needed but
  // looks like i can use this in a more generic way
  // to send previously unknown targets to flasks
  def sink: Sink[Task,Action] =
    Process.emit {
      case Redistributed(seq) =>
        Sharding.distribute(seq).map(_ => ())
      case _ => Task.now( () )
    }

  def event(e: AutoScalingEvent, resources: Seq[String])(r: Repository, asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Unit] = {
    interpreter(e, resources)(r, asg, ec2).map {
      case Redistributed(seq) =>
        Sharding.distribute(seq).map(_ => ())
      case _ =>
        Task.now( () )
    }
  }

  def run(queueName: String, resources: Seq[String], s: Sink[Task, Action])(r: Repository, sqs: AmazonSQS, asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Unit] = {
    stream(queueName, resources)(r,sqs,asg,ec2).flatMap {
      case -\/(fail) => Process.halt
      case \/-(win)  => s
    }.run
  }
}



