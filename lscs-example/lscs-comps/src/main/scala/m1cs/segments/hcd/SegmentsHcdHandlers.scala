package m1cs.segments.hcd

import akka.actor.typed.ActorSystem
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
 * This is the top level actor for the Segments HCD.
 */
//noinspection DuplicatedCode
class SegmentsHcdHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {
  private implicit val system:ActorSystem[Nothing] = ctx.system

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = cswCtx.loggerFactory.getLogger

  // This is a creator for segments. It is done this way to make testing easier since in testing environment
  // there is a different ActorContext
  private val creator: SegmentManager.SegmentCreator = (s, log) => ctx.spawn(hcd.SegmentActor(s, log), s.toString)

  // Set this during initialization to be a Segments instance
  private var createdSegments: Segments = _

  //#initialize
  /**
   * The TLA initialize reads the number of segments from the reference.conf file.  This is convenient for
   * testing. It creates that number of segments in each sector.  At some point this could be removed and
   * replaced with MAX_SEGMENT_NUMBER.
   */
  override def initialize(): Unit = {
    val maxSegments  = ctx.system.settings.config.getInt("m1cs.segments")
    val segmentRange = 1 to maxSegments //SegmentId.MAX_SEGMENT_NUMBER
    log.info(
      s"Initializing Segments HCD with ${segmentRange.max} segments in each sector for a total of ${segmentRange.max * ALL_SECTORS.size} segments."
    )
    createdSegments = SegmentManager.createSegments(creator, segmentRange, log)
  }
  //#initialize


  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    controlCommand match {
      case setup: Setup => handleValidation(runId, setup)
      case observe      => Invalid(runId, UnsupportedCommandIssue(s"$observe command not supported."))
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
        if (segmentIdValue != ALL_SEGMENTS && !createdSegments.segmentExists(SegmentId(segmentIdValue))) {
          Invalid(
            runId,
            CommandIssue.ParameterValueOutOfRangeIssue(s"The segmentId: $segmentIdValue is not currently available.")
          )
        }
        else {
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
      case observe      => Invalid(runId, UnsupportedCommandIssue(s"$observe command not supported."))
    }
  }

  private def handleSetup(runId: Id, setup: Setup): SubmitResponse = {
    setup.commandName match {
      case HcdDirectCommand.lscsDirectCommand =>
        // We know all these params are present at this point
        val command         = setup(lscsCommandKey).head
        val commandName     = setup(lscsCommandNameKey).head
        val segmentKeyValue = setup(segmentIdKey).head
        // The sendList, at this point, is either one segment or all segments.  The same execution approach
        // is used regardless of one or all
        val sendList = if (segmentKeyValue == ALL_SEGMENTS) {
          createdSegments.getAllSegments
        }
        else {
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
            log
          )
        )
        // Start the command and return Started
        mon1 ! SegComMonitor.Start
        Started(runId)
      case HcdShutdown.shutdownCommand =>
        createdSegments.shutdownAll()
        Completed(runId)
      case _ =>
        Error(runId, "This HCD only accepts Setups")
    }
  }

  // The following were ignored for this demonstration
  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}