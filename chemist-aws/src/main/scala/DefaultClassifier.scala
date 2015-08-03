package funnel
package chemist
package aws

import scalaz.concurrent.Task

/**
 * This default implementation does not properly handle the various upgrade
 * cases that you might encounter when migrating from one set of clusters to
 * another, but it instead provided as a default where all avalible flasks
 * and chemists are are "active". There are a set of upgrade scenarios where
 * you do not want to mirror from an existing flask cluster, so they are not
 * targets, nor are they active flasks.
 *
 * Providing this function with a task return type so that extensions can do I/O
 * if they need too (clearly a cache locally would be needed in that case)
 *
 * It is highly recomended you override this with your own classification logic.
 */
object DefaultClassifier extends Classifier[AwsInstance]{
  import Classification._

  private val Flask = "Flask"
  private val Chemist = "Chemist"

  def isApplication(prefix: String)(i: AwsInstance): Boolean =
    i.application.map(_.name.trim.toLowerCase.startsWith(prefix)).getOrElse(false)

  def isFlask(i: AwsInstance): Boolean =
    isApplication(Flask)(i)

  def isChemist(i: AwsInstance): Boolean =
    isApplication(Chemist)(i)

  val task: Task[AwsInstance => Classification] = {
    Task.delay {
      instance =>
        if(isFlask(instance)) ActiveFlask
        else if(isChemist(instance)) ActiveChemist
        else ActiveTarget
    }
  }
}
