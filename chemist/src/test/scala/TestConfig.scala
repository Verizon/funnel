package funnel
package chemist

import scalaz.stream.async
import scalaz.stream.async.mutable.Signal
import scalaz.concurrent.Task

class TestConfig extends PlatformConfig {
  val templates: List[LocationTemplate] =
    List(LocationTemplate("http://@host:@port/stream/previous"))
  val network = NetworkConfig("127.0.0.1",64523)
  val discovery: Discovery = new StaticDiscovery(Map(), Map())
  val remoteFlask: RemoteFlask = LoggingRemote
  def http: dispatch.Http = ???
  val sharder: Sharder = RandomSharding
  val maxInvestigatingRetries = 6
  val state: StateCache = MemoryStateCache

  class StaticDiscovery(targets: Map[TargetID, Set[Target]], flasks: Map[FlaskID, Flask]) extends Discovery {
    def listTargets: Task[Seq[(TargetID, Set[Target])]] = Task.delay(targets.toSeq)
    def listUnmonitorableTargets: Task[Seq[(TargetID, Set[Target])]] = Task.now(Seq.empty)
    def listAllFlasks: Task[Seq[Flask]] = Task.delay(flasks.values.toSeq)
    def listActiveFlasks: Task[Seq[Flask]] = Task.delay(flasks.values.toSeq)
    def lookupFlask(id: FlaskID): Task[Flask] = Task.delay(flasks(id))	// Can obviously cause the Task to fail
    def lookupTargets(id: TargetID): Task[Set[Target]] = Task.delay(targets(id))	// Can obviously cause the Task to fail
  }
}
