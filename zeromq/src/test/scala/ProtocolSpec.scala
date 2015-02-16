package funnel
package zeromq

import org.scalatest.{FlatSpec,Matchers}

class ProtocolSpec extends FlatSpec with Matchers {

  "protocol companion" should "parse protocols from strings" in {
    Protocol.fromString("ipc") should be (Some(IPC))
    Protocol.fromString("sdfgdfgdf") should be (None)
    Protocol.fromString("tcp") should be (Some(TCP))
    Protocol.fromString("TCP") should be (Some(TCP))
    Protocol.fromString("udp") should be (Some(UDP))
  }

}