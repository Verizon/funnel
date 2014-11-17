package intelmedia.ws.funnel

/**
 * A `Key` represents a variable from Funnel that changes over time.
 * It can be used to get a value from the stream at any given
 * point in time. The `Key` consists of a name, a type, a denomination (units),
 * a textual description, and a map of arbitrary attributes associated with this key
 * (such as its originating Funnel, grouping, or other ancillary metadata).
 */
case class Key[+A] private[funnel](name: String,
                                   typeOf: Reportable[A],
                                   units: Units[A],
                                   description: String,
                                   attributes: Map[String, String]) {

  /** Check if the name of the key ends with the given suffix */
  def endsWith(suffix: String): Boolean = name.endsWith(suffix)

  /** Check if the name of the key starts with the given prefix */
  def startsWith(prefix: String): Boolean = name.startsWith(prefix)

  /** Check if the key has the given attribute assigned to the given value */
  def has(attribute: String, value: String): Boolean =
    attributeMatch(attribute, _ == value)

  /**
   * Check if the key has the given attribute assigned to a value matching
   * the given predicate
   */
  def attributeMatch(attribute: String, p: String => Boolean): Boolean =
    attributes.get(attribute).map(p).getOrElse(false)

  /** Change the name of the key */
  def rename(s: String) = copy(name = s)

  /** Change the description of the key */
  def withDescription(s: String) = copy(description = s)

  /** Replace the attributes associated with the key */
  def withAttributes(m: Map[String, String]) = copy(attributes = m)

  /** Set the value of a named attribute */
  def setAttribute(name: String, value: String) =
    copy(attributes = attributes + (name -> value))

  /** Modify the name of the key using the given function */
  def modifyName(f: String => String): Key[A] = rename(f(name))

  /** Cast the key to a different type */
  def cast[B](R: Reportable[B], U: Units[B]): Option[Key[B]] =
    if (R == typeOf && units == U) Some(this.asInstanceOf[Key[B]])
    else None

  /** Get the default value for keys of this type */
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
