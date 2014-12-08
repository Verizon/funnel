package oncue.svc.funnel

case class Histogram[K](frequencies: Map[K,Int])
