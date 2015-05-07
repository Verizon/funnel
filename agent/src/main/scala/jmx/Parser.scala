package funnel
package agent
package jmx

import scalaz.\/

object Parser {

  def parse(cn: String, at: String) =
    fromCanonicalName(cn).map(_ + "/" + at)

  /**
   * This gnarly replace hack is needed because Java products using
   * Yammer / Coda metric library are incorrectly publishing MBean
   * object names that are quoted on the wire. Whilst the agent could
   * already pick these values up, they get fucked up on the funnel wire
   * as they are quouted in the HTTP path, and thus "not safe".
   */
  def fromCanonicalName(cn: String) = \/.fromTryCatchNonFatal {
    val Array(domain, tail) = cn.split(':')
    (formatDomain(domain) + "/" +
    keyspaces(tail).map(_._2).mkString("/")).toLowerCase.replace("\"", "")
  }

  /**
   * This really is not ideal as the elements come in from the order they
   * were actually added to the object name by the creating JMX endpoint,
   * which unhelpfully varies product to product. As such, this will look
   * right sometimes, and be backwards in other cases.
   *
   * //sigh
   */
  private[this] def keyspaces(tail: String): Seq[(String,String)] =
    tail.split(',').reverse.map { part =>
      val Array(key,value) = part.trim.split('=')
      (key,value)
    }

  private[this] def formatDomain(dn: String): String =
    dn.toLowerCase.replace('.','/')

}
