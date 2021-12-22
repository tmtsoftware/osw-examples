package m1cs.segments.streams.server

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.ByteString
import akka.stream.scaladsl.{Flow, Framing, Sink, Tcp}
import m1cs.segments.streams.shared.SocketMessage
import m1cs.segments.streams.shared.SocketMessage.{MAX_FRAME_LEN, MsgHdr, NET_HDR_LEN, RSP_TYPE, SourceId}

import java.nio.ByteOrder
import scala.concurrent.duration.*
import scala.concurrent.{Future, Promise}

/**
 * A TCL socket server that listens on the given host:port for connections
 * and accepts String messages in the format "id cmd". A reply is sent for
 * each message: "id COMPLETED".
 *
 * Currently any command can be sent and COMPLETED is always returned.
 * If the command is "DELAY ms" the reply is made after the given ms delay.
 */
class SocketServerStream(host: String = "127.0.0.1", port: Int = 8023)(implicit system: ActorSystem[?]) {

  import system.*

  private val connections = Tcp()(system.classicSystem).bind(host, port)

  // For random delay before replying to message
  private val rnd = new scala.util.Random
  private val minDelay = system.settings.config.getInt("m1cs.segment.streams.server.minDelay") // ms
  private val maxDelay = system.settings.config.getInt("m1cs.segment.streams.server.maxDelay") // ms

  // Reply to an incoming socket message.
  // The DELAY command is supported here, with one arg: the number of ms. For example: "DELAY 1000",
  // which just sleeps for that amount of time before replying with: "DELAY: Completed".
  // For now, all other commands get an immediate reply.
  private def handleMessage(bs: ByteString): Future[ByteString] = {
    val msg = SocketMessage.parse(bs)
    val cmd = msg.cmd.split(' ').head
    val s = if (cmd.startsWith("ERROR")) "ERROR" else "COMPLETED"
    val respMsg = s"$cmd: $s"
    val resp = SocketMessage(MsgHdr(RSP_TYPE, SourceId(120), MsgHdr.encodedSize + respMsg.length, msg.hdr.seqNo), respMsg)
    val delayMs = if (cmd == "DELAY")
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

        //noinspection DuplicatedCode
        // XXX Note: Looks like there might be a bug in Framing.lengthField, requiring the function arg!
        val serverLogic = Flow[ByteString]
          .via(Framing.lengthField(4, 4, MAX_FRAME_LEN, ByteOrder.BIG_ENDIAN, (_, i) => i + NET_HDR_LEN))
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
  def terminate(): Future[Unit] = {
    binding.flatMap(_.unbind())
  }
}

object SocketServerStream extends App {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "SocketServerStream")
  // TODO: Add host, port options
  new SocketServerStream()
}
