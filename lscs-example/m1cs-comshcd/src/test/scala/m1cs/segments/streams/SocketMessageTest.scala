package m1cs.segments.streams

import m1cs.segments.streams.shared.SocketMessage
import m1cs.segments.streams.shared.SocketMessage.{CMD_TYPE, MsgHdr, SourceId}
import org.scalatest.funsuite.AnyFunSuite

class SocketMessageTest extends AnyFunSuite {

  test("Test to and from ByteString") {
    val s    = "1234567890"
    val msg  = SocketMessage(MsgHdr(CMD_TYPE, SourceId(0), MsgHdr.encodedSize + s.length, 1), s)
    val bs   = msg.toByteString
    val msg2 = SocketMessage.parse(bs)
    assert(msg == msg2)
  }
}
