package intelmedia.ws.monitoring

import argonaut.{DecodeJson, EncodeJson}
import java.io.InputStream
import java.util.concurrent.ExecutorService
import scalaz.concurrent.Task
import scalaz.stream._
import scalaz.stream.{Process => P}

object SSE {

  import JSON._

  def dataEncode[A](a: A)(implicit A: EncodeJson[A]): String =
    "data: " + A(a).spaces2.replace("\n", "\ndata: ")

  /**
   * Write a server-side event stream (http://www.w3.org/TR/eventsource/)
   * of the given metrics to the `Writer`. This will block the calling
   * thread indefinitely.
   */
  def writeEvents(events: Process[Task, Datapoint[Any]],
                  sink: java.io.Writer): Unit =
    events.map(kv => s"event: reportable\n${dataEncode(kv)(EncodeDatapoint[Any])}\n")
          .intersperse("\n")
          .map(writeTo(sink))
          .run.run

  /**
   * Write a server-side event stream (http://www.w3.org/TR/eventsource/)
   * of the given keys to the given `Writer`. This will block the calling
   * thread indefinitely.
   */
  def writeKeys(events: Process[Task, Key[Any]], sink: java.io.Writer): Unit =
    events.map(k => s"event: key\n${dataEncode(k)}\n")
          .intersperse("\n")
          .map(writeTo(sink))
          .run.run

  private def writeTo(sink: java.io.Writer): String => Unit =
    line => try {
      sink.write(line)
      sink.flush // this is a line-oriented protocol,
                 // so we flush after each line, otherwise
                 // consumer may get delayed messages
    }
    catch { case e: java.io.IOException =>
      // when client disconnects we'll get a broken pipe
      // IOException from the above `sink.write`. This
      // gets translated to normal termination
      throw Process.End
    }

  /// parsing

  case class ParseError(msg: String) extends Exception

  /**
   * Streaming parser for an SSE stream. Example, given:
   *
   * {{{
   *   event: blah
   *   data: uno
   *   data: dos
   *
   *   event: other-event
   *   data: tres
   * }}}
   *
   * It will emit ("blah", "uno\ndos"), ("other-event","tres")
   *
   * This parser is rather brittle, and doesn't implement all the
   * various features of arbitrary SSE streams, described here:
   * http://www.w3.org/TR/2012/WD-eventsource-20120426/ In the
   * event of any unexpected formatting, raises a `ParseError`
   * within the returned `Process`.
   */
  def blockParser: Process1[String, (String,String)] = {
    def awaitingEvent: Process1[String,(String,String)] =
      P.await1[String].flatMap { line =>
        if (line.forall(_.isWhitespace)) awaitingEvent
        else if (line.startsWith(":")) awaitingEvent
        else {
          val (k,v) = parseLine(line)
          if (k != "event") throw ParseError("expected 'event'")
          collectingData(v, new StringBuilder)
        }
      }
    def collectingData(event: String, buf: StringBuilder):
    Process1[String,(String,String)] =
      P.await1[String].flatMap { line =>
        if (line.forall(_.isWhitespace)) P.emit(event -> buf.toString)
        else {
          val (k, v) = parseLine(line)
          if (k != "data") throw ParseError("expected 'data'")
          val nl = if (buf.isEmpty) "" else "\n"
          collectingData(event, buf.append(v).append(nl))
        }
      }
    awaitingEvent
  }

  // "data: blah" -> ("data", "blah")
  // "foo: bar" -> ("foo", "bar")
  def parseLine(line: String): (String, String) = {
    val c = line.indexOf(':')
    if (c == -1) (line, "")
    else {
      val hd = line.substring(0, c)
      val tl = line.drop(c+1).trim
      (hd, tl)
    }
  }

  /**
   * Attempt to read a unique event from the given URL/prefix.
   * Example: `readEvent("http://localhost:8001", "sliding/response-time")`
   * If successful, returns the unique `Key`, along with the event
   * stream for that `Key`. Raises an exception withing the `Task`
   * in the event of an error, or if the given prefix does not
   * uniquely determine a `Key`.
   */
  def readEvent(url: String, prefix: String):
      Task[(Key[Any], Process[Task, Any])] =
    urlDecode[List[Key[Any]]](url + "/keys").map { ks =>
      ks.filter(_.matches(prefix)) match {
        case List(k) => (k, readEvents(s"$url/${k.id.toString}").map(_.value))
        case ks2 => sys.error(s"'$prefix' did not return unique key set: $ks2")
      }
    }

  /**
   * Return a stream of all events from the given URL.
   * Example: `readEvents("http://localhost:8001/sliding/jvm")`.
   */
  def readEvents(url: String):
      Process[Task, Datapoint[Any]] =
    urlLinesR(url).pipe(blockParser).map {
      case (_,data) => parseOrThrow[Datapoint[Any]](data)
    }

  // various helper functions

  def parseOrThrow[A:DecodeJson](s: String): A =
    argonaut.Parse.decodeEither[A](s).fold(e => throw ParseError(e), identity)

  def urlDecode[A:DecodeJson](url: String): Task[A] =
    urlFullR(url).map(parseOrThrow[A])

  def urlLinesR(url: String): Process[Task, String] =
    Process.suspend { linesR(new java.net.URL(url).openStream) }

  def urlFullR(url: String): Task[String] =
    urlLinesR(url).chunkAll.map(_.mkString("\n")).runLastOr("")

  /**
   * Copied from latest version of scalaz-stream
   */
  def linesR(in: InputStream)(implicit S: ExecutorService =
                              Monitoring.defaultPool): Process[Task,String] =
    io.resource(Task(scala.io.Source.fromInputStream(in))(S))(
             src => Task(src.close)(S)) { src =>
      lazy val lines = src.getLines // A stateful iterator
      Task { if (lines.hasNext) lines.next else throw Process.End } (S)
    }
}
