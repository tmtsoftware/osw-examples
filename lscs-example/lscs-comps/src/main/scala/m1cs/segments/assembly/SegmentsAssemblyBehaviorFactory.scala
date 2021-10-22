package m1cs.segments.assembly

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}

class SegmentsAssemblyBehaviorFactory extends ComponentBehaviorFactory {

  override def handlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext): ComponentHandlers =
    new SegmentsAssemblyHandlers(ctx, cswCtx)
}
