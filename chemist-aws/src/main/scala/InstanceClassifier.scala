package funnel.chemist.aws

/**
 * The concept here is that upon discovering instances, they
 * are subsequently classified into a setup of finite groups.
 * These groups can then be used for making higher level
 * choices about what an instance should be used for (or if
 * it should be dropped entirely)
 */
sealed trait InstanceClassifier

object InstanceClassifier {
  case object ActiveFlask extends InstanceClassifier
  case object InactiveFlask extends InstanceClassifier
  case object ActiveTarget extends InstanceClassifier
  case object ActiveChemist extends InstanceClassifier
  case object InactiveChemist extends InstanceClassifier
  case object Unknown extends InstanceClassifier
}
