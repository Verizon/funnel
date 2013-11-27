package intelmedia.ws.monitoring

case class Key[+A] private[monitoring](name: String, typeOf: Reportable[A], units: Units[A]) {
  def matches(prefix: String): Boolean = name.startsWith(prefix)
  def rename(s: String) = copy(name = s)
  def cast[B](R: Reportable[B], U: Units[B]): Option[Key[B]] =
    if (R == typeOf && units == U) Some(this.asInstanceOf[Key[B]])
    else None
}

object Key {
  private[monitoring] def apply[A](name: String, units: Units[A])(implicit R: Reportable[A]): Key[A] =
    Key(name, R, units)

  implicit def keyToMetric[A](k: Key[A]): Metric[A] = Metric.key(k)
}
