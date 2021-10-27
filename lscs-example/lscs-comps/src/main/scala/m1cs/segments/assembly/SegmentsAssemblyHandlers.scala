package m1cs.segments.assembly

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.*
import csw.params.commands.CommandIssue.UnsupportedCommandIssue
import csw.params.commands.CommandResponse.*
import csw.params.commands.{CommandIssue, ControlCommand, Setup}
import csw.params.core.models.Id
import csw.prefix.models.Prefix
import csw.time.core.models.UTCTime
import m1cs.segments.shared.{HcdDirectCommand, HcdShutdown, SegmentCommands}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
 * SegmentsAssemblyHandlers is the TLA for the Segments Assembly
 * It receives Setups the are formatted according to the commands in [[SegmentCommands]]
 */
//noinspection DuplicatedCode
class SegmentsAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) {
  import cswCtx.*

  // These are used by the future calls
  private implicit val system: ActorSystem[Nothing] = ctx.system
  private implicit val ec: ExecutionContextExecutor = ctx.executionContext

  private val log = loggerFactory.getLogger
  // Hard-coding HCD prefix because it is not easily available from code. Would be documented in model files.
  private val hcdPrefix     = Prefix("M1CS.segmentsHCD")
  private val hcdConnection = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))
  private var hcdCS: Option[CommandService] = None // Initially, there is no CommandService for HCD

  // This assembly prefix
  private val assemblyPrefix: Prefix        = cswCtx.componentInfo.prefix
  private var hcdLocation: AkkaLocation     = _
  private var hcdCS: Option[CommandService] = None
  private implicit val timeout: Timeout     = 5.seconds

  override def initialize(): Unit = {
    log.info("Initializing SegmentsAssembly...")
  }

  /**
   * this is overriding tracking events for the SegmentHCD. The Assembly should be started
   * with a Component Configuration file that includes tracking and the info for the Segments HCD.
   * This is done in the test files for reference.
   * When the LocationUpdated event is received, a CommandService is created. When the
   * connection goes down, the CommandService is None
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

  /**
   * This is the validate handler. This should perform all validation needed so that
   * the command can execute, or it should return a validation error.
   * Here we return an error for an Observe or pass to the Setup validation.
   * @param runId command runId
   * @param controlCommand either a Setup or Observe
   * @return
   */
  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    controlCommand match {
      case setup: Setup => handleValidation(runId, setup)
      case observe      => Invalid(runId, UnsupportedCommandIssue(s"$observe command not supported."))
    }
  }

  // All Setup validation is performed here
  private def handleValidation(runId: Id, setup: Setup): ValidateCommandResponse = {
    if (SegmentCommands.ALL_COMMANDS.contains(setup.commandName) || setup.commandName.equals(HcdShutdown.shutdownCommand)) {
      Accepted(runId)
    }
    else {
      Invalid(
        runId,
        CommandIssue.UnsupportedCommandIssue(s"Segment Assembly does not support the `${setup.commandName}` command.")
      )
    }
  }

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
          commandResponseManager.updateCommand(sr.withRunId(runId))
        }
        Started(runId)
      case cmd =>
        log.info(s"Segments Assembly received a command: '$cmd',  runId: $runId, setup: $assemblySetup")

        // This simulates what the Assembly does to send to HCD - has received above Setup
        val hcdSetup: Setup = HcdDirectCommand.toHcdDirectCommand(assemblyPrefix, assemblySetup)
        // Assembly sends the Setup and updates
        submitAndWaitHCD(runId, hcdSetup) map { sr =>
          log.info(s"COMMAND COMPLETED from HCD: $sr")
          commandResponseManager.updateCommand(sr.withRunId(runId))
        }
        Started(runId)
      case c =>
        Error(runId, s"An unknown command was received by the Segment Assembly: $c")
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
        Future(Error(runId, s"The Segment HCD is not currently available: ${hcdConnection.componentId}"))
    }


  // The following were ignored for this demonstration
  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}