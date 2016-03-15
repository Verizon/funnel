//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package funnel.elastic

import java.net.URI
import scalaz.{-\/, \/-, \/, Kleisli}
import scalaz.concurrent.Task
import scala.collection._

//Preset response for given (method, path). result is errorCode or body
case class Rule(method: String = "GET", path: String, result: \/[Int, String])

//Simple "http request" model
case class Request(method: String = "GET", path: String, body: Option[String]) {
  def contains(s: String) = body.exists(_.contains(s))
}

object Rule {
  def GET(path: String, body: String) = new Rule("GET", path, \/-(body))

  def HEAD(path: String, body: String) = new Rule("HEAD", path, \/-(body))

  def PUT(path: String, body: String) = new Rule("PUT", path, \/-(body))

  def POST(path: String, body: String) = new Rule("POST", path, \/-(body))

  def failedGET(path: String, code: Int) = new Rule("GET", path, -\/(code))

  def failedHEAD(path: String, code: Int) = new Rule("HEAD", path, -\/(code))

  def failedPUT(path: String, code: Int) = new Rule("PUT", path, -\/(code))

  def failedPOST(path: String, code: Int) = new Rule("POST", path, -\/(code))

  def defaultRules(cfg: ElasticCfg) = Seq(
    HEAD(s"/_template/${cfg.templateName}", ""),  //template lookup
    Rule.POST(                                    //metric postings
      //esURL will return URL with host. we only need path part
      Elastic.esURL(cfg).dropWhile(_ != '/').drop(2).dropWhile(_ != '/'),
      ""
    )
  )
}

class InMemoryHttpLayer(rules: mutable.ListBuffer[Rule]) extends HttpLayer {

  val requests: mutable.ListBuffer[Request] = mutable.ListBuffer()

  def +(rule: Rule): InMemoryHttpLayer = {
    this.rules += rule
    this
  }

  private def verbString(v: HttpOp) = v match {
    case x: GET  => "GET"
    case x: PUT  => "PUT"
    case x: POST => "POST"
    case x: HEAD => "HEAD"
  }

  private def body(v: HttpOp, cfg: ElasticCfg) = v match {
    case x: PUT  => Some(x.body(cfg))
    case x: POST => Some(x.body(cfg))
    case _ => None
  }

  private def find(verb: String, u: String): Task[String] = {
    rules.find( r => r.method == verb && r.path == u) match {
      case None =>
        Task.fail(HttpException(404))
      case Some(x) =>
        x.result match {
        case -\/(c) => Task.fail(HttpException(c))
        case \/-(body) => Task.now(body)
      }
    }
  }

  override def http(v: HttpOp): Elastic.ES[String] = {
    Kleisli.kleisli(
      (cfg: ElasticCfg) => {
        requests += Request(verbString(v), new URI(v.url(cfg)).getPath, body(v, cfg))
        find(verbString(v), new URI(v.url(cfg)).getPath)
      }
    )
  }
}

object InMemoryHttpLayer {
  def apply(rules: Seq[Rule]): InMemoryHttpLayer =
    new InMemoryHttpLayer(mutable.ListBuffer(rules: _*))

  // config passing schema check and allowing posting new metric docs
  def apply(cfg: ElasticCfg): InMemoryHttpLayer = apply(Rule.defaultRules(cfg))
}