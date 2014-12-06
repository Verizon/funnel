package oncue.svc.funnel.chemist

import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.locks.ReentrantLock

case class BoundedStack[A](maximumEntries: Int){
  import scala.collection.JavaConverters._

  private val list: Deque[A] = new ConcurrentLinkedDeque[A]
  private val lock = new ReentrantLock

  def push(item: A): Unit = {
    println("===========================")
    println(item)
    println("===========================")

    def add: Unit = {
      list.push(item)
      if (list.size > maximumEntries) list.removeLast()
    }

    lock.lock()
    try add
    finally lock.unlock()
  }

  def pop: Option[A] = Option(list.poll)
  def peek: Option[A] = Option(list.peekFirst)
  def isEmpty: Boolean = list.isEmpty
  def size: Int = list.size
  def toSeq: Seq[A] = list.iterator.asScala.toSeq

  assert(maximumEntries > 0, "The maximum number of entries must greater than zero.")
}
