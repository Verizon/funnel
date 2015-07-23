package funnel
package chemist
package aws

trait Aws extends Platform {
  type Constraint <: AwsConfig
  type Config = Constraint
}

trait DefaultAws extends Aws {
  type Constraint = AwsConfig
}
