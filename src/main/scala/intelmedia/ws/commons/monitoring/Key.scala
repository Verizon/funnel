package intelmedia.ws.commons.monitoring

class Key[+A] private[monitoring](val label: String, val id: java.util.UUID) {
  override def equals(a: Any): Boolean =
    a.asInstanceOf[Key[Any]].id == id
  override def hashCode = id.hashCode
  override def toString = s"Key($label)"
}

object Key {
  private[monitoring] def apply[A](label: String): Key[A] =
    new Key(label, java.util.UUID.randomUUID)
}
