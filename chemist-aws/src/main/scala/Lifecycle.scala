package funnel
package chemist
package aws

import scalaz.{\/,-\/,\/-}
import scalaz.syntax.traverse._
import scalaz.syntax.monad._
import scalaz.concurrent.{Actor, Task}
import scalaz.stream.{Process, Process0, Process1, Sink}
import scalaz.stream.async.mutable.Signal
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import funnel.aws._
import telemetry.Telemetry._
import java.net.URI

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
  import metrics._
  import PlatformEvent._

  private implicit val log = Logger[Lifecycle.type]
  private val noop = \/-(Seq(NoOp))

  /**
   * Attempt to parse incomming SQS messages into `AutoScalingEvent`. Yield a
   * `MessageParseException` in the event the message is not decipherable.
   */
  def parseMessage(msg: Message): Throwable \/ AutoScalingEvent =
    Parse.decodeEither[AutoScalingEvent](msg.getBody).leftMap(MessageParseException(_))

  /**
   * This function essentially converts the polling of an SQS queue into a scalaz-stream
   * process. Said process contains Strings being delivered on the SQS queue, which we
   * then attempt to parse into an `AutoScalingEvent`. Assuming the message parses correctly
   * we then pass the `AutoScalingEvent` to the `interpreter` function for further processing.
   * In the event the message does not parse to an `AutoScalingEvent`,
   */
  def stream(queueName: String, resources: Seq[String], signal: Signal[Boolean]
    )(sqs: AmazonSQS, asg: AmazonAutoScaling, ec2: AmazonEC2, dsc: Discovery
    ): Process[Task, Throwable \/ Seq[PlatformEvent]] = {
      // adding this function to ensure that parse errors do not get
      // lifted into errors that will later fail the stream, and that
      // any errors in the interpreter are properly handled.
      def go(m: Message): Task[Throwable \/ Seq[PlatformEvent]] =
        parseMessage(m).traverseU(interpreter(_, resources, signal)(asg, ec2, dsc)).handle {
          case MessageParseException(err) =>
            log.warn(s"Unexpected recoverable error when parsing lifecycle message: $err")
           noop

          case InstanceNotFoundException(id,kind) =>
            log.warn(s"Unexpected recoverable error locating $kind id '$id' specified on lifecycle message.")
            noop

          case _ =>
            log.warn(s"Failed to handle error state when recieving lifecycle event: ${m.getBody}")
            noop
        }

    for {
      a <- SQS.subscribe(queueName)(sqs)(Chemist.defaultPool, Chemist.schedulingPool)
      _ <- Process.eval(Task(log.debug(s"stream, number messages recieved: ${a.length}")))

      b <- Process.emitAll(a)
      _ <- Process.eval(Task(log.debug(s"stream, raw message recieved: $b")))

      c <- Process.eval(go(b))
      _ <- Process.eval(Task(log.debug(s"stream, computed action: $c")))

      _ <- SQS.deleteMessages(queueName, a)(sqs)
    } yield c
  }

  def lifecycleActor(repo: Repository): Actor[PlatformEvent] = Actor(a => repo.platformHandler(a).run)
  def errorActor(repo: Repository): Actor[Error] = Actor(e => repo.errorSink(e).run)
  def keysActor(repo: Repository): Actor[(URI, Set[Key[Any]])] = Actor{ case (fl, keys) => repo.keySink(fl, keys).run }


  def interpreter(e: AutoScalingEvent, resources: Seq[String], signal: Signal[Boolean]
    )(asg: AmazonAutoScaling, ec2: AmazonEC2, dsc: Discovery
    ): Task[Seq[PlatformEvent]] = {

    log.debug(s"interpreting event: $e")

    // MOAR side-effects!
    LifecycleEvents.increment

    def targetsFromId(id: TargetID): Task[Seq[NewTarget]] =
      for {
        i <- dsc.lookupTargets(id)
      _  = log.debug(s"Found instance metadata from remote: $i")
      } yield i.toSeq.map(NewTarget)

    def terminatedTargetsFromId(id: TargetID): Task[Seq[PlatformEvent]] =
      for {
        i <- dsc.lookupTargets(id)
      } yield i.toSeq.map(t => TerminatedTarget(t.uri))

    def isFlask: Task[Boolean] =
      ASG.lookupByName(e.asgName)(asg).flatMap { a =>
        log.debug(s"Found ASG from the EC2 lookup: $a")

        a.getTags.asScala.find(t =>
          t.getKey.trim == "type" &&
          t.getValue.trim.startsWith("flask")
        ).fold(Task.now(false))(_ => Task.now(true))
      }

    def newFlask(id: FlaskID): Task[Seq[PlatformEvent]] =
      for {
        flask <- dsc.lookupFlask(id)
      } yield Seq(NewFlask(flask))

    e match {
      case AutoScalingEvent(_,Launch,_,_,_,_,_,_,_,_,_,_,id) =>
        isFlask.ifM(newFlask(FlaskID(id)), targetsFromId(TargetID(id)))
      case AutoScalingEvent(_,Terminate,_,_,_,_,_,_,_,_,_,_,id) =>
        isFlask.ifM(Task.now(Seq(TerminatedFlask(FlaskID(id)))), terminatedTargetsFromId(TargetID(id)))

      case _ => Task.now(Seq(NoOp))
    }
  }

  def logErrors(x: Throwable \/ Seq[PlatformEvent]): Process[Task,PlatformEvent] = x match {
    case -\/(t) =>
      log.error(s"Problem encountered when trying to processes SQS lifecycle message: $t")
      t.printStackTrace
      Process.halt
    case \/-(a) => Process.emitAll(a)
  }

  def telemetrySink(r: Repository, signal: Signal[Boolean]): Sink[Task, PlatformEvent] = {
    val lifecycle = lifecycleActor(r)
    val errors = errorActor(r)
    val keys = keysActor(r)

    Process.constant {
      case NewFlask(f) => monitorTelemetry(f, keys, errors, lifecycle, signal)
      case _ => Task.now(())
    }
  }

  /**
   * The main method for the lifecycle process. Run the `stream` method and then handle
   * failures that might occour on the process. This function is primarily used in the
   * init method for chemist so that the SQS/SNS lifecycle is started from the edge of
   * the world.
   */
  def run(queueName: String, resources: Seq[String], signal: Signal[Boolean]
    )(repo: Repository, sqs: AmazonSQS, asg: AmazonAutoScaling, ec2: AmazonEC2, dsc: Discovery
  ): Task[Unit] = {
    val ourWorld = stream(queueName, resources, signal)(sqs,asg,ec2,dsc) flatMap logErrors observe telemetrySink(repo, signal) to Process.constant(repo.platformHandler _)
    ourWorld.run.onFinish(_ => signal.set(false))
  }
}
