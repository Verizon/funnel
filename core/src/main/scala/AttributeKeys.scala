package funnel

/**
 * Hard-coded attribute key names. These really should be types, not strings.
 * But since these things are going over the wire, we should put them in one
 * place so that every place that refers to them agrees.
 */
object AttributeKeys {
  val bucket = "bucket"
  val source = "source"
}
