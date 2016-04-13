//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package funnel
package chemist
package aws

import funnel.instruments._

object metrics {
  val LifecycleEvents = counter("chemist/lifecycle/events", 0, "number of lifecycle events within a given window")

  object discovery {
    val ListInventory = lapTimer("chemist/discovery/inventory")
    val LookupManyAws = lapTimer("chemist/discovery/aws/lookups")
    val LookupAwsFailure = counter("chemist/discovery/aws/errors")
    val ValidateLatency = lapTimer("chemist/discovery/validate")
  }

  object model {
    val hostsTotal = numericGauge("chemist/hosts/total", 0)
    val hostsValid = numericGauge("chemist/hosts/valid", 0)
    val hostsInValid = numericGauge("chemist/hosts/invalid", 0)
    val hostsFlask = numericGauge("chemist/hosts/flasks", 0)
    val hostsActiveFlask = numericGauge("chemist/hosts/activeFlasks", 0)
  }
}
