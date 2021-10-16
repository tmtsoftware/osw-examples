package m1cs.segments.hcd

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Props, SpawnProtocol}
import akka.util.Timeout
import csw.logging.api.scaladsl.Logger
import m1cs.segments.shared.SegmentId
import m1cs.segments.streams.client.SocketClientStream
import m1cs.segments.streams.client.SocketClientStream.SpawnHelper

import scala.concurrent.duration.{DurationInt, FiniteDuration, MILLISECONDS}
import scala.util.Random

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
      handle(io, nextCommandId = 1, segmentId, log)
    }
  }

  def getRandomDelay: FiniteDuration = FiniteDuration(Random.between(10, 500), MILLISECONDS)

  def handle(io: SocketClientStream, nextCommandId: Int, segmentId: SegmentId, log: Logger): Behavior[SegmentActor.Command] =
    Behaviors
      .receive[SegmentActor.Command] { (ctx, m) =>
        import ctx.executionContext

        m match {
          case SegmentActor.Send(commandName, args, replyTo) =>
            val delay = getRandomDelay
            ctx.self ! SegmentActor.SendWithTime(commandName, args, delay, replyTo)
            handle(io, nextCommandId, segmentId, log)

          case SegmentActor.SendWithTime(commandName, _, delay, replyTo) =>
            val simCommand: String = s"DELAY ${delay.toMillis.toString}"
            //log.debug(s"Starting: $segmentId:$commandName with delay:$delay")
            // The following fakes started until sim does it
            // if (delay.toMillis > 1000) {
//              replyTo ! SegmentActor.Started(commandName, nextCommandId, segmentId)
            //          }
            io.send(nextCommandId, simCommand).foreach { x =>
              println(s"Done $segmentId")
              replyTo ! SegmentActor.Completed(commandName, nextCommandId, segmentId)
            }
            handle(io, nextCommandId + 1, segmentId, log)
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
