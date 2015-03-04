package funnel
package zeromq

case class Topic(topic: String) extends AnyVal {
  override def toString = topic
}
