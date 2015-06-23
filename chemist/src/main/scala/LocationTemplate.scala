package funnel
package chemist

/**
 * This has to be the naievest template system ever
 * constructed, but given its all being read from a
 * config, im not going to worry about parsing or
 * security issues, and instead do blind string
 * replacement like a BAWWWWSSSS.
 */
case class LocationTemplate(template: String){
  def has(n: NetworkScheme): Boolean =
    template.startsWith(n.scheme)

  def build(pairs: (String,String)*): String =
    pairs.toSeq.foldLeft(template){ (a,b) =>
      val (k,v) = b
      a.replace(k,v)
    }
}
