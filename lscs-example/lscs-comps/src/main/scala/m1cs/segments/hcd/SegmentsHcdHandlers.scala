package m1cs.segments.hcd

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandIssue.UnsupportedCommandIssue
import csw.params.commands.CommandResponse.*
import csw.params.commands.{CommandIssue, ControlCommand, Setup}
import csw.params.core.models.Id
import csw.time.core.models.UTCTime
import m1cs.segments.shared.HcdDirectCommand.*
import m1cs.segments.shared.SegmentCommands.{ALL_SEGMENTS, segmentIdKey}
import m1cs.segments.shared.SegmentId.ALL_SECTORS
import m1cs.segments.hcd
import m1cs.segments.hcd.SegmentManager.Segments
import m1cs.segments.shared.{HcdDirectCommand, HcdShutdown, SegmentId}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Comshcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
class SegmentsHcdHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {
//  import cswCtx._
  implicit val system = ctx.system

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = cswCtx.loggerFactory.getLogger

  private val creator: SegmentManager.SegmentCreator = (s, log) => ctx.spawn(hcd.SegmentActor(s, log), s.toString)

  // Set tis during init
  private var createdSegments: Segments = _
  //#initialize
  override def initialize(): Unit = {
    val maxSegments  = ctx.system.settings.config.getInt("m1cs.segments")
    val segmentRange = 1 to maxSegments //SegmentId.MAX_SEGMENT_NUMBER
    log.info(
      s"Initializing Segments HCD with ${segmentRange.max} segments in each sector for a total of ${segmentRange.max * ALL_SECTORS.size} segments."
    )
    createdSegments = SegmentManager.createSegments(creator, segmentRange, log)
  }
  //#initialize

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    controlCommand match {
      case setup: Setup => handleValidation(runId, setup)
      case observe => Invalid(runId, UnsupportedCommandIssue(s"$observe command not supported."))
    }
  }

  private def handleValidation(runId: Id, setup: Setup): ValidateCommandResponse = {
    setup.commandName match {
      case HcdDirectCommand.lscsDirectCommand =>
        if (!setup.exists(lscsCommandKey))
          Invalid(runId, CommandIssue.MissingKeyIssue(s"Setup must include the ${lscsCommandKey.keyName} parameter."))

        if (!setup.exists(segmentIdKey))
          Invalid(runId, CommandIssue.MissingKeyIssue(s"Setup must include the ${segmentIdKey.keyName} parameter."))

        // Verify that if one segment, that the segment is online in the list
        val segmentIdValue = setup(segmentIdKey).head
        if (segmentIdValue != ALL_SEGMENTS && (createdSegments.segmentExists(SegmentId(segmentIdValue)) == false)) {
           Invalid(runId, CommandIssue.ParameterValueOutOfRangeIssue(s"The segmentId: $segmentIdValue is not currently avaiable."))
        } else {
          Accepted(runId)
        }
      case HcdShutdown.shutdownCommand =>
        Accepted(runId)
      case _ =>
        Invalid(runId, CommandIssue.UnsupportedCommandIssue(s"HCD does not accept Observe"))
    }
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    controlCommand match {
      case setup: Setup => handleSetup(runId, setup)
      case observe => Invalid(runId, UnsupportedCommandIssue(s"$observe command not supported."))
    }
  }


  private def handleSetup(runId: Id, setup: Setup): SubmitResponse = {
    setup.commandName match {
      case HcdDirectCommand.lscsDirectCommand =>
        // We know all these params are present at this point
        val command = setup(lscsCommandKey).head
        val commandName = setup(lscsCommandNameKey).head
        val segmentKeyValue = setup(segmentIdKey).head
        println(s"Command: $command  $commandName  $segmentKeyValue")
        val sendList = if (segmentKeyValue == ALL_SEGMENTS) {
          createdSegments.getAllSegments
        } else {
          createdSegments.getSegment(segmentId = SegmentId(segmentKeyValue))
        }

        //log.info(s"SendList: $sendList")
        val mon1 = ctx.spawnAnonymous(
          hcd.SegComMonitor(
            commandName,
            command,
            sendList.segments,
            runId,
            (sr: SubmitResponse) => cswCtx.commandResponseManager.updateCommand(sr),
            log,
            10.seconds
          )
        )
        // Start the command and return Started
        mon1 ! SegComMonitor.Start
        Started(runId)
      case HcdShutdown.shutdownCommand =>
        createdSegments.shutdownAll
        Completed(runId)
      case _ =>
        Error(runId, "This HCD only accepts Setups")
    }
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}