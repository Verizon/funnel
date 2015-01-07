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
  value: String
)   // units: String,

case class TypedRequest(
  cluster: String,
  counters: List[Counter[Periodic[Double]]] = Nil,
  timers: List[Timer[Periodic[Stats]]] = Nil
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
  import InstrumentKinds._, InstrumentBuilder._
  import concurrent.duration._

  // TIM: this really should not be here. Move elsewhere; pass as an argument
  // val I = new Instruments(1.minute)

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

  private def toTypedInstrument[A : InstrumentBuilder](a: List[ArbitraryMetric]): List[A] = {
    val B = implicitly[InstrumentBuilder[A]]
    a.filter(m => B.filter(m.kind)).flatMap(x => List(B.apply(x)))
  }

  implicit def JsonToTypedRequest(implicit I: Instruments): DecodeJson[TypedRequest] =
    DecodeJson(c => for {
      k <- (c --\ "cluster").as[String]
      m <- (c --\ "metrics").as[List[ArbitraryMetric]]
    } yield TypedRequest(
      cluster = k,
      counters = toTypedInstrument[Counter[Periodic[Double]]](m),
      timers   = toTypedInstrument[Timer[Periodic[Stats]]](m)
    ))

  implicit val JsonToArbitraryMetric: DecodeJson[ArbitraryMetric] =
    DecodeJson(c => for {
      n <- (c --\ "name").as[String]
      k <- (c --\ "kind").as[InstrumentKind]
      v <- (c --\ "value").as[String]
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

