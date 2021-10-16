package m1cs.segments.hcd

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import csw.logging.api.scaladsl.Logger
import m1cs.segments.hcd
import m1cs.segments.shared.{Sector, SegmentId}

object SegmentManager {

  private type SegmentMap     = Map[SegmentId, ActorRef[SegmentActor.Command]]
  private type SegmentCreator = (ActorContext[SegmentManager.Command], SegmentId, Logger) => ActorRef[SegmentActor.Command]
  val internalCreator: SegmentCreator = (ctx, segId, log) => ctx.spawn(hcd.SegmentActor(segId, log), segId.toString)
  val externalCreator: SegmentCreator = (ctx, segId, log) => ctx.spawn(hcd.SegmentStreamActor(segId, log), segId.toString)
  private val emptyMap                = Map.empty[SegmentId, ActorRef[SegmentActor.Command]]

  def apply(log: Logger, external: Boolean): Behavior[SegmentManager.Command] =
    Behaviors.setup { ctx =>
      handle(ctx, emptyMap, if (external) externalCreator else internalCreator, log)
    }

  private def handle(
      ctx: ActorContext[SegmentManager.Command],
      segments: SegmentMap,
      segmentCreator: SegmentCreator,
      log: Logger
  ): Behavior[SegmentManager.Command] =
    Behaviors.receiveMessage {
      case CreateSegments(range) =>
        val segSet                  = SegmentId.makeSegments(range)
        val newSegments: SegmentMap = segSet.map { (i => (i, segmentCreator(ctx, i, log))) }.toMap
        newSegments.foreach(t => ctx.watchWith(t._2, SegmentIdShutdown(t._1)))
        handle(ctx, segments ++ newSegments, segmentCreator, log)
      case CreateSectorSegments(sector, range) =>
        val segSet                  = SegmentId.makeSectorSegmentIds(sector, range)
        val newSegments: SegmentMap = segSet.map { (i => (i, segmentCreator(ctx, i, log))) }.toMap
        newSegments.foreach(t => ctx.watchWith(t._2, SegmentIdShutdown(t._1)))
        handle(ctx, segments ++ newSegments, segmentCreator, log)
      case SegmentExists(segmentId, replyTo) =>
        replyTo ! segments.isDefinedAt(segmentId)
        Behaviors.same
      case GetSegment(segmentId, replyTo) =>
        segments.get(key = segmentId) match {
          case Some(s) => replyTo ! Segments(List(s))
          case None    => replyTo ! Segments(List.empty[ActorRef[SegmentActor.Command]])
        }
        Behaviors.same
      case GetAllSegments(replyTo) =>
        val result = segments.values.toList
        replyTo ! Segments(result)
        Behaviors.same
      case HowManySegments(replyTo) =>
        replyTo ! segments.size
        Behaviors.same
      case ShutdownOne(segmentId, replyTo) =>
        segments.get(segmentId).foreach(_ ! SegmentActor.ShutdownSegment)
        terminating(ctx, segments, segmentCreator, log, numberToDelete = 1, replyTo)
      case ShutdownAll(replyTo) =>
        // If there are no segments, don't try to shut them down
        if (segments.isEmpty) {
          replyTo ! true
          Behaviors.same
        }
        else {
          segments.values.foreach(_ ! SegmentActor.ShutdownSegment)
          terminating(ctx, segments, segmentCreator, log, segments.size, replyTo)
        }
      case ShutdownSM(replyTo) =>
        ctx.self ! ShutdownAll(replyTo)
        Behaviors.stopped
      case Print =>
        log.info(s"Internal Segments: $segments")
        Behaviors.same
      case _ =>
        Behaviors.unhandled
    }

  private def terminating(
      ctx: ActorContext[SegmentManager.Command],
      segments: SegmentMap,
      segmentCreator: SegmentCreator,
      log: Logger,
      numberToDelete: Int,
      replyTo: ActorRef[Boolean]
  ): Behavior[SegmentManager.Command] =
    Behaviors.receiveMessagePartial {
      case SegmentIdShutdown(segmentId) =>
        //log.debug(s"Received terminated for: $segmentId")
        val newSegments = segments - segmentId
        if (numberToDelete <= 1) {
          log.debug(s"It's done: $numberToDelete Back to handle sending true")
          replyTo ! true
          handle(ctx, newSegments, segmentCreator, log)
        }
        else
          terminating(ctx, newSegments, segmentCreator, log, numberToDelete - 1, replyTo)
      case a =>
        println(s"Received another message in terminated: $a")
        Behaviors.same
    }

  sealed trait Command

  final case class CreateSegments(range: Range) extends Command // All Sectors

  final case class CreateSectorSegments(sector: Sector, range: Range) extends Command

  final case class SegmentExists(segmentId: SegmentId, replyTo: ActorRef[Boolean]) extends Command

  final case class HowManySegments(replyTo: ActorRef[Int]) extends Command

  final case class ShutdownOne(segmentId: SegmentId, replyTo: ActorRef[Boolean]) extends Command

  final case class ShutdownAll(replyTo: ActorRef[Boolean]) extends Command

  final case class GetSegment(segmentId: SegmentId, replyTo: ActorRef[Segments]) extends Command

  final case class GetAllSegments(replyTo: ActorRef[Segments]) extends Command

  final case class ShutdownSM(replyTo: ActorRef[Boolean]) extends Command

  private final case class SegmentIdShutdown(segmentId: SegmentId) extends Command

  final case object Print extends Command

  final case class Segments(segments: List[ActorRef[SegmentActor.Command]])

}
