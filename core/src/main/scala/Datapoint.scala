package oncue.svc.funnel

case class Datapoint[A](key: Key[A], value: A) {

  def typeOf: Reportable[A] = key.typeOf
  def units: Units[A] = key.units

  /** Safely cast this `Datapoint` to the given type. */
  def cast[B](t: Reportable[B]): Option[Datapoint[B]] =
    if (typeOf == t) Some(this.asInstanceOf[Datapoint[B]])
    else None
}
