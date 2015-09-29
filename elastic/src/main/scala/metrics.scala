package funnel
package elastic

import instruments._

object metrics {
  val NonHttpErrors    = counter("elastic/errors/other")
  val HttpResponse5xx  = counter("elastic/http/5xx")
  val HttpResponse4xx  = counter("elastic/http/4xx")
  val HttpResponse2xx  = lapTimer("elastic/http/2xx")
}
