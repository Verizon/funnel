package funnel
package chemist

import org.scalatest.{FlatSpec,Matchers}

class BoundedStackSpec extends FlatSpec with Matchers {
  val alpha = ('A' to 'Z').toList

  it should "pop values in order they were pushed" in {
    val B1 = new BoundedStack[Char](26)
    alpha.foreach(B1.push)

    alpha.foldLeft(List.empty[Char]){ (a, c) =>
      B1.pop.toList ++ a
    } should equal (alpha)
  }

  it should "pop the last whcih was pushed" in {
    val B1 = new BoundedStack[Char](26)
    alpha.foreach(B1.push)

    B1.pop should equal(Some('Z')) // last pushed, first to pop
    B1.peek should equal(Some('Y'))
    B1.pop should equal(Some('Y'))
    B1.push('A')
    B1.pop should equal(Some('A'))
  }

  it should "honour the maximum number of entries" in {
    val B1 = new BoundedStack[Int](10)
    (1 to 11).foreach(B1.push)
    B1.peek should equal(Some(11))
    B1.toSeq should equal ((2 to 11).reverse.toSeq)
  }

  import scalaz.concurrent.Task
  import scalaz._, Scalaz._

  it should "work reasonably even with a lot of thrashing on the stack" in {
    val B1 = new BoundedStack[Int](10)
    (1 to 10001).toVector.traverse(i => Task(B1.push(i))).run
    B1.peek should equal(Some(10001))
    B1.toSeq.toList should equal ( (2 to 10001).toList.reverse.take(10) )
  }

}