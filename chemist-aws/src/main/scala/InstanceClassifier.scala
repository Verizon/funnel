package funnel.chemist.aws

sealed trait InstanceClassifier
object InstanceClassifier {
  case object ActiveFlask extends InstanceClassifier
  case object InactiveFlask extends InstanceClassifier
  case object ActiveTarget extends InstanceClassifier
  case object Unknown extends InstanceClassifier
}