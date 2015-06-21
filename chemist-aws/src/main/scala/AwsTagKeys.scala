package funnel
package chemist
package aws

object AwsTagKeys {
  val name                = "funnel:target:name"
  val qualifier           = "funnel:target:qualifier"
  val version             = "funnel:target:version"
  val supervisionTemplate = "funnel:telemetry:uri-template"
  val mirrorTemplate      = "funnel:mirror:uri-template"
}
