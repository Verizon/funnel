package intelmedia.ws.monitoring

case class Datapoint[A](key: Key[A], typeOf: Reportable[A], units: Units[A], value: A)
