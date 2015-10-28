package funnel
package chemist

import java.net.URI
import java.util.UUID

import scalaz.==>>

import LocationIntent._
import Sharding.Distribution

import org.scalacheck.{ Arbitrary, Gen, Properties }
import Arbitrary.arbitrary
import org.scalacheck.Prop.forAll

object PipelineSpecification extends Properties("Pipeline") {
  def genUUID = UUID.randomUUID()
  implicit lazy val arbUUID: Arbitrary[UUID] = Arbitrary(genUUID)

  def genURI = new URI("http://localhost:8080/chemist/" + genUUID)
  implicit lazy val arbURI: Arbitrary[URI] = Arbitrary(genURI)

  def genTarget = for {
    id  <- arbitrary[UUID]
    uri <- arbitrary[URI]
  } yield Target(id.toString, uri)
  implicit lazy val arbTarget: Arbitrary[Target] = Arbitrary(genTarget)

  implicit lazy val arbLocationIntent: Arbitrary[LocationIntent] = Arbitrary(Gen.oneOf(Mirroring, Ignored))

  def genLocationTemplate = for {
    template <- arbitrary[String]
  } yield LocationTemplate(template)
  implicit lazy val arbLocationTemplate: Arbitrary[LocationTemplate] = Arbitrary(genLocationTemplate)

  def genLocation = for {
    host <- arbitrary[String]
    port <- arbitrary[Int]
    datacenter <- arbitrary[String]
    intent <- arbitrary[LocationIntent]
    templates <- arbitrary[List[LocationTemplate]]
  } yield Location(host = host, port = port, datacenter = datacenter, intent = intent, templates = templates)
  implicit lazy val arbLocation: Arbitrary[Location] = Arbitrary(genLocation)

  def genFlaskID = for {
    id <- arbitrary[String]
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
}
