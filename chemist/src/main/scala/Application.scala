package funnel
package chemist

case class Application(
  name: String,
  version: String
){
  override def toString: String = s"$name-v$version"
}

