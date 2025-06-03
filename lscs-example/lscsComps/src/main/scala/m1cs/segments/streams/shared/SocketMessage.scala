package m1cs.segments.streams.shared

import org.apache.pekko.util.ByteString
import SocketMessage.*

import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets

/**
 * Represents commands to the server as well as responses from the server.
 */
object SocketMessage {
  // XXX TODO: use enum?
  // system wide message ID's
  case class MessageId(id: Int)

  val CMD_TYPE: MessageId        = MessageId(0x10000)          // !< CmdMsg
  val RSP_TYPE: MessageId        = MessageId(0x20000)          // !< RspMsg
  val LOG_TYPE: MessageId        = MessageId(0x30000)          // !< LogMsg
  val DATA_TYPE: MessageId       = MessageId(0x40000)          // !< DataHdr
  val SEG_STATUS_DATA: MessageId = MessageId(DATA_TYPE.id + 1) // !< SegmentStatusMsg
//  WH_STRAIN_DATA //!< WarpHarnStrainMsg
//  WH_CALIB_DATA //!< WarpHarnCalibMsg
//  SENS_CFG_DATA //!< SensConfigMsg
//  SENS_DIAG_DATA //!< SensDiagMsg
//  ACT_CFG_DATA //!< ActConfigMsg
//  ACT_DIAG_DATA //!< ActDiagMsg
//  ACT_TRANSIENT_DATA //!< ActTransDataMsg
//  ACT_SNAPSHOT_DATA //!< ActSnapshotDataMsg
//  SEG_REALTIME_DATA //!< SegRtDataMsg
//  ACT_REALTIME_DATA //!< ActTargetMsg
//  RAW_DATA //!< RawDataMsg data
//  MAX_MSG_ID //!< Maximum valid message id

  // ascii representation for "<TT>"
  val NET_HDR_ID                   = 0x3c54543e
  private[streams] val NET_HDR_LEN = 2 * 4

  private val MAX_CMD_LEN = 256

  // Max size of a command with headers
  private[streams] val MAX_FRAME_LEN = MAX_CMD_LEN + MsgHdr.encodedSize + NET_HDR_LEN

  // sender application ID (TODO: Define ids)
  case class SourceId(id: Int)

  /**
   * TCP internal message header
   * @param hdrId message header id
   * @param msgLen length of user's message in bytes
   */
  case class MsgHdrDcl(hdrId: Int, msgLen: Int)

  object MsgHdr {
    // Size in bytes when encoded
    val encodedSize: Int = 3 * 4
  }

  // #MsgHdr
  /**
   * @param msgId message type
   * @param srcId sender application id
   * @param seqNo sequence number
   */
  case class MsgHdr(msgId: MessageId, srcId: SourceId, seqNo: Int)
  // #MsgHdr

  /**
   * Parses the command from the given ByteString
   */
  def parse(bs: ByteString): SocketMessage = {
    println("XXX SocketMessage.parse() ")
    try {
      val buffer = bs.toByteBuffer.order(ByteOrder.BIG_ENDIAN)

      // from struct msg_hdr_dcl
      val hdrId     = buffer.getInt() // XXX TODO check if valid!
      val msgLen    = buffer.getInt()
      val msgHdrDcl = MsgHdrDcl(hdrId, msgLen)

      val buffer1 = buffer.order(ByteOrder.LITTLE_ENDIAN)

      // MsgHdr
      val msgId = MessageId(buffer1.getInt() & 0x0ffff)
      val srcId = SourceId(buffer1.getInt() & 0x0ffff)
      val seqNo = buffer1.getInt() & 0x0ffff

      // Message/Command contents
      val msgHdr = MsgHdr(msgId, srcId, seqNo)

      val bytes = Array.fill(msgLen - MsgHdr.encodedSize)(0.toByte)
      buffer1.get(bytes)
      // Remove any trailing null chars
      val s = new String(bytes, StandardCharsets.UTF_8).split('\u0000').head
      SocketMessage(msgHdrDcl, msgHdr, s)
    }
    catch {
      case ex: Exception =>
        ex.printStackTrace()
        throw ex
    }
  }
}

//#SocketMessage
/**
 * The type of a message sent to the server (also used for the reply).
 *
 * @param hdrDcl TCP internal message header
 * @param hdr message header
 * @param cmd the actual text of the command
 */
case class SocketMessage(hdrDcl: MsgHdrDcl, hdr: MsgHdr, cmd: String) {
//#SocketMessage

  /**
   * Encodes the command for sending (see parse)
   */
  def toByteString: ByteString = {
    val buffer1 = ByteBuffer.allocateDirect(NET_HDR_LEN + hdrDcl.msgLen + 1).order(ByteOrder.BIG_ENDIAN)
    // from struct msg_hdr_dcl
    buffer1.putInt(hdrDcl.hdrId)
    buffer1.putInt(hdrDcl.msgLen + 1) // Note: add 1 to hdrDcl.msgLen for terminating null char

    val buffer = buffer1.order(ByteOrder.LITTLE_ENDIAN)
    // from MsgHdr
    buffer.putInt(hdr.msgId.id)
    buffer.putInt(hdr.srcId.id)
    buffer.putInt(hdr.seqNo)

    // Contents of message/command
    buffer.put(cmd.getBytes(StandardCharsets.UTF_8))
    buffer.put(0.toByte) // Add terminating null char for C server!

    buffer.flip()
    ByteString.fromByteBuffer(buffer)
  }
}
