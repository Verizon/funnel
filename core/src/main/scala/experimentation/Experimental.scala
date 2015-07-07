package funnel
package experimentation

import scala.collection.concurrent.TrieMap

/** A wrapper for instrument constructors to support experimentation and A/B testing */
case class Experimental[I,K](underlying: (Key[K] => Key[K]) => I) {
  val memo = new TrieMap[(ExperimentID, GroupID), I]
  val unadorned = underlying(identity)

  def apply(token: Map[ExperimentID, GroupID])(f: I => Unit): Unit =
    if (token.isEmpty)
      f(unadorned)
    else token.foreach {
      case (e, g) => f(memo.getOrElseUpdate(
        (e, g), underlying(_.setAttribute(AttributeKeys.experimentID, e)
                            .setAttribute(AttributeKeys.experimentGroup, g))))
    }
}

