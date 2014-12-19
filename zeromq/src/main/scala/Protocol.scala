package oncue.svc.funnel
package zeromq

abstract class Protocol(override val toString: String)
case object TCP extends Protocol("tcp")
case object IPC extends Protocol("ipc")
case object UDP extends Protocol("udp")
case object InProc extends Protocol("proc")
case object Pair extends Protocol("pair")
