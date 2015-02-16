package funnel
package agent
package jmx

import scalaz.\/

object Parser {

  def parse(cn: String, at: String) =
    fromCanonicalName(cn).map(_ + "/" + at)

  def fromCanonicalName(cn: String) = \/.fromTryCatchNonFatal {
    val Array(domain, tail) = cn.split(':')
    (formatDomain(domain) + "/" +
    keyspaces(tail).map(_._2).mkString("/")).toLowerCase
  }

  private[this] def keyspaces(tail: String): Seq[(String,String)] =
    tail.split(',').reverse.map { part =>
      val Array(key,value) = part.trim.split('=')
      (key,value)
    }

  private[this] def formatDomain(dn: String): String =
    dn.toLowerCase.replace('.','/')

}
