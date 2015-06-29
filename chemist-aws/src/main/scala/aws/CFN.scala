package funnel
package aws

import scalaz.concurrent.Task
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest

object CFN {
  import scala.collection.JavaConverters._

  def getStackOutputs(name: String)(cfn: AmazonCloudFormation): Task[Map[String,String]] = {
    val req = (new DescribeStacksRequest).withStackName(name)
    for {
      a <- Task(cfn.describeStacks(req))(funnel.chemist.Chemist.defaultPool)
      b  = a.getStacks.asScala.flatMap(_.getOutputs.asScala)
    } yield b.map(x => x.getOutputKey -> x.getOutputValue).toMap
  }
}
