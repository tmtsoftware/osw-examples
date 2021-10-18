package m1cs.segments.hcd

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PostStop}
import akka.util.Timeout
import csw.logging.api.scaladsl.Logger
import m1cs.segments.shared.SegmentId
import m1cs.segments.streams.client.SocketClientStream

import scala.concurrent.duration.{DurationInt, FiniteDuration, MILLISECONDS}
import scala.util.{Failure, Random, Success}

object SegmentStreamActor {

  private type SeqNo = Int // JPL Sequence Number
  private type Cmd   = String
  // The following is temp hack for testing error
  val ERROR_SEG_ID: Int          = 6
  val ERROR_COMMAND_NAME: String = "ERROR"
  implicit val timout: Timeout   = Timeout(5.seconds)

  def apply(segmentId: SegmentId, log: Logger, waiterSize: Int = 10): Behavior[SegmentActor.Command] = {
    Behaviors.setup[SegmentActor.Command] { ctx =>
      val io: SocketClientStream = SocketClientStream(ctx, segmentId.toString)
      handle(io, nextSegmentId = 1, segmentId, log)
    }
  }

  def getRandomDelay: FiniteDuration = FiniteDuration(Random.between(10, 500), MILLISECONDS)

  def handle(io: SocketClientStream, nextSegmentId: Int, segmentId: SegmentId, log: Logger): Behavior[SegmentActor.Command] =
    Behaviors
      .receive[SegmentActor.Command] { (ctx, m) =>
        import ctx.executionContext

        m match {
          case SegmentActor.Send(commandName, args, replyTo) =>
            val delay = getRandomDelay
            ctx.self ! SegmentActor.SendWithTime(commandName, args, delay, replyTo)
            handle(io, nextSegmentId, segmentId, log)

          case SegmentActor.SendWithTime(commandName, _, delay, replyTo) =>
            val simCommand: String = s"DELAY ${delay.toMillis.toString}"
            //log.debug(s"Starting: $segmentId:$commandName with delay:$delay")
            // The following fakes started until sim does it
            if (delay.toMillis > 1000) {
              replyTo ! SegmentActor.Started(commandName, nextSegmentId, segmentId)
            }
            io.send(simCommand).map { _ =>
              println(s"Done $segmentId")
              if (commandName == ERROR_COMMAND_NAME)
                replyTo ! SegmentActor.Error(commandName, nextSegmentId, segmentId, "XXX test error")
              else
                replyTo ! SegmentActor.Completed(commandName, nextSegmentId, segmentId)
            }.onComplete {
              case Success(value) =>
              case Failure(exception) => log.error(s"Socket send failed: $exception", ex = exception)
            }
            handle(io, nextSegmentId + 1, segmentId, log)
          case SegmentActor.ShutdownSegment =>
            // IO closed on poststop
            Behaviors.stopped
          case SegmentActor.Unknown =>
            log.error("Received SegmentActor.Unknown")
            Behaviors.unhandled
        }
      }
      .receiveSignal { case (_, PostStop) =>
        io.terminate()
        //log.debug(s">>>SegmentStreamActor terminated stream >>>STOPPED<<<")
        Behaviors.same
      }

}
