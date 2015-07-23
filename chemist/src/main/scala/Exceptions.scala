package funnel.chemist

case class InstanceNotFoundException(
  instanceId: String,
  kind: String = "instance") extends RuntimeException {
  override val getMessage: String = s"No $kind found with the id: '$instanceId'"
}

case class InvalidLocationException(location: Location) extends RuntimeException {
  override val getMessage: String = s"No hostname is specified for the specified location: $location"
}

case class NotAFlaskException(e: AutoScalingEvent) extends RuntimeException {
  override val getMessage: String = s"The specified scaling event was not a flask $e"
}

case class FlaskMissingSupervision[A](e: A) extends RuntimeException {
  override val getMessage: String = s"The specified flask instance does not appear to have a supervision socket"
}
