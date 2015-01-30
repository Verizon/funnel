package oncue.svc.funnel
package zeromq

abstract class Protocol(name: String){
  override def toString: String = name
}
object Protocol {
  lazy val all: Seq[Protocol] =
    TCP :: IPC :: UDP :: InProc :: Pair :: Nil

  def fromString(s: String): Option[Protocol] =
    all.find(_.toString == s.toLowerCase)
}

case object TCP extends Protocol("tcp")
case object IPC extends Protocol("ipc")
case object UDP extends Protocol("udp")
case object InProc extends Protocol("proc")
case object Pair extends Protocol("pair")

