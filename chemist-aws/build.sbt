
import oncue.build._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Revolver.settings

ScalaTest.settings

Custom.compilation

Custom.resources

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-ec2"            % V.aws,
  "com.amazonaws" % "aws-java-sdk-autoscaling"    % V.aws,
  "com.amazonaws" % "aws-java-sdk-sns"            % V.aws,
  "com.amazonaws" % "aws-java-sdk-sqs"            % V.aws,
  "com.amazonaws" % "aws-java-sdk-cloudformation" % V.aws
)

fork in Test := true

mainClass in Revolver.reStart := Some("funnel.chemist.aws.Main")
