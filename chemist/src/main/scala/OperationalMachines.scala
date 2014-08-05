package intelmedia.ws.funnel
package chemist

import scalaz.concurrent.Task
import scalaz.stream.Process
import concurrent.duration._
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient

object OperationalMachines {
  def list(asg: AmazonAutoScalingClient): Task[List[Any]] = Task.now(Nil)

  def periodic(delay: Duration)(asg: AmazonAutoScalingClient) =
    Process.awakeEvery(delay).evalMap(_ => list(asg))

}
