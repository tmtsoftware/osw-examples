package m1cs.segments.hcd

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.{Flow, Framing, Sink, Source, Tcp}
import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.util.ByteString
import csw.logging.api.scaladsl.Logger
import m1cs.segments.hcd.SegmentActor.CommandFinished
import m1cs.segments.shared.SegmentId
import m1cs.segments.streams.shared.Command

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object SegmentStreamActor {
  /*
  sealed trait Command //extends akka.actor.NoSerializationVerificationNeeded
  case class Send(commandName: String, args: String, replyTo: ActorRef[SegmentStreamActor.Response]) extends Command
  case class SendWithTime(commandName: String, args: String, time: FiniteDuration, replyTo: ActorRef[SegmentStreamActor.Response]) extends Command
  case class CommandFinished(commandName: String, commandId: Int) extends Command

  sealed trait Response {
    val seqNo: Int
    val segmentId: SegmentId
  }
  case class Started(commandName: String, seqNo: Int, segmentId: SegmentId) extends Response
  case class Processing(commandName: String, seqNo: Int, segmentId: SegmentId) extends Response
  case class Completed(commandName: String, seqNo: Int, segmentId: SegmentId) extends Response
  case class Error(commandName: String, seqNo: Int, segmentId: SegmentId, message: String) extends Response
  case object Unknown extends Command
   */
  private type SeqNo      = Int // JPL Sequence Number
  private type Cmd        = String
  private type WaiterList = SizedList[(SeqNo, ActorRef[SegmentActor.Response], Cmd)]
  // The following is temp hack for testing error
  val ERROR_SEG_ID: Int          = 6
  val ERROR_COMMAND_NAME: String = "ERROR"

  def apply(segmentId: SegmentId, log: Logger, waiterSize: Int = 10): Behavior[SegmentActor.Command] = {
    Behaviors.setup { ctx =>
      val io: SocketClientStream2 = new SocketClientStream2(segmentId.toString, ctx.self)(ctx.system)
      handle(io, new WaiterList(waiterSize), nextCommandId = 1, segmentId, log)
    }
  }

  def getRandomDelay: FiniteDuration = FiniteDuration(Random.between(4, 1000), MILLISECONDS)

  def handle(
      io: SocketClientStream2,
      waiterList: WaiterList,
      nextCommandId: Int,
      segmentId: SegmentId,
      log: Logger
  ): Behavior[SegmentActor.Command] =
    Behaviors.receiveMessage {
      case SegmentActor.Send(commandName, _, replyTo) =>
        val delay      = getRandomDelay
        val simCommand = s"DELAY ${delay.toMillis.toString}"
        log.debug(s"Starting: $segmentId:$commandName:$nextCommandId with delay: $delay.")
        // The following fakes started until sim does it
        if (delay.toMillis > 1000) {
          replyTo ! SegmentActor.Started(commandName, nextCommandId, segmentId)
        }
        // ignoring future for right now
        io.send(nextCommandId, simCommand)
        handle(io, waiterList.append((nextCommandId, replyTo, commandName)), nextCommandId + 1, segmentId, log)

      case SegmentActor.SendWithTime(commandName, _, delay, replyTo) =>
        val simCommand: String = s"DELAY ${delay.toMillis.toString}"
        log.debug(s"Starting: $segmentId:$commandName with delay:$delay")
        // The following fakes started until sim does it
        if (delay.toMillis > 1000) {
          replyTo ! SegmentActor.Started(commandName, nextCommandId, segmentId)
        }
        // ignoring future for right now
        io.send(nextCommandId, simCommand)
        handle(io, waiterList.append((nextCommandId, replyTo, commandName)), nextCommandId + 1, segmentId, log)

      case SegmentActor.CommandFinished(commandName, commandId) =>
        //log.debug(s"Finished: $segmentId:$commandName:$commandId")
        waiterList.query(_._1 == commandId) match {
          case Some(entry) =>
            // Special check to make error
            /*
              if (segmentId.number == ERROR_SEG_ID && entry._3 == ERROR_COMMAND_NAME) {
                val errorMessage = "Fake Error Message"
                val errorCmd = entry._3
                log.error(s"Command: $errorCmd} on segment: $segmentId produced ERROR with message: $errorMessage")
                entry._2 ! SegmentActor.Error(errorCmd, commandId, segmentId, errorMessage)
              }
              else {
                entry._2 ! SegmentActor.Completed(entry._3, commandId, segmentId)
              }
             */
            entry._2 ! SegmentActor.Completed(entry._3, commandId, segmentId)
            handle(io, waiterList.remove(_._1 == commandId), nextCommandId, segmentId, log)
          case None =>
            log.error(s"Did not find an entry for: $commandName and commandId: $commandId")
            Behaviors.same
        }
      case SegmentActor.Unknown =>
        log.error("Received SegmentActor.Unknown")
        Behaviors.unhandled
    }

  /**
   * This is a specialized list that will only keep a maximum number of elements
   *
   * @param max      size of list to retain
   * @param initList the list can be initialized with some values
   * @tparam T the type of elements in the list
   */
  private class SizedList[T](max: Int, initList: List[T] = List.empty) {
    val list: ListBuffer[T] = ListBuffer() ++= initList

    def append(sr: T): SizedList[T] = {
      // If the list is at the maximum, remove 1 and add the new one
      if (list.size == max) {
        list.dropInPlace(1)
      }
      list.append(sr)
      this
    }

    def remove(f: T => Boolean): SizedList[T] = {
      val newList = list.filterNot(f)
      new SizedList(max, newList.toList)
    }

    // Runs the predicate argument against each member of the list and returns first
    def query(f: T => Boolean): Option[T] = list.find(f)

    // Runs the function for every member of the list
    def foreach[U](f: T => U): Unit = list.foreach(f)

    override def toString: String = s"SizedList(${list.toString()})"
  }

}

