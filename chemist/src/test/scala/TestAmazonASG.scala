package funnel
package chemist

import com.amazonaws.services.autoscaling.{AmazonAutoScaling,AmazonAutoScalingClient}
import com.amazonaws.services.autoscaling.model.{
  DescribeAutoScalingGroupsRequest,
  DescribeAutoScalingGroupsResult,
  AutoScalingGroup,
  TagDescription,
  Instance => ASGInstance}
import collection.JavaConverters._

// I really hate this shit, but its nessicary to run a fat integration test.
object TestAmazonASG {
  private def tags(t: (String,String)*): java.util.List[TagDescription] =
    t.toSeq.map { case (k,v) =>
      new TagDescription().withKey(k).withValue(v)
    }.asJava

  private def randomasg: AutoScalingGroup = {
    val uuid = java.util.UUID.randomUUID
    new AutoScalingGroup()
      .withAutoScalingGroupName(s"name-$uuid")
      .withAvailabilityZones(Seq("us-east-1a", "us-east-1b", "us-east-1c").asJava)
      .withCreatedTime(new java.util.Date)
      .withDesiredCapacity(1)
      .withMaxSize(1)
      .withMinSize(1)
      .withInstances()
      .withLaunchConfigurationName(s"lc-$uuid")
      .withTags(tags("type" -> "flask"))
  }

  def multiple: TestAmazonASG =
    new TestAmazonASG {
      def describeAutoScalingGroups(x$1: DescribeAutoScalingGroupsRequest): DescribeAutoScalingGroupsResult =
        (new DescribeAutoScalingGroupsResult)
          .withAutoScalingGroups(Seq(randomasg, randomasg).asJava)
    }

  def single(f: String => String): TestAmazonASG =
    new TestAmazonASG {
      def describeAutoScalingGroups(x$1: DescribeAutoScalingGroupsRequest): DescribeAutoScalingGroupsResult = {
        val a = randomasg
        val n = a.getAutoScalingGroupName
        (new DescribeAutoScalingGroupsResult)
          .withAutoScalingGroups(a.withAutoScalingGroupName(f(n)))
      }
    }

  def single: TestAmazonASG = single(identity)

  // def failure:
}

trait TestAmazonASG extends AmazonAutoScaling {

