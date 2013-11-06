package intelmedia.ws.monitoring

case class Histogram[K](frequencies: Map[K,Int])
