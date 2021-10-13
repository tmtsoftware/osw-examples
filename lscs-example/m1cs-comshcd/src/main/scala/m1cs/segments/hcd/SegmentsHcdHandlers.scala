package m1cs.segments.hcd

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.commands.{CommandIssue, ControlCommand, Setup}
import csw.params.core.models.Id
import csw.time.core.models.UTCTime
import m1cs.segments.shared.HcdCommands.lscsCommandKey
import m1cs.segments.shared.SegmentCommands.{AllSegments, OneSegment, segmentIdKey, toCommandInfo}
import m1cs.segments.shared.SegmentId.ALL_SECTORS
import m1cs.segments.shared.{SegmentCommands, SegmentId}
import m1cs.segments.hcd

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

  // Set tis during init
  private var segments: Map[SegmentId, ActorRef[SegmentActor.Command]] = _

  override def initialize(): Unit = {
    val segmentNumber = 1 to 10 //SegmentId.MAX_SEGMENT_NUMBER
    log.info(
      s"Initializing comsHcd with ${segmentNumber.max} segments in each sector for a total of ${segmentNumber.max * ALL_SECTORS.size} segments."
    )
    val segList = SegmentId.makeSegments(segmentNumber)
    log.info(s"SegmentIds: $segList")
    // Create a map of SegmentIds to SegmentActors
    val external = ctx.system.settings.config.getBoolean("m1cs.external")
    println(s"Its: $external")
    segments = if (external) {
      println("External")
      segList.map { i => (i, ctx.spawn(hcd.SegmentStreamActor(i, log), i.toString)) }.toMap
    }
    else {
      println("Internal")
      segList.map { i => (i, ctx.spawn(hcd.SegmentActor(i, log), i.toString)) }.toMap
    }
    println(s"XX: $segments")
    Thread.sleep(3000)
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    controlCommand match {
      case s @ Setup(_, commandName, _, _) =>
        if (!SegmentCommands.AllCommands.contains(commandName.name))
          Invalid(runId, CommandIssue.UnsupportedCommandIssue(s"HCD does not support the `$commandName` command."))
        if (!s.exists(lscsCommandKey)) {
          Invalid(runId, CommandIssue.MissingKeyIssue(s"Setup must include the ${lscsCommandKey.keyName} parameter."))
        }
        if (!s.exists(segmentIdKey)) {
          Invalid(runId, CommandIssue.MissingKeyIssue(s"Setup must include the ${segmentIdKey.keyName} parameter."))
        }
        // Verify that if one segment, that the segment is online in the list

        //if (!segments.get(segmentId).isDefined) {
//          Invalid(runId, CommandIssue.ParameterValueOutOfRangeIssue(s"The segmentId: $segmentId is not currently avaiable."))
//        }
        Accepted(runId)
      case _ =>
        Invalid(runId, CommandIssue.UnsupportedCommandIssue(s"HCD docs not accept Observe"))
    }
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    controlCommand match {
      case s @ Setup(_, commandName, _, _) =>
        // We know it's here at this point
        val command = s(lscsCommandKey)
        val info    = toCommandInfo(s, command.head)
        val sendList = info match {
          case AllSegments(_) =>
            segments.values.toList
          case OneSegment(segmentId, _) =>
            List(segments(segmentId))
        }
        //log.info(s"SendList: $sendList")
        val mon1 = ctx.spawnAnonymous(
          hcd.SegComMonitor(
            commandName.name,
            command.head,
            sendList,
            runId,
            (sr: SubmitResponse) => cswCtx.commandResponseManager.updateCommand(sr),
            log,
            10.seconds
          )
        )
        mon1 ! SegComMonitor.Start
        Started(runId)
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
