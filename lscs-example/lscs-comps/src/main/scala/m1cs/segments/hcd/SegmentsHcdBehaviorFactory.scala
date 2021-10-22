package m1cs.segments.hcd

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}

class SegmentsHcdBehaviorFactory extends ComponentBehaviorFactory {

  override def handlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext): ComponentHandlers =
    new SegmentsHcdHandlers(ctx, cswCtx)

}
