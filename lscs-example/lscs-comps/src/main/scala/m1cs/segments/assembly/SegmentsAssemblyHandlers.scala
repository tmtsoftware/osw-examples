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
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Comshcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
//noinspection DuplicatedCode
class SegmentsAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) {
  import cswCtx.*

  implicit val system: ActorSystem[Nothing] = ctx.system
  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  private val log = loggerFactory.getLogger
  // Hard-coding HCD prefix because it is not easily available
  private val hcdPrefix     = Prefix("M1CS.segmentsHCD")
  private val hcdConnection = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))
  // This assembly prefix
  private val assemblyPrefix: Prefix        = cswCtx.componentInfo.prefix
  private var hcdLocation: AkkaLocation     = _
  private var hcdCS: Option[CommandService] = None
  private implicit val timeout: Timeout     = 5.seconds

  override def initialize(): Unit = {
    log.info("Initializing SegmentsAssembly...")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")
    trackingEvent match {
      case LocationUpdated(location) =>
        log.info(s">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>Got Location: $location")
        // Should be safe here since we are tracking only Akka location
        hcdLocation = location.asInstanceOf[AkkaLocation]
        hcdCS = Some(CommandServiceFactory.make(hcdLocation)(ctx.system))
      case LocationRemoved(connection) =>
        if (connection == hcdConnection) {
          hcdCS = None
        }
    }
  }

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    controlCommand match {
      case setup: Setup => handleValidation(runId, setup)
      case observe      => Invalid(runId, UnsupportedCommandIssue(s"$observe command not supported."))
    }
  }

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

  private def handleSetup(runId: Id, assemblySetup: Setup): SubmitResponse = {
    assemblySetup.commandName match {
      case HcdShutdown.shutdownCommand =>
        val hcdSetup = Setup(assemblyPrefix, HcdShutdown.shutdownCommand)

        submitAndWaitHCD(runId, hcdSetup) map { sr =>
          commandResponseManager.updateCommand(sr.withRunId(runId))
        }
        Started(runId)
      case cmd =>
        log.info(s"Segments Assembly received a command: '$cmd',  runId: $runId, setup: $assemblySetup")

        // This simulates what the Assembly does to send to HCD - has received above Setup
        val hcdSetup: Setup = HcdDirectCommand.toHcdDirectCommand(assemblyPrefix, assemblySetup)
        // Assembly creates an HCD setup from
        log.info(s"HCD Setup: $hcdSetup")
        submitAndWaitHCD(runId, hcdSetup) map { sr =>
          log.info(s"COMMAND COMPLETED from HCD: $sr")
          commandResponseManager.updateCommand(sr.withRunId(runId))
        }
        Started(runId)
    }
  }

  private def submitAndWaitHCD(runId: Id, setup: Setup): Future[SubmitResponse] =
    hcdCS match {
      case Some(cs) =>
        cs.submitAndWait(setup)
      case None =>
        Future(Error(runId, s"A needed HCD is not available: ${hcdConnection.componentId}"))
    }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
