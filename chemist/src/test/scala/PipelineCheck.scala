package funnel
package chemist

import java.net.URI
import java.util.UUID

import scalaz.==>>

import LocationIntent._
import Sharding.Distribution

import org.scalacheck.{ Arbitrary, Gen, Properties }
import Arbitrary.arbitrary
import Gen.{ alphaNumChar, listOf, listOfN, oneOf }
import org.scalacheck.Prop.{ BooleanOperators, forAll }

object PipelineSpecification extends Properties("Pipeline") {
  /** Generates alphanumeric characters */
  def alphaNumStr: Gen[String] =
    listOfN(10, alphaNumChar).map(_.mkString).suchThat(_.forall(c => c.isDigit || c.isLetter))

  def genUUID = UUID.randomUUID()
  implicit lazy val arbUUID: Arbitrary[UUID] = Arbitrary(genUUID)

  def genURI = for {
    str <- alphaNumStr
  } yield new URI("http://localhost:8080/chemist/" + str)
  implicit lazy val arbURI: Arbitrary[URI] = Arbitrary(genURI)

  def genTarget = for {
    id  <- arbitrary[UUID]
    uri <- arbitrary[URI]
  } yield Target(id.toString, uri)
  implicit lazy val arbTarget: Arbitrary[Target] = Arbitrary(genTarget)

  implicit lazy val arbLocationIntent: Arbitrary[LocationIntent] = Arbitrary(oneOf(Mirroring, Ignored))

  def genLocationTemplate = for {
    template <- alphaNumStr
  } yield LocationTemplate(template)
  implicit lazy val arbLocationTemplate: Arbitrary[LocationTemplate] = Arbitrary(genLocationTemplate)

  def genLocation = for {
    host <- alphaNumStr
    port <- arbitrary[Int]
    datacenter <- alphaNumStr
    intent <- arbitrary[LocationIntent]
    templates <- arbitrary[List[LocationTemplate]]
  } yield Location(host = host, port = port, datacenter = datacenter, intent = intent, templates = templates)
  implicit lazy val arbLocation: Arbitrary[Location] = Arbitrary(genLocation)

  def genFlaskID = for {
    id <- alphaNumStr
  } yield FlaskID(id)
  implicit lazy val arbFlaskID: Arbitrary[FlaskID] = Arbitrary(genFlaskID)

  def genFlask = for {
    flaskID <- arbitrary[FlaskID]
    location <- arbitrary[Location]
  } yield Flask(flaskID, location)
  implicit lazy val arbFlask: Arbitrary[Flask] = Arbitrary(genFlask)

  def genDistribution = for {
    pairs <- arbitrary[List[(Flask, Set[Target])]]
  } yield ==>>(pairs: _*)
  implicit lazy val arbDistribution: Arbitrary[Distribution] = Arbitrary(genDistribution)

  property("newFlask works") = forAll { (f: Flask, d: Distribution) =>
    val (nd, _) = Pipeline.handle.newFlask(f, RandomSharding)(d)
    ("The existing Distribution does not contain the Flask" |:
      !d.keySet.contains(f)) &&
    ("The new Distribution contains the Flask" |:
      nd.keySet.contains(f)) &&
    ("The existing and new Distributions have the same Targets" |:
      Sharding.targets(d) == Sharding.targets(nd))
  }
}
