package m1cs.segments.hcd

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import csw.logging.api.scaladsl.Logger
import csw.params.commands.CommandResponse.{Completed, Error, SubmitResponse}
import csw.params.core.models.Id

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object SegComMonitor {
  private val TIMEOUT_KEY     = "TIMEOUT_KEY"
  private val DEFAULT_TIMEOUT = 30.seconds

  def apply(
      commandName: String,
      fullCommand: String,
      segments: List[ActorRef[SegmentActor.Command]],
      runId: Id,
      replyTo: SubmitResponse => Unit, // This is here so that handler can provide the CRM but tests can do something else
      log: Logger,
      timeout: FiniteDuration = DEFAULT_TIMEOUT
  ): Behavior[Command] = {
    Behaviors.setup { ctx =>
      val segmentResponseMapper: ActorRef[SegmentActor.Response] = ctx.messageAdapter(rsp => WrappedSegmentResponse(rsp))
      log.info(s"Sending $commandName to ${segments.size} segments.")

      def starting(): Behavior[Command] =
        Behaviors.receiveMessage {
          case Start =>
            segments.foreach(_ ! SegmentActor.Send(commandName, fullCommand, segmentResponseMapper))
            waiting(segments.size, responsesReceived = 0)
          case _ =>
            Behaviors.unhandled
        }

      def waiting(totalSegments: Int, responsesReceived: Int): Behavior[Command] = {
        log.debug(s"XXX waiting: totalSegments=$totalSegments, responsesReceived=$responsesReceived")
        // This if is only executed if all segments return Completed, if there is an error
        // it will terminate early and return the first error
        Behaviors.withTimers { xx =>
          Behaviors
            .receiveMessage[Command] {
              case wrapped: WrappedSegmentResponse =>
                wrapped.response match {
                  case SegmentActor.Started(commandName, commandId, segmentId) =>
                    //timers.startSingleTimer(TIMEOUT_KEY, CommandTimeout, timeout)
                    //log.debug(s"Started: $segmentId:$commandName:$commandId with $timeout timeout.")
                    Behaviors.same
                  case SegmentActor.Completed(commandName, commandId, segmentId) =>
                    val updatedResponsesReceived = responsesReceived + 1
                    if (totalSegments == updatedResponsesReceived) {
                      log.info(
                        s"$commandName completed successfully for $updatedResponsesReceived segments. Sending Completed($runId)"
                      )
                      //log.debug(s"Cancelling Timeout Timer")
                      //timers.cancel(TIMEOUT_KEY)
                      replyTo(Completed(runId))
                      Behaviors.stopped
                    }
                    else {
                      //  log.debug(s"Recieved: $segmentId")
                      //if (Math.floorMod(responsesReceived, 20) == 0)
//                        log.debug(s"Completed: $segmentId:$commandName:$commandId  Total completed: $responsesReceived")
                      waiting(totalSegments, updatedResponsesReceived)
                    }
                  case SegmentActor.Processing(commandName, commandId, segmentId) =>
                    log.debug(s"Processing: $segmentId:$commandName:$commandId")
                    Behaviors.same
                  case SegmentActor.Error(commandName, commandId, segmentId, message) =>
                    log.error(s">>>>>>>>>>>>>>>>>>>>>>>>>>>>Error received: $commandName, $commandId, $segmentId, $message")
                    val updatedResponsesReceived = responsesReceived + 1
                    log.info(
                      s"Error: $segmentId:$commandName:$commandId--STOPPING, $updatedResponsesReceived responses received."
                    )
                    log.debug(s"Cancelling Timeout Timer")
                    //timers.cancel(TIMEOUT_KEY)
                    replyTo(Error(runId, message))
                    Behaviors.stopped

                }
              case Start =>
                Behaviors.unhandled
              case CommandTimeout =>
                replyTo(
                  Error(
                    runId,
                    s"Segment command timed out after received: $responsesReceived responses of expected: $totalSegments."
                  )
                )
                Behaviors.stopped
              case a =>
                log.error(s"Received some other message: $a")
                Behaviors.same
            }
            .receiveSignal { case (_, PostStop) =>
              log.debug(s">>>SegComMonitor for $runId STOPPED<<<")
              Behaviors.same
            }
        }
      }

      starting()

    }
  }

  sealed trait Command
  final private case class WrappedSegmentResponse(response: SegmentActor.Response) extends Command
  case object Start                                                                extends Command
  final private case object CommandTimeout                                         extends Command

}
