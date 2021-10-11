package m1cs.segments.assembly

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse.{Accepted, Completed, Error, Invalid, SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandIssue, ControlCommand, Setup}
import csw.params.core.models.Id
import csw.time.core.models.UTCTime

case class TestingHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {
  val log = cswCtx.loggerFactory.getLogger

  def initialize(): Unit = {}

  def validateCommand(runId: Id, cc: ControlCommand): ValidateCommandResponse = Accepted(runId)

  def onSubmit(runId: Id, cc: ControlCommand): SubmitResponse = Completed(runId)

  def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  def onOperationsMode(): Unit = {}

  def onShutdown(): Unit = {}

  def onGoOffline(): Unit = {}

  def onGoOnline(): Unit = {}

  def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

}
