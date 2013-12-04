package intelmedia.ws
package monitoring

import com.twitter.algebird.Group
import org.scalacheck._
import Prop._
import Arbitrary._
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz.Nondeterminism
import scalaz.stream.{process1, Process}

object MonitoringSpec extends Properties("monitoring"){
  import argonaut.{DecodeJson, EncodeJson, Parse}

  def roundTrip[A:EncodeJson:DecodeJson](a: A): Prop = {
    val out1: String = implicitly[EncodeJson[A]].apply(a).nospaces
    // println(out1)
    val parsed = Parse.decode[A](out1).toOption.get
    val out2: String = implicitly[EncodeJson[A]].apply(parsed).nospaces
    out1 == out2 || (s"out1: $out1, out2: $out2" |: false)
  }

  property("NaN handling") = secure {
    import JSON._
    roundTrip(Stats.statsGroup.zero) &&
    roundTrip(Double.NaN) &&
    roundTrip(Double.PositiveInfinity) &&
    roundTrip(Double.NegativeInfinity)
  }

  property("prettyURL") = secure {
    import Monitoring._
    val i1 = new java.net.URL("http://google.com:80/foo/bar/baz")
    val i2 = new java.net.URL("http://google.com/foo/bar/baz")
    val i3 = new java.net.URL("http://google.com:80")
    val i4 = new java.net.URL("http://google.com")
    val o = (prettyURL(i1), prettyURL(i2), prettyURL(i3), prettyURL(i4))
    o._1 == "google.com-80/foo/bar/baz" &&
    o._2 == "google.com/foo/bar/baz" &&
    o._3 == "google.com-80" &&
    o._4 == "google.com" || ("" + o |: false)
  }
}

