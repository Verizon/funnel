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
/*
  implicit val InstanceToJson: EncodeJson[Instance] =
    EncodeJson((i: Instance) =>
      ("id"         := i.id) ->:
      ("host"       := i.location.host) ->:
      ("version"    := i.application.map(_.version)) ->:
      ("datacenter" := i.location.datacenter) ->:
      ("firewalls"  := i.firewalls.toList) ->:
      ("tags"       := i.tags) ->: jEmptyObject
    )
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

  implicit val terminatedFlaskToJson: EncodeJson[RepoEvent.TerminatedFlask] =
    EncodeJson { t =>
      ("type" := "TerminatedFlask") ->:
      ("instance" := t.id) ->:
      jEmptyObject
    }

  implicit val terminatedTargetToJson: EncodeJson[RepoEvent.TerminatedTarget] =
    EncodeJson { t =>
      ("type" := "TerminatedTarget") ->:
      ("instance" := t.id) ->:
      jEmptyObject
    }

  implicit val repoEventToJson: EncodeJson[RepoEvent] =
    EncodeJson {
      case sc@RepoEvent.StateChange(_,_,_) => stateChangeToJson.encode(sc)
      case nf@RepoEvent.NewFlask(_) => newFlaskToJson.encode(nf)
      case t@RepoEvent.TerminatedFlask(_) => terminatedFlaskToJson.encode(t)
      case t@RepoEvent.TerminatedTarget(_) => terminatedTargetToJson.encode(t)
    }
}
