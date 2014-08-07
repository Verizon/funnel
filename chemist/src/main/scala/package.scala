package oncue.svc.funnel

package object chemist {
  import java.util.concurrent.ConcurrentHashMap
  import java.net.URL

  type Shards = ConcurrentHashMap[String, Set[URL]]

  type InstanceID = String
}

