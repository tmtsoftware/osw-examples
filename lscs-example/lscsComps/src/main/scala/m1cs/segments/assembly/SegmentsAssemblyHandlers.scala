package m1cs.segments.assembly

import akka.actor.typed.scaladsl.ActorContext
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{AkkaLocation, ComponentId, ComponentType, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.params.commands.CommandIssue.UnsupportedCommandIssue
import csw.params.commands.CommandResponse
import csw.params.commands.CommandResponse.{Accepted, Invalid, Started, SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandIssue, ControlCommand, Setup}
import csw.params.core.models.Id
import csw.prefix.models.Prefix
import csw.time.core.models.UTCTime
import m1cs.segments.segcommands.Common
import m1cs.segments.shared.{HcdDirectCommand, HcdShutdown}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
 * SegmentsAssemblyHandlers is the TLA for the Segments Assembly
 * It receives Setups the are formatted according to the commands in m1cs.segments.segcommands package.
 */
//noinspection DuplicatedCode
class SegmentsAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) {

  // These are used by the future calls
  private implicit val ec: ExecutionContextExecutor = ctx.executionContext
  // Required to get logging going
  private val log = cswCtx.loggerFactory.getLogger

  // Require that we have a connection to track and a Prefix
  require(
    cswCtx.componentInfo.getConnections.size() > 0,
    "The Assembly Component Configuration File must have a tracking connection to the Segments HCD."
  )
  private val hcdPrefix                     = Prefix(cswCtx.componentInfo.getConnections.get(0).prefix.toString())
  private val hcdConnection                 = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))
  private var hcdCS: Option[CommandService] = None // Initially, there is no CommandService for HCD

  // This assembly prefix
  private val assemblyPrefix: Prefix = cswCtx.componentInfo.prefix

  override def initialize(): Unit = {
    log.info("Initializing SegmentsAssembly...")
  }

  //#tracking-events
  /**
   * This is overriding tracking events to gain events for Segments HCD. The Assembly should be started
   * with a Component Configuration file that includes tracking and the info for the Segments HCD.
   * This is done in the test files for reference.
   * When the LocationUpdated event is received, a CommandService is created. When the
   * connection goes down, the CommandService is set to None. When None an error is issued in onSubmit.
   * @param trackingEvent CSW TrackingEvent.
   */
  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")
    trackingEvent match {
      case LocationUpdated(location) =>
        log.debug(s"Assembly received HCD location: $location")
        // Should be safe here since we are tracking only Akka location
        val hcdLocation = location.asInstanceOf[AkkaLocation]
        hcdCS = Some(CommandServiceFactory.make(hcdLocation)(ctx.system))
      case LocationRemoved(connection) =>
        if (connection == hcdConnection) {
          hcdCS = None
        }
    }
  }
  //#tracking-events

  //#handle-validation
  /**
   * This is the validate handler. This should perform all validation needed so that
   * the command can execute, or it should return a validation error.
   * Here we return an error for an Observe or pass to the Setup validation.
   * @param runId command runId
   * @param controlCommand either a Setup or Observe
   * @return a [ValidateCommandResponse]
   */
  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    controlCommand match {
      case setup: Setup => handleValidation(runId, setup)
      case observe      => Invalid(runId, UnsupportedCommandIssue(s"$observe command not supported."))
    }
  }

  // All Setup validation is performed here
  private def handleValidation(runId: Id, setup: Setup): ValidateCommandResponse = {
    if (Common.ALL_COMMANDS.contains(setup.commandName) || setup.commandName.equals(HcdShutdown.shutdownCommand)) {
      Accepted(runId)
    }
    else {
      Invalid(
        runId,
        CommandIssue.UnsupportedCommandIssue(s"Segment Assembly does not support the `${setup.commandName}` command.")
      )
    }
  }
  //#handle-validation

  //#important-code
  /**
   * The Assembly receives a Setup command with the name of the low-level command.
   * It transforms it into an HCD command, which is just the String command to all or one segment.
   * Ranges aren't yet supported.
   */
  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    controlCommand match {
      case setup: Setup => handleSetup(runId, setup)
      case observe      => Invalid(runId, UnsupportedCommandIssue(s"$observe command not supported."))
    }
  }

  /**
   * Processes commands as Setups
   * @param runId command runId
   * @param assemblySetup the [[Setup]] to execute
   * @return [[SubmitResponse]] response from the command. All commands are started currently.
   */
  private def handleSetup(runId: Id, assemblySetup: Setup): SubmitResponse = {
    assemblySetup.commandName match {
      case HcdShutdown.shutdownCommand =>
        log.debug(s"Segments Assembly received shutdown request: $runId and $assemblySetup")
        val hcdSetup = Setup(assemblyPrefix, HcdShutdown.shutdownCommand)

        submitAndWaitHCD(runId, hcdSetup) map { sr =>
          cswCtx.commandResponseManager.updateCommand(sr.withRunId(runId))
        }
        Started(runId)
      case cmd =>
        log.info(s"Segments Assembly received a command: '$cmd',  runId: $runId, setup: $assemblySetup")

        // This simulates what the Assembly does to send to HCD - has received above Setup
        try {
          val hcdSetup: Setup = HcdDirectCommand.toHcdDirectCommand(assemblyPrefix, assemblySetup)
          // Assembly sends the Setup and updates
          submitAndWaitHCD(runId, hcdSetup) map { sr =>
            log.info(s"Assembly command completed from HCD: $sr")
            cswCtx.commandResponseManager.updateCommand(sr.withRunId(runId))
          }
          Started(runId)
        }
        catch {
          case _: Exception =>
            CommandResponse.Error(runId, s"An exception was thrown while processing setup: ${assemblySetup.commandName}")
        }
    }
  }

  /**
   * This is a convenience routine to check the availability of HCD prior to sending
   * @param runId command runId
   * @param setup the Setup to send
   * @return command response as a SubmitResponse
   */
  private def submitAndWaitHCD(runId: Id, setup: Setup): Future[SubmitResponse] =
    hcdCS match {
      case Some(cs) =>
        // Added a delay here because segment commands take an unknown amount of time.
        // Can be made an implicit for all calls in file for a more complex situation with different timeouts.
        cs.submitAndWait(setup)(timeout = 15.seconds)
      case None =>
        Future(CommandResponse.Error(runId, s"The Segment HCD is not currently available: ${hcdConnection.componentId}"))
    }
  //#important-code
  // The following were ignored for this demonstration
  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}
}
