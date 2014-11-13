package intelmedia.ws.funnel

case class Key[+A] private[funnel](name: String,
                                   typeOf: Reportable[A],
                                   units: Units[A],
                                   description: String,
                                   attributes: Map[String, String]) {
  def endsWith(suffix: String): Boolean = name.endsWith(suffix)
  def startsWith(prefix: String): Boolean = name.startsWith(prefix)
  def has(attribute: String, value: String): Boolean =
    attributeMatch(attribute, _ == value)
  def attributeMatch(attribute: String, p: String => Boolean): Boolean =
    attributes.get(attribute).map(p).getOrElse(false)
  def rename(s: String) = copy(name = s)
  def withDescription(s: String) = copy(description = s)
  def withAttributes(m: Map[String, String]) = copy(attributes = m)
  def setAttribute(name: String, value: String) =
    copy(attributes = attributes + (name -> value))
  def modifyName(f: String => String): Key[A] = rename(f(name))
  def cast[B](R: Reportable[B], U: Units[B]): Option[Key[B]] =
    if (R == typeOf && units == U) Some(this.asInstanceOf[Key[B]])
    else None
  def default: Option[A] = Units.default(typeOf, units)
}

object Key {

  def StartsWith(prefix: String) = new Function1[Key[Any],Boolean] {
    def apply(k: Key[Any]) = k.startsWith(prefix)
    override def toString = "Key.StartsWith("+prefix+")"
  }
  def EndsWith(suffix: String) = new Function1[Key[Any],Boolean] {
    def apply(k: Key[Any]) = k.endsWith(suffix)
    override def toString = "Key.EndsWith("+suffix+")"
  }

  def apply[A](name: String, units: Units[A], desc: String = "", attribs: Map[String, String] = Map())(
    implicit R: Reportable[A]): Key[A] = Key(name, R, units, desc, attribs)

  implicit def keyToMetric[A](k: Key[A]): Metric[A] = Metric.key(k)
}
