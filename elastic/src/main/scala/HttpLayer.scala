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

import journal.Logger
import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import scalaz.concurrent.Task

/**
  * Simple abstraction of ES HTTP API (so we can mock it in the tests)
  */
sealed trait HttpOp {
  def url: Reader[ElasticCfg, String]
}

case class POST(url: Reader[ElasticCfg, String], body: Reader[ElasticCfg, String]) extends HttpOp

case class GET(url: Reader[ElasticCfg, String]) extends HttpOp

case class HEAD(url: Reader[ElasticCfg, String]) extends HttpOp

case class PUT(url: Reader[ElasticCfg, String], body: Reader[ElasticCfg, String]) extends HttpOp

case class HttpException(code: Int) extends RuntimeException {
  def serverError = code >= 500
  def clientError = code >= 400 && code < 500

  override def toString() = s"HttpException($code)"
}

trait HttpLayer {
  def http(v: HttpOp): Elastic.ES[String]
}

//Dispatch-based implementation
trait DispatchHttpLayer extends HttpLayer {
  private[this] val log = Logger[Elastic.type]

  /**
    * Given a scala future, convert it to task when the future completes
    */
  private def fromScalaFuture[A](a: => Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatchNonFatal[A](t.get)) }}

  /****************************** http i/o ******************************/

  import dispatch._, Defaults._

  /**
    * sadly required as dispatch has a very naïve error handler by default,
    * and in this case we're looking to output the body of the response to the log
    * in order to help with debugging in the event documents were not able to be
    * submitted to the backend. Should function exactly like the default impl
    * with the addition of the logging.
    */
  private val handler = new FunctionHandler[String]({ resp =>
    val status = resp.getStatusCode
    if((status / 100) == 2) resp.getResponseBody
    else {
      log.error(s"Backend returned a code ${resp.getStatusCode} failure. Body response was: ${resp.getResponseBody}")
      throw StatusCode(status)
    }
  })

//  def post(json: Json) = StatusCode

  private def req(v: HttpOp): Kleisli[Id.Id, ElasticCfg, Req] = v match {
    case PUT(u, b) => for {
      uri <- u
      body <- b
    } yield url(uri).PUT.setContentType("application/json", "UTF-8") << body

    case POST(u, b) => for {
      uri <- u
      body <- b
    } yield url(uri).POST.setContentType("application/json", "UTF-8") << body

    case GET(u) => u.map(s => url(s).GET)
    case HEAD(u) => u.map(s => url(s).GET)
  }

  def http(v: HttpOp): Elastic.ES[String] =
    Kleisli.kleisli(
      (cfg: ElasticCfg) =>
        for {
          r <- Task.delay(req(v)(cfg))
          response <- fromScalaFuture(cfg.http(r > handler)).handleWith {
            case e: StatusCode => Task.fail(HttpException(e.code))
          }
        } yield response
    )
}

object SharedHttpLayer {
  val H = new DispatchHttpLayer {}
}