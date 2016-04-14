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

import funnel.instruments._

object metrics {
  val ErrorsFlask     = counter("chemist/errors/flask") 
  val ErrorsDiscovery = counter("chemist/errors/discovery") 

  val GatherAssignedLatency = lapTimer("chemist/io/flask/gather-assigned") 
  val MonitorCallLatency = lapTimer("chemist/io/flask/monitor") 
  val UnmonitorCallLatency = lapTimer("chemist/io/flask/unmonitor") 

  val DiscoveryLatency = lapTimer("chemist/io/discovery") 

  val MonitorCommands   = counter("chemist/model/commands/monitor") 
  val UnmonitorCommands = counter("chemist/model/commands/unmonitor") 

  val ModelAssignedSources = numericGauge("chemist/model/streams/assigned", 0) 
  val ModelAllSources  = numericGauge("chemist/model/streams/all", 0)
  val ModelDeadSources = numericGauge("chemist/model/streams/dead", 0)

  val ModelDeadFlasks   = numericGauge("chemist/model/flasks/dead", 0) 
  val ModelLiveFlasks   = numericGauge("chemist/model/flasks/live", 0) 
}
