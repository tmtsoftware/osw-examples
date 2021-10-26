package m1cs.segments.hcd

import akka.actor.typed.ActorRef
import csw.logging.api.scaladsl.Logger
import m1cs.segments.shared.{Sector, SegmentId}

import scala.annotation.tailrec

object SegmentManager {
  final case class SegmentList(segments: List[ActorRef[SegmentActor.Command]])
  private type SegmentMap = Map[SegmentId, ActorRef[SegmentActor.Command]]
  type SegmentCreator     = (SegmentId, Logger) => ActorRef[SegmentActor.Command]

  def createSegments(segmentCreator: SegmentCreator, range: Range, log: Logger): Segments = {
    val segSet = SegmentId.makeSegments(range)
    Segments(segSet.map { s => (s, segmentCreator(s, log)) }.toMap)
  }

  def createSectorSegments(segmentCreator: SegmentCreator, sector: Sector, range: Range, log: Logger): Segments = {
    val segSet = SegmentId.makeSectorSegmentIds(sector, range)
    Segments(segSet.map { s => (s, segmentCreator(s, log)) }.toMap)
  }

  private[hcd] case class Segments(private var segments: SegmentMap) {

    def getSegment(segmentId: SegmentId): SegmentList =
      segments.get(key = segmentId) match {
        case Some(s) => SegmentList(List(s))
        case None    => SegmentList(List.empty[ActorRef[SegmentActor.Command]])
      }

    def getAllSegments: SegmentList = SegmentList(segments.values.toList)

    def segmentExists(segmentId: SegmentId): Boolean = segments.isDefinedAt(segmentId)

    def howManySegments: Int = segments.size

    // Internal method that is used by shutdownOne and shutdownAll -- doesn't use Segments
    private def internalShutdownOne(segmentMap: SegmentMap, segmentId: SegmentId): SegmentMap =
      segmentMap.get(segmentId) match {
        case Some(seg) =>
          seg.tell(SegmentActor.ShutdownSegment)
          segmentMap - segmentId
        case None =>
          segmentMap
      }

    def shutdownOne(segmentId: SegmentId): Segments = Segments(internalShutdownOne(segments, segmentId))

    def shutdownAll(): Segments = {
      @tailrec
      def doOne(segmentsIn: SegmentMap): SegmentMap = {
        if (segmentsIn.isEmpty) segmentsIn
        else doOne(internalShutdownOne(segmentsIn, segmentsIn.head._1))
      }
      // shuts down one at a time using shutdownOne
      Segments(doOne(segments))
    }

    override def toString: String = segments.toString
  }
}
