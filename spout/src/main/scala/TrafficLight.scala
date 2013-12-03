package intelmedia.ws.monitoring

case class TrafficLight(gauge: Gauge[Continuous[String], String]){
  import TrafficLight._
  def red = gauge.set(Red)
  def green = gauge.set(Green)
  def amber = gauge.set(Amber)
  // for the americans
  def yellow = gauge.set(Amber)
  def key = gauge.key
}
object TrafficLight {
  private[monitoring] val Red = "red"
  private[monitoring] val Amber = "amber"
  private[monitoring] val Green = "green"
}
