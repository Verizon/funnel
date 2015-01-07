package oncue.svc.funnel
package agent

import argonaut._, Argonaut._
// import oncue.svc.funnel.instruments.Counter

case class Request(
  cluster: String,
  metrics: List[ArbitraryMetric]
)
case class ArbitraryMetric(
  name: String,
  kind: InstrumentKind,
  value: Option[String]
)   // units: String,

case class InstrumentRequest(
  cluster: String,
  counters: List[ArbitraryMetric] = Nil,
  timers: List[ArbitraryMetric] = Nil
)

trait InstrumentKind
object InstrumentKinds {
  case object Counter extends InstrumentKind
  case object Timer extends InstrumentKind
// case object GaugeNumeric extends InstrumentKind
// case object GaugeString extends InstrumentKind
// case object TrafficLight extends InstrumentKind
}

object JSON {
  import InstrumentKinds._
  import concurrent.duration._

  // {
  //   "cluster": "foo-whatever",
  //   "metrics": [
  //     {
  //       "name": "ntp/whatever",
  //       "kind": "timer",
  //       "value": 0.1234
  //     }
  //   ]
  // }

  implicit def JsonToInstrumentRequest: DecodeJson[InstrumentRequest] =
    DecodeJson(c => for {
      k <- (c --\ "cluster").as[String]
      m <- (c --\ "metrics").as[List[ArbitraryMetric]]
    } yield InstrumentRequest(
      cluster = k,
      counters = m.filter(_.kind == Counter),
      timers   = m.filter(_.kind == Timer)
    ))

  implicit val JsonToArbitraryMetric: DecodeJson[ArbitraryMetric] =
    DecodeJson(c => for {
      n <- (c --\ "name").as[String]
      k <- (c --\ "kind").as[InstrumentKind]
      v <- (c --\ "value").as[String].option
    } yield ArbitraryMetric(n, k, v))

  implicit val JsonToInstrumentKind: DecodeJson[InstrumentKind] = DecodeJson { c =>
    c.as[String].map(_.toLowerCase).flatMap {
      case "counter"           => DecodeResult.ok { Counter }
      case "timer"             => DecodeResult.ok { Timer }
      // case "gauge-with-double" => DecodeResult.ok { GaugeWithDouble }
      // case "gauge-with-long"   => DecodeResult.ok { GaugeWithLong }
      // case "gauge-with-string" => DecodeResult.ok { GaugeWithString }
      // case "traffic-light"     => DecodeResult.ok { TrafficLight }
    }
  }


}

