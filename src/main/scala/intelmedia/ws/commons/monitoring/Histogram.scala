package intelmedia.ws.commons.monitoring

case class Histogram[K](frequencies: Map[K,Int])
