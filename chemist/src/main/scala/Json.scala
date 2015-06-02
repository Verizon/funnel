package funnel
package chemist

object JSON {
  import argonaut._, Argonaut._
  import javax.xml.bind.DatatypeConverter // hacky, but saves the extra dependencies
  import java.net.URI

  implicit class AsDate(in: String){
    def asDate: java.util.Date =
      javax.xml.bind.DatatypeConverter.parseDateTime(in).getTime
  }

  implicit val encodeFlaskID: EncodeJson[FlaskID] = implicitly[EncodeJson[String]].contramap(_.value)
  implicit val encodeURI: EncodeJson[URI] = implicitly[EncodeJson[String]].contramap(_.toString)

  ////////////////////// chemist messages //////////////////////

  /**
   * {
   *   "shard": "instance-f1",
   *   "targets": [
   *     {
   *       "cluster": "testing",
   *       "urls": [
   *         "http://...:5775/stream/sliding",
   *         "http://...:5775/stream/sliding"
   *       ]
   *     }
   *   ]
   * }
   */
  implicit val ShardingSnapshotToJson: EncodeJson[(Flask, Map[String, List[URI]])] =
    EncodeJson((m: (Flask, Map[String, List[URI]])) =>
      ("shard"   := m._1) ->:
      ("targets" := m._2.toList) ->: jEmptyObject
    )

  implicit val flaskToJson: EncodeJson[Flask] =
    EncodeJson((f: Flask) =>
      ("id"       := f.id)       ->:
      ("location" := f.location) ->: jEmptyObject)


  implicit val locationToJson: EncodeJson[Location] = implicitly[EncodeJson[URI]].contramap[Location](_.asURI(""))

  /**
   * {
   *   "firewalls": [
   *     "imdev-flask-1-7-118-pbXOvo-WebServiceSecurityGroup-11WVCT4E6GY52",
   *     "monitor-funnel"
   *   ],
   *   "datacenter": "us-east-1a",
   *   "host": "ec2-54-197-46-246.compute-1.amazonaws.com",
   *   "id": "i-0c24ede2"
   * }
   */
  ////////////////////// flask messages //////////////////////

  /**
   *[
   *  {
   *    "cluster": "imqa-maestro-1-0-261-QmUo7Js",
   *    "urls": [
   *      "http://ec2-23-20-119-134.compute-1.amazonaws.com:5775/stream/sliding",
   *      "http://ec2-23-20-119-134.compute-1.amazonaws.com:5775/stream/uptime",
   *      "http://ec2-54-81-136-185.compute-1.amazonaws.com:5775/stream/sliding",
   *      "http://ec2-54-81-136-185.compute-1.amazonaws.com:5775/stream/uptime"
   *    ]
   *  }
   *]
   */
  implicit val ClustersToJSON: EncodeJson[(String, List[URI])] =
    EncodeJson((t: (String, List[URI])) =>
      ("cluster" := t._1) ->:
      ("urls"   := t._2 ) ->: jEmptyObject
    )

  ////////////////////// lifecycle events //////////////////////

  private def targetMessage(`type`: String, instance: URI, flask: Option[FlaskID], time: Long) = {
    ("type" := `type`) ->:
    ("instance" := instance) ->:
    ("time" := time) ->:
    flask.fold(jEmptyObject)(f => ("flask" := f) ->: jEmptyObject)
  }

  import TargetLifecycle._

  private def discoveryJson(d: Discovery) = {
    ("type" := "Discovery") ->:
    ("target" := d.target.uri.toString) ->:
    ("time" := d.time ) ->:
    jEmptyObject
  }


