package m1cs.segments.hcd

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import csw.logging.api.scaladsl.Logger
import csw.params.commands.CommandResponse.{Completed, Error, SubmitResponse}
import csw.params.core.models.Id

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object SegComMonitor {
  private val TIMEOUT_KEY     = "TIMEOUT_KEY"
  private val DEFAULT_TIMEOUT = 30.seconds // This is a guess on how long to wait for all segments

  /**
   * A SegComMonitor executes a command to a set of Segments and waits for the responses.
   * It is an actor and a new one is created for each segment command that is received by the HCD.
   * @param commandName - the command name (i.e. ACTUATOR)
   * @param fullCommand - the fully-formed command text
   * @param segments - the list of SegmentActors to receive the command
   * @param runId - the runId of the request received by the HCD
   * @param replyTo - this is a function that is called to notify with the final SubmitResponse
   * @param log - logger from CSW
   * @param timeout - an optional timeout to wait for all commands to complete before timing out
   * @return The SegComMonitor Behavior
   */
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
      new SegMonitor(commandName, fullCommand, segments, runId, replyTo, log, segmentResponseMapper, timeout).starting()
    }
  }

  //#seg-mon
  /**
   * These are the commands for the Command Sequence Monitor. The WrappedSegmentResponse is needed to receive
   * the SegmentActor.Response and transform it into a SegComMonitor.Command.
   * CommandTimeout is is issued and received if the Command Monitor times out before receiving all segment responses.
   */
  sealed trait Command
  final private case class WrappedSegmentResponse(response: SegmentActor.Response) extends Command
  case object Start                                                                extends Command
  final private case object CommandTimeout                                         extends Command

  /**
   * This private class implements the Segment Command Monitor.
   * The class is an actor with a two state FSM. The monitor is created with a list of segment actors and a
   * segment command that is to be sent to each of the segment actors in the list. It waits for the Start message
   * in the `starting` state. Once Start is received, it sends to command sequentially to each segment actor and
   * waits for the responses in the `waiting` state.
   *
   * It determines completion by counting responses and also checking that the responses are Completed rather than Error.
   * Once the correct number of responses have been received, it executes the replyTo function, which is usually a
   * function to update the CSW CRM, which notifies the caller that the command is completed.
   *
   * There is one more feature.  A timeout can be provided that will allow the command to timeout and reply to the
   * caller with an Error.
   *
   * A new Segment Command Monitor is created for every command executed, after all the responses have been received or
   * the monitor is otherwise completed, the monitor returns Behaviors.stopped, which causes the actor to be ended.
   */
  class SegMonitor private[SegComMonitor] (
      commandName: String,
      fullCommand: String,
      segments: List[ActorRef[SegmentActor.Command]],
      runId: Id,
      replyTo: SubmitResponse => Unit, // This is here so that handler can provide the CRM but tests can do something else
      log: Logger,
      segmentResponseMapper: ActorRef[SegmentActor.Response],
      timeout: FiniteDuration
  ) {
    def starting(): Behavior[Command] =
      Behaviors.receiveMessage {
        case Start =>
          log.debug(s"Sending $commandName to ${segments.size} segments.")
          segments.foreach(_ ! SegmentActor.Send(commandName, fullCommand, segmentResponseMapper))
          waiting(segments.size, responsesReceived = 0)
        case _ =>
          Behaviors.unhandled
      }

    /**
     * The waiting state is entered once the command is sent to the segments. Every time a response from a segment
     * is received, the Behavior calls itself with updated state. This is not a recursive call in Akka.
     * @param totalSegments the number of expected responses
     * @param responsesReceived number of responses received so far
     * @return a typed Behavior
     */
    def waiting(totalSegments: Int, responsesReceived: Int): Behavior[Command] =
      Behaviors.withTimers { timers =>
        Behaviors
          .receiveMessage[Command] {
            case wrapped: WrappedSegmentResponse =>
              wrapped.response match {
                case SegmentActor.Started(commandName, commandId, segmentId) =>
                  timers.startSingleTimer(TIMEOUT_KEY, CommandTimeout, timeout)
                  log.debug(s"Started: $segmentId:$commandName:$commandId with $timeout timeout.")
                  Behaviors.same
                case SegmentActor.Completed(commandName, commandId, segmentId) =>
                  val updatedResponsesReceived = responsesReceived + 1
                  if (totalSegments == updatedResponsesReceived) {
                    log.info(
                      s"$commandName completed successfully for **$updatedResponsesReceived** segments. Sending Completed($runId)"
                    )
                    log.debug(s"Cancelling Timeout Timer")
                    timers.cancel(TIMEOUT_KEY)
                    replyTo(Completed(runId))
                    Behaviors.stopped
                  }
                  else {
                    if (Math.floorMod(responsesReceived, 20) == 0)
                      log.debug(s"Completed: $segmentId:$commandName:$commandId  Total completed: $responsesReceived")
                    waiting(totalSegments, updatedResponsesReceived)
                  }
                case SegmentActor.Processing(commandName, commandId, segmentId) =>
                  log.debug(s"Processing: $segmentId:$commandName:$commandId")
                  Behaviors.same
                case SegmentActor.Error(commandName, commandId, segmentId, message) =>
                  val updatedResponsesReceived = responsesReceived + 1
                  log.error(
                    s"Error: $segmentId:$commandName:$commandId--STOPPING, $updatedResponsesReceived responses received."
                  )
                  // Cancel the timeout timer
                  timers.cancel(TIMEOUT_KEY)
                  replyTo(Error(runId, message))
                  Behaviors.stopped
              }
            case Start =>
              Behaviors.unhandled
            case CommandTimeout =>
              replyTo(
                Error(
                  runId,
                  s"A segment command timed out after receiving: $responsesReceived responses of expected: $totalSegments."
                )
              )
              Behaviors.stopped
            case other =>
              log.error(s"SegComMonitor received some other message.  Just FYI: $other")
              Behaviors.same
          }
          .receiveSignal { case (_, PostStop) =>
            log.debug(s">>>SegComMonitor for $runId STOPPED<<<")
            Behaviors.same
          }
      }
  }
  //#seg-mon
}
