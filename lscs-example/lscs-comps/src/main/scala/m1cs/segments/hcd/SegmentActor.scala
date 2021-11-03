package m1cs.segments.hcd

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import csw.logging.api.scaladsl.Logger
import m1cs.segments.streams.client.SocketClientStream
import m1cs.segments.segcommands.SegmentId

import scala.concurrent.duration.{DurationInt, FiniteDuration, MILLISECONDS}
import scala.util.{Failure, Random, Success}

//#segment-actor
object SegmentActor {

  // The following is temp hack for testing errors
  val ERROR_SEG_ID: Int                = 6
  val ERROR_COMMAND_NAME: String       = "ERROR"
  val FAKE_ERROR_MESSAGE: String       = "Fake Error Message"
  private implicit val timout: Timeout = Timeout(5.seconds)

  def apply(segmentId: SegmentId, log: Logger): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      val hostName  = ctx.system.settings.config.getString("m1cs.simulatorHost")
println(s"HostName: $hostName")
      val io: SocketClientStream = SocketClientStream(ctx, segmentId.toString, host = hostName)
      handle(io, seqNo = 1, segmentId, log)
    }
  }

  private def getRandomDelay: FiniteDuration = FiniteDuration(Random.between(10, 1250), MILLISECONDS)

  private def handle(io: SocketClientStream, seqNo: Int, segmentId: SegmentId, log: Logger): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, m) =>
      import ctx.executionContext
      m match {
        case Send(commandName, args, replyTo) =>
          ctx.self ! SendWithTime(commandName, args, getRandomDelay, replyTo)
          handle(io, seqNo, segmentId, log)

        case SendWithTime(commandName, _, delay, replyTo) =>
          // The following fakes JPL Started until sim does it
          if (delay.toMillis > 1000)
            replyTo ! Started(commandName, seqNo, segmentId)

          // Right now simulator just does random delays
          val simCommand = s"DELAY ${delay.toMillis.toString}"
          io.send(simCommand).onComplete {
            case Success(_) =>
              // The value returned from the simulator is not used at this point.  That could change.
              // Message from simulator not used at this point
              if (commandName == ERROR_COMMAND_NAME)
                replyTo ! Error(commandName, seqNo, segmentId, FAKE_ERROR_MESSAGE)
              else
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

  case class Send(commandName: String, args: String, replyTo: ActorRef[SegmentActor.Response]) extends Command
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