private[hcd] class SocketClientStream2(name: String, replyTo: ActorRef[SegmentActor.Command], host: String = "127.0.0.1", port: Int = 8888)(
    implicit system: ActorSystem[Nothing]
) {
  implicit val ec: ExecutionContext = system.executionContext
  private val connection            = Tcp()(system.classicSystem).outgoingConnection(host, port)

  // Use a queue to feed commands to the stream
  private val (queue, source) = Source.queue[String](bufferSize = 2, OverflowStrategy.backpressure).preMaterialize()

  // A sink for responses from the server
  private val sink = Sink.foreach[String] { s =>
    val (id, resp) = Command.parse(s)
    //println(s"Sink: received: $id and $resp")
    replyTo ! CommandFinished(resp, id)
  }

  // Used to feed commands to the stream
  private val clientFlow = Flow.fromSinkAndSource(sink, source)

  // Converts the strings to ByteString, quits if "q" received (sending "BYE" to server)
  private val parser: Flow[String, ByteString, NotUsed] =
    Flow[String]
      .takeWhile(_ != "q")
      .concat(Source.single("BYE"))
      .map(elem => ByteString(s"$elem\n"))

  // Commands are assumed to be terminated with "\n" for now
  private val flow = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .map(_.utf8String)
    .via(clientFlow)
    .via(parser)

  private val connectedFlow = connection.join(flow).run()
  connectedFlow.foreach { c =>
    //println(s"XXX local addr: ${c.localAddress}, remote addr: ${c.remoteAddress}")
  }

  /**
   * Sends a message to the server and returns the response
   * @param id a unique id for the command
   * @param msg the command text
   * @return the future response from the server
   */
  def send(id: Int, msg: String): Future[QueueOfferResult] = {
    val cmd = Command.make(id, msg)
    queue.offer(cmd)
    //clientActor.ask(GetResponse(id, _))
  }

  /**
   * Terminates the stream
   */
  def terminate(): Unit = {
    queue.offer("q")
    //  clientActor ! Stop
  }
}
