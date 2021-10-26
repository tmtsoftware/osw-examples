package m1cs.segments.hcd

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import csw.logging.api.scaladsl.Logger
import m1cs.segments.shared.SegmentId
import m1cs.segments.streams.client.SocketClientStream

import scala.concurrent.duration.{DurationInt, FiniteDuration, MILLISECONDS}
import scala.util.{Failure, Random, Success}

object SegmentActor {

  // The following is temp hack for testing error
  val ERROR_SEG_ID: Int                = 6
  val ERROR_COMMAND_NAME: String       = "ERROR"
  val FAKE_ERROR_MESSAGE: String       = "Fake Error Message"
  private implicit val timout: Timeout = Timeout(5.seconds)

  def apply(segmentId: SegmentId, log: Logger): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      val io: SocketClientStream = SocketClientStream(ctx, segmentId.toString)
      handle(io, nextSegmentId = 1, segmentId, log)
    }
  }

  def getRandomDelay: FiniteDuration = FiniteDuration(Random.between(10, 500), MILLISECONDS)

  def handle(io: SocketClientStream, nextSegmentId: Int, segmentId: SegmentId, log: Logger): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, m) =>
      import ctx.executionContext
      m match {
        case Send(commandName, args, replyTo) =>
          ctx.self ! SendWithTime(commandName, args, getRandomDelay, replyTo)
          handle(io, nextSegmentId, segmentId, log)

        case SendWithTime(commandName, _, delay, replyTo) =>
          // The following fakes JPL Started until sim does it
          if (delay.toMillis > 1000)
            replyTo ! Started(commandName, nextSegmentId, segmentId)

          // Right now simulator just does random delays
          val simCommand = s"DELAY ${delay.toMillis.toString}"
          io.send(simCommand).onComplete {
            case Success(value) =>
              // Message from simulator not used at this point
              if (commandName == ERROR_COMMAND_NAME)
                replyTo ! Error(commandName, nextSegmentId, segmentId, FAKE_ERROR_MESSAGE)
              else
                replyTo ! Completed(commandName, nextSegmentId, segmentId)
            case Failure(exception) =>
              log.error(s"Socket send failed: $exception", ex = exception)
              replyTo ! Error(commandName, nextSegmentId, segmentId, "Error received from simulator.")
          }
          handle(io, nextSegmentId + 1, segmentId, log)

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

        case Unknown =>
          log.error("Received SegmentActor.Unknown")
          Behaviors.unhandled

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

  case class Send(commandName: String, args: String, replyTo: ActorRef[SegmentActor.Response]) extends Command
  case class SendWithTime(commandName: String, args: String, time: FiniteDuration, replyTo: ActorRef[SegmentActor.Response])
      extends Command
  case class CommandFinished(commandName: String, commandId: Int)              extends Command
  case class Started(commandName: String, seqNo: Int, segmentId: SegmentId)    extends Response
  case class Processing(commandName: String, seqNo: Int, segmentId: SegmentId) extends Response
  case class Completed(commandName: String, seqNo: Int, segmentId: SegmentId)  extends Response

  case class Error(commandName: String, seqNo: Int, segmentId: SegmentId, message: String) extends Response

  case class ShutdownSegment2(replyTo: ActorRef[Boolean]) extends Command
  case object ShutdownSegment                             extends Command

  case object Unknown extends Command

}