  // def describeAutoScalingGroups(x$1: DescribeAutoScalingGroupsRequest): com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult =
  def attachInstances(x$1: com.amazonaws.services.autoscaling.model.AttachInstancesRequest): Unit = ???
  def createAutoScalingGroup(x$1: com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest): Unit = ???
  def createLaunchConfiguration(x$1: com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest): Unit = ???
  def createOrUpdateTags(x$1: com.amazonaws.services.autoscaling.model.CreateOrUpdateTagsRequest): Unit = ???
  def deleteAutoScalingGroup(x$1: com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest): Unit = ???
  def deleteLaunchConfiguration(x$1: com.amazonaws.services.autoscaling.model.DeleteLaunchConfigurationRequest): Unit = ???
  def deleteNotificationConfiguration(x$1: com.amazonaws.services.autoscaling.model.DeleteNotificationConfigurationRequest): Unit = ???
  def deletePolicy(x$1: com.amazonaws.services.autoscaling.model.DeletePolicyRequest): Unit = ???
  def deleteScheduledAction(x$1: com.amazonaws.services.autoscaling.model.DeleteScheduledActionRequest): Unit = ???
  def deleteTags(x$1: com.amazonaws.services.autoscaling.model.DeleteTagsRequest): Unit = ???
  def describeAccountLimits(): com.amazonaws.services.autoscaling.model.DescribeAccountLimitsResult = ???
  def describeAccountLimits(x$1: com.amazonaws.services.autoscaling.model.DescribeAccountLimitsRequest): com.amazonaws.services.autoscaling.model.DescribeAccountLimitsResult = ???
  def describeAdjustmentTypes(): com.amazonaws.services.autoscaling.model.DescribeAdjustmentTypesResult = ???
  def describeAdjustmentTypes(x$1: com.amazonaws.services.autoscaling.model.DescribeAdjustmentTypesRequest): com.amazonaws.services.autoscaling.model.DescribeAdjustmentTypesResult = ???
  def describeAutoScalingGroups(): com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult = ???
  def describeAutoScalingInstances(): com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesResult = ???
  def describeAutoScalingInstances(x$1: com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesRequest): com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesResult = ???
  def describeAutoScalingNotificationTypes(): com.amazonaws.services.autoscaling.model.DescribeAutoScalingNotificationTypesResult = ???
  def describeAutoScalingNotificationTypes(x$1: com.amazonaws.services.autoscaling.model.DescribeAutoScalingNotificationTypesRequest): com.amazonaws.services.autoscaling.model.DescribeAutoScalingNotificationTypesResult = ???
  def describeLaunchConfigurations(): com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult = ???
  def describeLaunchConfigurations(x$1: com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest): com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult = ???
  def describeMetricCollectionTypes(): com.amazonaws.services.autoscaling.model.DescribeMetricCollectionTypesResult = ???
  def describeMetricCollectionTypes(x$1: com.amazonaws.services.autoscaling.model.DescribeMetricCollectionTypesRequest): com.amazonaws.services.autoscaling.model.DescribeMetricCollectionTypesResult = ???
  def describeNotificationConfigurations(): com.amazonaws.services.autoscaling.model.DescribeNotificationConfigurationsResult = ???
  def describeNotificationConfigurations(x$1: com.amazonaws.services.autoscaling.model.DescribeNotificationConfigurationsRequest): com.amazonaws.services.autoscaling.model.DescribeNotificationConfigurationsResult = ???
  def describePolicies(): com.amazonaws.services.autoscaling.model.DescribePoliciesResult = ???
  def describePolicies(x$1: com.amazonaws.services.autoscaling.model.DescribePoliciesRequest): com.amazonaws.services.autoscaling.model.DescribePoliciesResult = ???
  def describeScalingActivities(): com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesResult = ???
  def describeScalingActivities(x$1: com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesRequest): com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesResult = ???
  def describeScalingProcessTypes(): com.amazonaws.services.autoscaling.model.DescribeScalingProcessTypesResult = ???
  def describeScalingProcessTypes(x$1: com.amazonaws.services.autoscaling.model.DescribeScalingProcessTypesRequest): com.amazonaws.services.autoscaling.model.DescribeScalingProcessTypesResult = ???
  def describeScheduledActions(): com.amazonaws.services.autoscaling.model.DescribeScheduledActionsResult = ???
  def describeScheduledActions(x$1: com.amazonaws.services.autoscaling.model.DescribeScheduledActionsRequest): com.amazonaws.services.autoscaling.model.DescribeScheduledActionsResult = ???
  def describeTags(): com.amazonaws.services.autoscaling.model.DescribeTagsResult = ???
  def describeTags(x$1: com.amazonaws.services.autoscaling.model.DescribeTagsRequest): com.amazonaws.services.autoscaling.model.DescribeTagsResult = ???
  def describeTerminationPolicyTypes(): com.amazonaws.services.autoscaling.model.DescribeTerminationPolicyTypesResult = ???
  def describeTerminationPolicyTypes(x$1: com.amazonaws.services.autoscaling.model.DescribeTerminationPolicyTypesRequest): com.amazonaws.services.autoscaling.model.DescribeTerminationPolicyTypesResult = ???
  def disableMetricsCollection(x$1: com.amazonaws.services.autoscaling.model.DisableMetricsCollectionRequest): Unit = ???
  def enableMetricsCollection(x$1: com.amazonaws.services.autoscaling.model.EnableMetricsCollectionRequest): Unit = ???
  def executePolicy(x$1: com.amazonaws.services.autoscaling.model.ExecutePolicyRequest): Unit = ???
  def getCachedResponseMetadata(x$1: com.amazonaws.AmazonWebServiceRequest): com.amazonaws.ResponseMetadata = ???
  def putNotificationConfiguration(x$1: com.amazonaws.services.autoscaling.model.PutNotificationConfigurationRequest): Unit = ???
  def putScalingPolicy(x$1: com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest): com.amazonaws.services.autoscaling.model.PutScalingPolicyResult = ???
  def putScheduledUpdateGroupAction(x$1: com.amazonaws.services.autoscaling.model.PutScheduledUpdateGroupActionRequest): Unit = ???
  def resumeProcesses(x$1: com.amazonaws.services.autoscaling.model.ResumeProcessesRequest): Unit = ???
  def setDesiredCapacity(x$1: com.amazonaws.services.autoscaling.model.SetDesiredCapacityRequest): Unit = ???
  def setEndpoint(x$1: String): Unit = ???
  def setInstanceHealth(x$1: com.amazonaws.services.autoscaling.model.SetInstanceHealthRequest): Unit = ???
  def setRegion(x$1: com.amazonaws.regions.Region): Unit = ???
  def shutdown(): Unit = ???
  def suspendProcesses(x$1: com.amazonaws.services.autoscaling.model.SuspendProcessesRequest): Unit = ???
  def terminateInstanceInAutoScalingGroup(x$1: com.amazonaws.services.autoscaling.model.TerminateInstanceInAutoScalingGroupRequest): com.amazonaws.services.autoscaling.model.TerminateInstanceInAutoScalingGroupResult = ???
  def updateAutoScalingGroup(x$1: com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest): Unit = ???

}
