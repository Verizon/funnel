package intelmedia.ws.monitoring

case class Datapoint[A](key: Key[A], typeOf: Reportable[A], units: Units[A], value: A) {

  /** Safely cast this `Datapoint` to the given type. */
  def cast[B](t: Reportable[B]): Option[Datapoint[B]] =
    if (typeOf == t) Some(this.asInstanceOf[Datapoint[B]])
    else None
}
