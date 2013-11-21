package intelmedia.ws.monitoring

case class KeyInfo[+A](key: Key[A], typeOf: Reportable[A], units: Units[A])


