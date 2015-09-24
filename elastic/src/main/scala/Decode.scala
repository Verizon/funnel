package funnel
package elastic

import scalaz.concurrent.Task

/** URL decoding extractor for strings */
object Decode {
  import java.net.URLDecoder
  import java.nio.charset.Charset

  trait Extract {
    def charset: Charset
    def apply(raw: String): Option[String] =
      unapply(raw)
    def unapply(raw: String): Option[String] =
      Task.delay(URLDecoder.decode(raw, charset.name())).attempt.map(_.toOption).run
  }

  /** Extract a string from a UTF8 URL-encoded string */
  object utf8 extends Extract {
    val charset = Charset.forName("utf8")
  }
}
