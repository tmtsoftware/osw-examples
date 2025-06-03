package m1cs.segments.streams.server

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.util.ByteString
import org.apache.pekko.stream.scaladsl.{Flow, Framing, Sink, Tcp}
import m1cs.segments.streams.shared.SocketMessage
import m1cs.segments.streams.shared.SocketMessage.{MAX_FRAME_LEN, MsgHdr, MsgHdrDcl, NET_HDR_ID, NET_HDR_LEN, RSP_TYPE, SourceId}

import java.nio.ByteOrder
import scala.concurrent.duration.*
import scala.concurrent.{Future, Promise}

sealed trait SocketServerStreamBase {
  def terminate(): Future[Unit]
}

//noinspection ScalaUnusedSymbol
// No-op class: Assume socket server is running externally
class NoOpSocketServerStream(host: String = "127.0.0.1", port: Int = 8023)(implicit system: ActorSystem[?])
    extends SocketServerStreamBase {

  override def terminate(): Future[Unit] = Future.successful(())
}

/**
 * A TCP socket server that listens on the given host:port for connections
 * and accepts String messages in the format "id cmd". A reply is sent for
 * each message: "cmd Completed."
 *
 * Currently any command can be sent and "Completed." is always returned.
 * If the command is "DELAY ms" the reply is made after the given ms delay,
 * otherwise after a random delay configured in reference.conf.
 */
class SocketServerStream(host: String = "127.0.0.1", port: Int = 8023)(implicit system: ActorSystem[?])
    extends SocketServerStreamBase {

  import system.*

  private val connections = Tcp()(system.classicSystem).bind(host, port)

  // For random delay before replying to message
  private val rnd      = new scala.util.Random
  private val minDelay = system.settings.config.getInt("m1cs.segment.streams.server.minDelay") // ms
  private val maxDelay = system.settings.config.getInt("m1cs.segment.streams.server.maxDelay") // ms

  // Reply to an incoming socket message.
  // The DELAY command is supported here, with one arg: the number of ms. For example: "DELAY 1000",
  // which just sleeps for that amount of time before replying with: "DELAY: Completed".
  // For now, all other commands get an immediate reply.
  private def handleMessage(bs: ByteString): Future[ByteString] = {
    val msg       = SocketMessage.parse(bs)
    val cmd       = msg.cmd.split(' ').head
    val s         = if (cmd.toUpperCase().startsWith("ERROR")) "Error." else "Completed."
    val respMsg   = s"$cmd: $s"
    val msgHdrDcl = MsgHdrDcl(NET_HDR_ID, MsgHdr.encodedSize + respMsg.length)
    val msgHdr    = MsgHdr(RSP_TYPE, SourceId(120), msg.hdr.seqNo)
    val resp      = SocketMessage(msgHdrDcl, msgHdr, respMsg)
    val delayMs =
      if (cmd.toUpperCase() == "DELAY")
        msg.cmd.split(" ")(1).toInt
      else
        minDelay + rnd.nextInt((maxDelay - minDelay) + 1)

    if (delayMs == 0) {
      Future.successful(resp.toByteString)
    }
    else {
      val p = Promise[ByteString]()
      system.classicSystem.scheduler.scheduleOnce(delayMs.millis)(p.success(resp.toByteString))
      p.future
    }
  }

  private val binding =
    connections
      .to(Sink.foreach { connection =>
        // server logic, parses incoming commands
        val commandParser = Flow[ByteString]
          .takeWhile(_ != ByteString("BYE"))
          .mapAsyncUnordered(100)(handleMessage)

        // noinspection DuplicatedCode
        // XXX Note: Looks like there might be a bug in Framing.lengthField, requiring the function arg!
        val serverLogic = Flow[ByteString]
//          .via(Framing.lengthField(4, 4, MAX_FRAME_LEN, ByteOrder.BIG_ENDIAN, (_, i) => i + NET_HDR_LEN))
          .via(Framing.lengthField(4, 4, MAX_FRAME_LEN, ByteOrder.BIG_ENDIAN))
          .via(commandParser)

        val _ = connection.handleWith(serverLogic)
      })
      .run()

  binding.foreach { b =>
    println(s"server: local address: ${b.localAddress}")

  }

  /**
   * Shuts down the server
   *
   * @return
   */
  override def terminate(): Future[Unit] = {
    binding.flatMap(_.unbind())
  }
}

object MaybeSocketServerStream {
  def apply(host: String = "127.0.0.1", port: Int = 8023)(implicit system: ActorSystem[?]): SocketServerStreamBase = {
    if (sys.env.contains("USE_NATIVE_SOCKET_SERVER") || sys.props.contains("USE_NATIVE_SOCKET_SERVER")) {
      println("XXX Using bin/CmdSrvSim")
      NoOpSocketServerStream(host, port)
    }
    else {
      println("XXX Using simulation")
      SocketServerStream(host, port)
    }
  }
}

object SocketServerStreamApp {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "SocketServerStream")
    // TODO: Add host, port options
    MaybeSocketServerStream.apply()
  }
}
