package funnel
package chemist
package aws

import knobs._
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Regions,Region}
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import dispatch.Http
import concurrent.duration.Duration
import funnel.aws._
import scalaz.stream.async.signalOf
import scalaz.concurrent.Strategy

/**
 * Settings needed to subscribe ot the appropriate queues
 * used by Chemist in AWS.
 */
case class QueueConfig(
  queueName: String,
  topicName: String
)

/**
 * Used to figure out where this particular chemist
 * is running, which is going to be useful for figuring
 * out service-locality in the future.
 */
case class MachineConfig(
  id: String,
  location: Location
)

case class AwsConfig(
  templates: List[LocationTemplate],
  network: NetworkConfig,
  machine: MachineConfig,
  queue: QueueConfig,
  sns: AmazonSNS,
  sqs: AmazonSQS,
  ec2: AmazonEC2,
  asg: AmazonAutoScaling,
  cfn: AmazonCloudFormation,
  commandTimeout: Duration,
  includeVpcTargets: Boolean,
  sharder: Sharder,
  classifier: Classifier[AwsInstance],
  maxInvestigatingRetries: Int
) extends PlatformConfig {

  val discovery: AwsDiscovery = new AwsDiscovery(ec2, asg, classifier, templates)

  val repository: Repository = new StatefulRepository

  val http: Http = Http.configure(
    _.setAllowPoolingConnection(true)
     .setConnectionTimeoutInMs(commandTimeout.toMillis.toInt))

  val signal = signalOf(true)(Strategy.Executor(Chemist.serverPool))

  val remoteFlask = new HttpFlask(http, repository, signal)
}

object AwsConfig {
  def readConfig(cfg: Config): AwsConfig = {
    val topic     = cfg.require[String]("chemist.sns-topic-name")
    val queue     = cfg.require[String]("chemist.sqs-queue-name")
    val templates = cfg.require[List[String]]("chemist.target-resource-templates")
    val aws       = cfg.subconfig("aws")
    val network   = cfg.subconfig("chemist.network")
    val timeout   = cfg.require[Duration]("chemist.command-timeout")
    val usevpc    = cfg.lookup[Boolean]("chemist.include-vpc-targets").getOrElse(false)
    val sharding  = cfg.lookup[String]("chemist.sharding-strategy")
    val classifiy = cfg.lookup[String]("chemist.classification-stratagy")
    val retries   = cfg.require[Int]("chemist.max-investigating-retries")
    AwsConfig(
      templates 		          = templates.map(LocationTemplate),
      network                 = readNetwork(network),
      queue                   = QueueConfig(topic, queue),
      sns                     = readSNS(aws),
      sqs                     = readSQS(aws),
      ec2                     = readEC2(aws),
      asg                     = readASG(aws),
      cfn                     = readCFN(aws),
      sharder                 = readSharder(sharding),
      classifier              = readClassifier(classifiy),
      commandTimeout          = timeout,
      includeVpcTargets       = usevpc,
      machine                 = readMachineConfig(cfg),
      maxInvestigatingRetries = retries
    )
  }

  private def readClassifier(c: Option[String]): Classifier[AwsInstance] =
    c match {
      case Some("default") => DefaultClassifier
      case _               => DefaultClassifier
    }

  private def readMachineConfig(cfg: Config): MachineConfig =
    MachineConfig(
      id = cfg.lookup[String]("aws.instance-id").getOrElse("local"),
      location = Location(
        host = cfg.require[String]("aws.meta-data.local-ipv4"),
        port = cfg.require[Int]("chemist.network.funnel-port"),
        datacenter = cfg.require[String]("aws.meta-data.placement.region"),
        intent = LocationIntent.Mirroring,
        templates = Nil
      )
    )

  private def readSharder(c: Option[String]): Sharder =
    c match {
      case Some("least-first-round-robin") => LFRRSharding
      case Some("random")                  => RandomSharding
      case _                               => RandomSharding
    }

  private def readNetwork(cfg: Config): NetworkConfig =
    NetworkConfig(cfg.require[String]("host"), cfg.require[Int]("port"))

  private def readCFN(cfg: Config): AmazonCloudFormation =
    CFN.client(
      new BasicAWSCredentials(
        cfg.require[String]("access-key"),
        cfg.require[String]("secret-key")),
      cfg.lookup[String]("proxy-host"),
      cfg.lookup[Int]("proxy-port"),
      cfg.lookup[String]("proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("region")))
    )

  private def readSNS(cfg: Config): AmazonSNS =
    SNS.client(
      new BasicAWSCredentials(
        cfg.require[String]("access-key"),
        cfg.require[String]("secret-key")),
      cfg.lookup[String]("proxy-host"),
      cfg.lookup[Int]("proxy-port"),
      cfg.lookup[String]("proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("region")))
    )

  private def readSQS(cfg: Config): AmazonSQS =
    SQS.client(
      new BasicAWSCredentials(
        cfg.require[String]("access-key"),
        cfg.require[String]("secret-key")),
      cfg.lookup[String]("proxy-host"),
      cfg.lookup[Int]("proxy-port"),
      cfg.lookup[String]("proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("region")))
    )

  private def readASG(cfg: Config): AmazonAutoScaling =
    ASG.client(
      new BasicAWSCredentials(
        cfg.require[String]("access-key"),
        cfg.require[String]("secret-key")),
      cfg.lookup[String]("proxy-host"),
      cfg.lookup[Int]("proxy-port"),
      cfg.lookup[String]("proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("region")))
    )

  private def readEC2(cfg: Config): AmazonEC2 =
    EC2.client(
      new BasicAWSCredentials(
        cfg.require[String]("access-key"),
        cfg.require[String]("secret-key")),
      cfg.lookup[String]("proxy-host"),
      cfg.lookup[Int]("proxy-port"),
      cfg.lookup[String]("proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("region")))
    )

}
