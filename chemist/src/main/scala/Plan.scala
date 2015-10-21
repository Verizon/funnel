package funnel
package chemist

import Sharding.Distribution

sealed trait Plan
case class Distribute(dist: Distribution) extends Plan
case object Ignore extends Plan
