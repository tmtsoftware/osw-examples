package m1cs.segments.assembly

import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models._
import csw.params.commands.CommandResponse._
import csw.params.commands.{ControlCommand, Setup}
import csw.params.core.models.Id
import csw.prefix.models.Prefix
import csw.time.core.models.UTCTime
import m1cs.segments.shared.HcdCommands.{toAllSegments, toOneSegment}
import m1cs.segments.shared.SegmentCommands.ACTUATOR.toActuatorCommand
import m1cs.segments.shared.SegmentCommands.{AllSegments, OneSegment}

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
class SegmentsAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) {
  import cswCtx._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  // Hard-coding HCD prefix because it is not easily available
  private val hcdPrefix     = Prefix("M1CS.segmentsHCD")
  private val hcdConnection = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))
  // This assembly prefix
  private val prefix: Prefix                = cswCtx.componentInfo.prefix
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
    Accepted(runId)
  }

  override def onSubmit(runId: Id, cc: ControlCommand): SubmitResponse = cc match {
    case setup: Setup =>
      log.info(s"Segments Assembly received a command:  $runId and $setup")
      val command = toActuatorCommand(setup)
      val hcdSetup = command match {
        case AllSegments(command) =>
          toAllSegments(prefix, command)
        case OneSegment(segmentId, command) =>
          toOneSegment(prefix, segmentId, command)
      }
      simpleHCD(runId, hcdSetup) map { sr =>
        log.info(s"SCOMMAND COMPLETED from HCD: $sr")
        commandResponseManager.updateCommand(sr.withRunId(runId))
      }
      Started(runId)
    case _ =>
      log.error("What")
      Completed(runId)
  }

  private def simpleHCD(runId: Id, setup: Setup): Future[SubmitResponse] =
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