  implicit val targetMessagesToJson: EncodeJson[TargetMessage] =
    EncodeJson {
      case d @ Discovery(_, _) => discoveryJson(d)
      case Assignment(target, f, l) => targetMessage("Assignment", target.uri, Some(f), l)
      case Confirmation(target, f, l) => targetMessage("Confirmation", target.uri, Some(f), l)
      case Migration(target, f, l) => targetMessage("Migration", target.uri, Some(f), l)
      case Unassignment(target, f, l) => targetMessage("Unassignment", target.uri, Some(f), l)
      case Unmonitoring(target, f, l) => targetMessage("Unmonitoring", target.uri, Some(f), l)
      case Terminated(target,t) => targetMessage("Terminated", target.uri, None, t)
      case Problem(target,f,m,t) =>
        ("type" := "Progblem") ->:
        ("instance" := target.uri) ->:
        ("time" := t) ->:
        ("flask" := f) ->:
        ("msg" := m) ->: jEmptyObject
    }




  implicit val stateChangeToJson: EncodeJson[RepoEvent.StateChange] =
    EncodeJson { sc =>
      ("from-state" := sc.from.toString) ->:
      ("to-state" := sc.to.toString) ->:
      ("message" := sc.msg) ->:
      jEmptyObject
    }

  implicit val newFlaskToJson: EncodeJson[RepoEvent.NewFlask] =
    EncodeJson { nf =>
      ("type" := "NewFlask") ->:
      ("flask" := nf.flask.id) ->:
      ("location" := nf.flask.location.asURI()) ->:
      jEmptyObject
    }

  implicit val repoEventToJson: EncodeJson[RepoEvent] =
    EncodeJson {
      case sc@RepoEvent.StateChange(_,_,_) => stateChangeToJson.encode(sc)
      case nf@RepoEvent.NewFlask(_) => newFlaskToJson.encode(nf)
    }

  def encodeNewTarget(t: Target): Json = {
    ("type" := "NewTarget") ->:
    ("cluster" := t.cluster) ->:
    ("uri" := t.uri) ->:
    jEmptyObject
  }

  def encodeNewFlask(f: Flask): Json = {
    ("type" := "NewFlask") ->:
    ("flask" := f.id) ->:
    ("location" := f.location) ->:
    jEmptyObject
  }

  def encodeTerminatedTarget(u: URI): Json = {
    ("type" := "TerminatedTarget") ->:
    ("uri" := "u") ->:
    jEmptyObject
  }

  def encodeTerminatedFlask(f: FlaskID): Json = {
    ("type" := "TerminatedFlask") ->:
    ("flask" := f.value) ->:
    jEmptyObject
  }

  def encodeMonitored(f: FlaskID, u: URI): Json = {
    ("type" := "Monitored") ->:
    ("flask" := f.value) ->:
    ("uri" := u) ->:
    jEmptyObject
  }

  def encodeProblem(f: FlaskID, u: URI, msg: String): Json = {
    ("type" := "Problem") ->:
    ("flask" := f.value) ->:
    ("uri" := u) ->:
    ("msf" := msg) ->:
    jEmptyObject
  }

  def encodeUnmonitored(f: FlaskID, u: URI): Json = {
    ("type" := "Unmonitored") ->:
    ("flask" := f.value) ->:
    ("uri" := u) ->:
    jEmptyObject
  }

  def encodeAssigned(f: FlaskID, t: Target): Json = {
    ("type" := "Assigned") ->:
    ("flask" := f.value) ->:
    ("cluster" := t.cluster) ->:
    ("uri" := t.uri) ->:
    jEmptyObject
  }

  implicit val platformEventToJson: EncodeJson[PlatformEvent] =
    EncodeJson {
      case PlatformEvent.NewTarget(t) => encodeNewTarget(t)
      case PlatformEvent.NewFlask(f) => encodeNewFlask(f)
      case PlatformEvent.TerminatedTarget(u) => encodeTerminatedTarget(u)
      case PlatformEvent.TerminatedFlask(f) => encodeTerminatedFlask(f)
      case PlatformEvent.Monitored(f, u) => encodeMonitored(f, u)
      case PlatformEvent.Problem(f, u, msg) => encodeProblem(f, u, msg)
      case PlatformEvent.Unmonitored(f, u) => encodeUnmonitored(f, u)
      case PlatformEvent.Assigned(f, t) => encodeAssigned(f, t)
      case PlatformEvent.NoOp => ("type" := "NoOp") ->: jEmptyObject
    }
}
