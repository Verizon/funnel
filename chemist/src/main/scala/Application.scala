package funnel
package chemist

/**
 * Application is meant to represent the logically unique deployment
 * of a given application. For example, foo service 1.2.3 deployment A,
 * and foo service 1.2.3 deployment B and foo service 2.3.4 are all
 * unique applications as far as chemist knows (this is needed for other
 * parts of the system to support experimentation etc)
 */
case class Application(
  name: String,
  version: String,
  qualifier: Option[String]
){
  override def toString: String =
    s"$name-$version${qualifier.map("-"+_).getOrElse("")}"
}
