package oncue.svc.funnel
package object internals {

  type ARN = String

  import com.amazonaws.services.sns.AmazonSNSClient
  import com.amazonaws.services.sqs.AmazonSQSClient
  import scalaz.concurrent.Task

  def createTopicWithSubscribedQueue(name: String
    )(sns: AmazonSNSClient, sqs: AmazonSQSClient): Task[ARN] = {
    for {
      q <- SQS.create(name)(sqs)
      t <- SNS.create(name)(sns)
      x <- SNS.subscribe(t,q,"sqs")(sns)
    } yield x
  }

  import java.util.concurrent.atomic.AtomicReference
  import annotation.tailrec

  type Ref[A] = AtomicReference[A]

  implicit class Atomic[A](val atomic: AtomicReference[A]){
    @tailrec final def update(f: A => A): A = {
      val oldValue = atomic.get()
      val newValue = f(oldValue)
      if (atomic.compareAndSet(oldValue, newValue)) newValue else update(f)
    }

    def get: A = atomic.get
  }
}
