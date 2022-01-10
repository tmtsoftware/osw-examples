package m1cs.segments.hcd

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import csw.logging.api.scaladsl.Logger
import m1cs.segments.streams.client.SocketClientStream
import m1cs.segments.segcommands.SegmentId

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Properties, Success}

//#segment-actor
object SegmentActor {

  // The following is temp hack for testing errors
  val ERROR_SEG_ID: Int                = 6
  val ERROR_COMMAND_NAME: String       = "ERROR"
  val FAKE_ERROR_MESSAGE: String       = "Error received from simulator."
  private implicit val timout: Timeout = Timeout(5.seconds)

  def apply(segmentId: SegmentId, log: Logger): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      // Here we check to see if there is a property called simulatorHost
      val simulatorHost          = Properties.propOrElse("simulatorHost", "localhost")
      log.debug(s"Connecting to: $simulatorHost")
      val io: SocketClientStream = SocketClientStream(ctx, segmentId.toString, host = simulatorHost)
      handle(io, segmentId, log)
    }
  }

  private def handle(io: SocketClientStream, segmentId: SegmentId, log: Logger): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, m) =>
      // This is an error sequence number to return when send fails
      val errorSeqNum = -1
      import ctx.executionContext
      m match {
        case Send(commandName, command, replyTo) =>
          log.debug(s"Sending segment $segmentId => $command")
          // This branch is here to test error only. TMT simulator will delay a random time before replying with ERROR
          // For testing, it is possible to send ERROR and TMT simulator will reply with ERROR rather than COMPLETED
          // This will only happen if the error command is sent to error segment
          val simCommand = if (commandName == ERROR_COMMAND_NAME && segmentId.number == ERROR_SEG_ID) {
            s"ERROR $command"
          } else {
            command
          }
          io.send(simCommand).onComplete {
            case Success(m) =>
              // This is the sequence number used by the low level socket code to send the command
              // It is created by the SocketServerStream code
              val seqNum = m.hdr.seqNo
              if (m.cmd.toLowerCase.contains("completed")) {
                replyTo ! Completed(commandName, seqNum, segmentId)
              } else {
                replyTo ! Error(commandName, seqNum, segmentId, "Error received from simulator.")
              }
              log.debug(s"Segment $segmentId: ${m.cmd}")
            case Failure(exception) =>
              // This branch occurs when for instance, the socket send fails
              log.error(s"Socket send failed: $exception", ex = exception)
              replyTo ! Error(commandName, errorSeqNum, segmentId, "Error failure received from simulator.")
          }
          handle(io, segmentId, log)

        case SendWithTime(commandName, _, delay, replyTo) =>
          // If DELAY command is sent, TMT simulator waits for specified time, this is for testing overlaps
          val simCommand = s"DELAY ${delay.toMillis.toString}"
          log.debug(s"Sending segment: $segmentId => $simCommand")

          io.send(simCommand).onComplete {
            case Success(m) =>
              // The value returned from the simulator for DELAY is not used at this point.  That could change.
              // Message from simulator not used at this point - cannot handle ERROR
              val seqNum = m.hdr.seqNo
              log.debug(s"Received: ${m.cmd} $seqNum")
              replyTo ! Completed(commandName, seqNum, segmentId)
            case Failure(exception) =>
              log.error(s"Socket send failed: $exception", ex = exception)
              replyTo ! Error(commandName, errorSeqNum, segmentId, "Error received from simulator.")
          }
          handle(io, segmentId, log)
         //#segment-actor

        case ShutdownSegment2(replyTo) =>
          log.debug(s"Shutting down segment: $segmentId")
          io.terminate()
          replyTo ! true
          Behaviors.stopped

        case ShutdownSegment =>
          // This one just shuts down and stops
          log.debug(s"Shutting down segment: $segmentId")
          io.terminate()
          Behaviors.stopped

        case CommandFinished(_, _) =>
          log.error("Received SegmentActor.CommandFinished")
          Behaviors.unhandled
      }
    }

  sealed trait Command //extends akka.actor.NoSerializationVerificationNeeded

  sealed trait Response {
    val commandName: String
    val seqNo: Int
    val segmentId: SegmentId
  }

  case class Send(commandName: String, command: String, replyTo: ActorRef[SegmentActor.Response]) extends Command
  case class SendWithTime(commandName: String, command: String, time: FiniteDuration, replyTo: ActorRef[SegmentActor.Response])
      extends Command
  case class CommandFinished(commandName: String, commandId: Int)              extends Command
  case class Started(commandName: String, seqNo: Int, segmentId: SegmentId)    extends Response
  case class Processing(commandName: String, seqNo: Int, segmentId: SegmentId) extends Response
  case class Completed(commandName: String, seqNo: Int, segmentId: SegmentId)  extends Response

  case class Error(commandName: String, seqNo: Int, segmentId: SegmentId, message: String) extends Response
  // Currently not in use
  case class ShutdownSegment2(replyTo: ActorRef[Boolean]) extends Command
  case object ShutdownSegment                             extends Command
}
