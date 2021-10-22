package m1cs.segments.shared

import scala.concurrent.{ExecutionContext, Future}

// Allowable sectors
sealed trait Sector
case object A extends Sector
case object B extends Sector
case object C extends Sector
case object D extends Sector
case object E extends Sector
case object F extends Sector

object Sector {
  def apply(input: Char): Sector = input.toUpper match {
    case 'A' => A
    case 'B' => B
    case 'C' => C
    case 'D' => D
    case 'E' => E
    case 'F' => F
    case _   => throw new IllegalArgumentException("Sector values can only be A to F.")
  }
}

case class SegmentId(sector: Sector, number: Int) {
  import SegmentId._

  checkSegmentNumbers(number)

  override def toString = s"$sector$number"
}

object SegmentId {
  val MIN_SEGMENT_NUMBER = 1  // Segments go from 1 to 82
  val MAX_SEGMENT_NUMBER = 82 //

  val MIN_SECTOR = 'A'
  val MAX_SECTOR = 'F'

  val ALL_SECTORS                = Set(A, B, C, D, E, F)
  val ALL_SEGMENT_NUMBERS: Range = MIN_SEGMENT_NUMBER to MAX_SEGMENT_NUMBER

  private[segments] def checkSegmentNumbers(number: Int): Boolean = {
    if (number < MIN_SEGMENT_NUMBER || number > MAX_SEGMENT_NUMBER)
      throw new IllegalArgumentException(s"A Segment Number must be $MIN_SEGMENT_NUMBER to $MAX_SEGMENT_NUMBER inclusive.")
    else true
  }

  def apply(in: String): SegmentId = {
    require(
      in.length == 2 || in.length == 3,
      s"SectorId must start with a sector (e.g. A) followed by number from $MIN_SEGMENT_NUMBER to $MAX_SEGMENT_NUMBER"
    )

    // Sector must be char 0
    val sector: Sector = Sector(in(0))

    // Number 1-MAX
    val numString = in.stripTrailing().substring(1)
    val segNum    = numString.toInt
    checkSegmentNumbers(segNum)
    SegmentId(sector, segNum)
  }

  def makeSegments(range: Range): Set[SegmentId] = {
    for {
      s    <- SegmentId.ALL_SECTORS
      segs <- makeSectorSegmentIds(s, range)
    } yield segs
  }

  def sectorMap[A](sector: Sector, range: Range, f: (SegmentId) => A)(implicit
      ec: ExecutionContext
  ): Future[List[(SegmentId, A)]] = {
    Future {
      range.map { i =>
        val segmentId = SegmentId(sector, i)
        (segmentId, f(segmentId))
      }.toList
    }
  }

  def makeSectorSegmentIds(sector: Sector, range: Range): Set[SegmentId] = {
    val res = for {
      i <- range
    } yield SegmentId(sector, i)
    res.toSet
  }
}

case class SegmentRange(sector: Sector, numbers: Range) {
  import SegmentId._

  require(numbers.start >= MIN_SEGMENT_NUMBER, s"Minimum Segment Number of ${numbers.start} must be >= $MIN_SEGMENT_NUMBER.")
  require(numbers.end <= MAX_SEGMENT_NUMBER, s"Maximum Segment Number must be <= $MAX_SEGMENT_NUMBER")

  def size: Int = numbers.size

  def min: Int = numbers.min

  def max: Int = numbers.max

  def toSegmentIds: Vector[SegmentId] = numbers.map((sn: Int) => SegmentId(sector, sn)).toVector

  def map[B](f: SegmentId => B): IndexedSeq[B] =
    for {
      x <- numbers
    } yield f(SegmentId(sector, x))

  override def toString = s"$sector[${numbers.start}-${numbers.end}]"
}

object SegmentRange {
  import SegmentId._

  def apply(in: String): SegmentRange = {

    require(in.length == 5 || in.length == 8, s"Segment Range must start with a sector (e.g. A) followed by (min-max).")

    // Sector must be char 0
    val sector: Sector = Sector(in(0))

    val pattern                       = "\\[([1-9]+)-([1-9]+)\\]".r
    val pattern(minString, maxString) = in.substring(1)

    // Should be okay to convert to Ints at this point
    val min = minString.toInt
    val max = maxString.toInt
    require(checkSegmentNumbers(min))
    require(checkSegmentNumbers(max))
    SegmentRange(sector, min to max)
  }
}
