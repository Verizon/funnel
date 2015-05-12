package funnel

case class Edge(
  origin: ContinuousGauge[Edge.Origin],
  destination: ContinuousGauge[Edge.Destination],
  timer: Timer[Periodic[Stats]],
  state: TrafficLight
)
object Edge {
  type Origin = String
  type Destination = String
}
