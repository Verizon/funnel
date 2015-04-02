package funnel
package chemist
package aws

import com.amazonaws.services.sqs.model.Message

object TestMessage {
  def apply(body: String, id: String = java.util.UUID.randomUUID.toString): Message =
    (new Message)
      .withMessageId(id)
      .withBody(body)
}
