package funnel
package zeromq

/**
 * A payload for transmitting via a ∅ socket
 */
case class Transported(
  /** the scheme by which this packet was encoded */
  scheme: Scheme,
  /** The version of the scheme */
  version: Version,
  /** The version of the scheme */
  window: Option[Window],
  /** An optional "topic" for this packet, which can be used for filtering subscriptions */
  topic: Option[Topic],
  /** The actual payload */
  bytes: Array[Byte]
) {
  /**
   * construct the String for the header packet
   */
  def header: String = (window,topic) match {
    case (None, None) => s"$scheme/$version"
    case (Some(w), None) => s"$scheme/$version/$w"
    case (None, Some(t)) => s"$scheme/$version/${Windows.unknown}/$t"
    case (Some(w), Some(t)) => s"$scheme/$version/$w/$t"
  }
}

object Transported {
  import Versions._

  val P = new scalaparsers.Parsing[Unit] {}
  import scalaparsers.ParseState
  import scalaparsers.Supply
  import scalaparsers.Pos
  import scalaparsers.Err
  import P._

  val slash = ch('/')
  val notSlash: Parser[String] = satisfy(_ != '/').many map(_.mkString)

  val scheme: Parser[Scheme] = notSlash map Schemes.fromString
  val version: Parser[Version] = notSlash map Versions.fromString
  val window: Parser[Option[Window]] = notSlash map Windows.fromString

  val topic: Parser[Option[Topic]] =
    slash.optional flatMap { _ match {
                              case None => unit(None)
                              case Some(s) => (notSlash map {t => Some(Topic(t))}).orElse(Some(Topic("")))
                            }
    }

  val windowTopic: Parser[(Option[Window], Option[Topic])] =
    (slash.optional).flatMap { sl =>
      sl match  {
        case None => unit((None, None))
        case Some(_) => for {
          w <- window
          t <- topic
        } yield(w -> t)
      }
    }

  val headerParse: Parser[Array[Byte] => Transported] =
    for {
      s <- scheme << slash
      v <- version
      wt <- windowTopic
    } yield (bytes => Transported(s,v,wt._1, wt._2, bytes))

  /**
   * Reconstructed the Transported instance on the receive side given the header and payload
   */
  def apply(h: String, bytes: Array[Byte]): Transported = {
    headerParse.run(ParseState(
                      loc = Pos.start("header", h),
                      input = h,
                      s = (),
                      layoutStack = List()), Supply.create) match {
      case Left(e) => Transported(Schemes.unknown, Versions.unknown, None, None, bytes)
      case Right(f) => f._2(bytes)
    }
  }
}

/**
 * A typeclass for preparing an A for being written to a ∅ socket, It
 * needs to be able to produce a Transportable object for any instance
 * of A
 */
abstract class Transportable[A] {
  def apply(a: A): Transported
}

object Transportable {
  // create a Transportable from a A => Transported
  def apply[A](f: A => Transported): Transportable[A] = new Transportable[A] { def apply(a: A) = f(a) }
}
