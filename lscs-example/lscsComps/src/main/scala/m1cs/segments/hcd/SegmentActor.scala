package m1cs.segments.hcd

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import csw.logging.api.scaladsl.Logger
import m1cs.segments.streams.client.SocketClientStream
import m1cs.segments.segcommands.SegmentId
import m1cs.segments.streams.shared.SocketMessage.MessageId

import scala.concurrent.duration.{DurationInt, FiniteDuration, MILLISECONDS}
import scala.util.{Failure, Properties, Random, Success}

//#segment-actor
object SegmentActor {

  // The following is temp hack for testing errors
  val ERROR_SEG_ID: Int                = 6
  val ERROR_COMMAND_NAME: String       = "ERROR"
  val FAKE_ERROR_MESSAGE: String       = "Fake Error Message"
  private implicit val timout: Timeout = Timeout(5.seconds)

  def apply(segmentId: SegmentId, log: Logger): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      // Here we check to see if there is a property called simulatorHost
      val simulatorHost          = Properties.propOrElse("simulatorHost", "localhost")
      val io: SocketClientStream = SocketClientStream(ctx, segmentId.toString, host = simulatorHost)
      handle(io, seqNo = 1, segmentId, log)
    }
  }

  private def handle(io: SocketClientStream, seqNo: Int, segmentId: SegmentId, log: Logger): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, m) =>
      import ctx.executionContext
      m match {
        case Send(commandName, command, replyTo) =>
          log.debug(s"Sending commandName: $commandName and command: $command to simulator.")
          // This branch sends the JPL command directly, TMT simulator will delay a random time before replying
          // For testing, it is possible to send ERROR and TMT simulator will reply with ERROR rather than COMPLETED
          val simCommand = if (commandName == ERROR_COMMAND_NAME) {
            s"ERROR ${command}"
          } else {
            command
          }
          io.send(simCommand, MessageId(seqNo)).onComplete {
            case Success(m) =>
              if (m.cmd.toUpperCase.contains("COMPLETED")) {
                log.debug(s"Message seqNo: ${m.hdr.seqNo}")
                replyTo ! Completed(commandName, seqNo, segmentId)
              } else {
                replyTo ! Error(commandName, seqNo, segmentId, "Error received from simulator.")
              }
              log.debug(s"Command completed with: ${m.cmd}")
            case Failure(exception) =>
              log.error(s"Socket send failed: $exception", ex = exception)
              replyTo ! Error(commandName, seqNo, segmentId, "Error received from simulator.")
          }
          handle(io, seqNo + 1, segmentId, log)

        case SendWithTime(commandName, _, delay, replyTo) =>
          // If DELAY command is sent, TMT simulator waits for specified time, this is for testing overlaps
          val simCommand = s"DELAY ${delay.toMillis.toString}"
          log.debug(s"Sending command: $simCommand to simulator.")

          io.send(simCommand).onComplete {
            case Success(m) =>
              // The value returned from the simulator for DELAY is not used at this point.  That could change.
              // Message from simulator not used at this point - cannot handle ERROR
              log.debug(s"Received: ${m.cmd}")
              replyTo ! Completed(commandName, seqNo, segmentId)
            case Failure(exception) =>
              log.error(s"Socket send failed: $exception", ex = exception)
              replyTo ! Error(commandName, seqNo, segmentId, "Error received from simulator.")
          }
          handle(io, seqNo + 1, segmentId, log)
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
