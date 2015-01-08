package oncue.svc.laboratory

/**
 * Whatever genius decided that java.net.URL would actually
 * reach out to the DNS to do an equality check... *sigh*
 * This makes using java.net.URL in collections really really
 * painful due to all the equality checking that takes place.
 *
 * This small wrapper looks to alevieate this pain and make it
 * explicit when you want to go down to a `java.net.URL`.
 */
case class SafeURL(underlying: String){
  def toJavaURL = new java.net.URL(underlying)
}
object SafeURL {
  def apply(u: java.net.URL): SafeURL = SafeURL(u.toString)
}
