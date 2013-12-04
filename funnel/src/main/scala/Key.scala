package intelmedia.ws.monitoring

case class Key[+A] private[monitoring](name: String, typeOf: Reportable[A], units: Units[A]) {
  def endsWith(suffix: String): Boolean = name.endsWith(suffix)
  def startsWith(prefix: String): Boolean = name.startsWith(prefix)
  def rename(s: String) = copy(name = s)
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

  def apply[A](name: String, units: Units[A])(implicit R: Reportable[A]): Key[A] =
    Key(name, R, units)

  implicit def keyToMetric[A](k: Key[A]): Metric[A] = Metric.key(k)
}
