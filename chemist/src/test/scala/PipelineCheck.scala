package funnel
package chemist

import java.net.URI
import java.util.UUID

import scalaz.==>>
import scalaz.concurrent.Task

import Chemist.Context
import LocationIntent._
import PlatformEvent._
import Sharding.Distribution

import org.scalacheck._
import Gen.{ alphaNumChar, listOfN, oneOf }
import Arbitrary.arbitrary
import Prop.{ falsified, forAll, passed, BooleanOperators }

class StaticDiscovery(targets: Map[TargetID, Set[Target]], flasks: Map[FlaskID, Flask]) extends Discovery {
  def listTargets: Task[Seq[(TargetID, Set[Target])]] = Task.delay(targets.toSeq)
  def listUnmonitorableTargets: Task[Seq[(TargetID, Set[Target])]] = Task.now(Seq.empty)
  def listAllFlasks: Task[Seq[Flask]] = Task.delay(flasks.values.toSeq)
  def listActiveFlasks: Task[Seq[Flask]] = Task.delay(flasks.values.toSeq)
  def lookupFlask(id: FlaskID): Task[Flask] = Task.delay(flasks(id))	// Can obviously cause the Task to fail
  def lookupTargets(id: TargetID): Task[Set[Target]] = Task.delay(targets(id))	// Can obviously cause the Task to fail
}

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

  def genTargetID = for {
    id <- alphaNumStr
  } yield TargetID(id)
  implicit lazy val arbTargetID: Arbitrary[TargetID] = Arbitrary(genTargetID)

  def genDiscovery = for {
    tpairs <- arbitrary[List[(TargetID, Set[Target])]]
    fpairs <- arbitrary[List[(FlaskID, Flask)]]
  } yield new StaticDiscovery(tpairs.toMap, fpairs.toMap)
  implicit lazy val arbDiscovery: Arbitrary[StaticDiscovery] = Arbitrary(genDiscovery)

  def genNewTarget = for {
    target <- arbitrary[Target]
  } yield NewTarget(target)

  def genNewFlask = for {
    flask <- arbitrary[Flask]
  } yield NewFlask(flask)

  def genTerminatedTarget = for {
    uri <- arbitrary[URI]
  } yield TerminatedTarget(uri)

  def genTerminatedFlask = for {
    flaskID <- arbitrary[FlaskID]
  } yield TerminatedFlask(flaskID)

  def genNoOp = Gen.const(NoOp)

  implicit lazy val arbPlatformEvent: Arbitrary[PlatformEvent] =
    Arbitrary(oneOf(genNewTarget, genNewFlask, genTerminatedTarget, genTerminatedFlask, genNoOp))

  def genContextOfPlatformEvent = for {
    d <- arbitrary[Distribution]
    e <- arbitrary[PlatformEvent]
  } yield Context(d, e)
  implicit lazy val arbContextOfPlatformEvent: Arbitrary[Context[PlatformEvent]] =
    Arbitrary(genContextOfPlatformEvent)

  implicit lazy val arbSharder: Arbitrary[Sharder] = Arbitrary(oneOf(RandomSharding, LFRRSharding))

  property("newFlask works") = forAll { (f: Flask, s: Sharder, d: Distribution) =>
    val (nd, _) = Pipeline.handle.newFlask(f, s)(d)
    ("The existing Distribution does not contain the Flask" |:
      !d.keySet.contains(f)) &&
    ("The new Distribution contains the Flask" |:
      nd.keySet.contains(f)) &&
    ("The existing and new Distributions have the same Targets" |:
      Sharding.targets(d) == Sharding.targets(nd))
  }

  property("newTarget works") = forAll { (t: Target, s: Sharder, d: Distribution) =>
    val nd = Pipeline.handle.newTarget(t, s)(d)
    ("The existing Distribution does not contain the Target" |:
      !Sharding.targets(d).contains(t)) &&
     ("The new Distribution contains the Target" |:
       (d.size > 0) ==> Sharding.targets(nd).contains(t))
  }

  property("transform works") = forAll { (sd: StaticDiscovery, s: Sharder, c: Context[PlatformEvent]) =>
    val d: Distribution = c.distribution
    val e: PlatformEvent = c.value
    val cp: Context[Plan] = Pipeline.transform(sd, s)(c)
    val nd: Distribution = cp.distribution
    val p: Plan = cp.value

    e match {
      case NewTarget(t) => p match {
        case Distribute(w) =>
          (d.size > 0) ==>
          ("The old Distribution does not contain the new Target" |: !Sharding.targets(d).contains(t)) &&
          ("The Work does contain the new Target" |: Sharding.targets(w).contains(t))
        case _ => falsified
      }
      case NewFlask(f) => p match {
        case Redistribute(stop, start) =>
          ("The new Flask is not in the old Distribution" |: !Sharding.shards(d).contains(f)) &&
          ("The new Flask is in the new Distribution" |: Sharding.shards(start).contains(f)) &&
          ("The Targets in the old Distribution are all in the new Distribution" |:
            Sharding.targets(d) == Sharding.targets(start)) &&
          ("All of the Flasks have Work" |: start.values.forall(!_.isEmpty))
        case _ => falsified
      }
      case NoOp => passed
      case TerminatedFlask(f) => p match {
        case Produce(tasks) =>
          val ts = tasks.run.map { _ match {
            case NewTarget(t) => t
            case _ => throw new RuntimeException("Not all Produced PlatformEvents were NewTargets")
          }}
          ("Discovery's Targets are all Produced" |:
            sd.listTargets.run.flatMap(_._2) == ts.toSet) &&
          ("The terminated Flask is not in the new Distribution" |: !Sharding.shards(nd).contains(f))
        case _ => falsified
      }
      case TerminatedTarget(t) => p match {
        case Ignore => passed
        case _ => falsified
      }
      case _ => falsified
    }
  }
}
