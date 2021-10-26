package m1cs.segments.hcd

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.hcd
//import m1cs.segments.hcd.SegmentActor.{ERROR_COMMAND_NAME, ERROR_SEG_ID}
import m1cs.segments.shared.{A, SegmentId}
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.duration.*

class SegmentManagerTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike {

  private val testKit = ActorTestKit()

  import frameworkTestKit.*

  LoggingSystemFactory.forTestingOnly()
  private val log = GenericLoggerFactory.getLogger

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  private val testCreator: SegmentManager.SegmentCreator = (s, log) => testKit.spawn(hcd.SegmentActor(s, log), s.toString)

  test("One Segment - one Sector - lib") {
    // Create one segment
    var segments = SegmentManager.createSectorSegments(testCreator, A, 1 to 1, log)
    segments.howManySegments shouldBe 1

    segments = segments.shutdownOne(SegmentId("A1"))
    // Should now be zero
    segments.howManySegments shouldBe 0

    TestProbe[Int]().expectNoMessage(1000.milli)
  }

  test("One Segment - one Sector shutdownAll - lib") {
    // Create one segment
    var segments = SegmentManager.createSectorSegments(testCreator, A, 1 to 1, log)
    segments.howManySegments shouldBe 1

    segments = segments.shutdownAll()
    // Should now be zero
    segments.howManySegments shouldBe 0

    TestProbe[Int]().expectNoMessage(1000.milli)
  }

  test("One Segment - All Sectors") {
    // Should create 1 in each sector
    var segments = SegmentManager.createSegments(testCreator, 1 to 1, log)
    segments.howManySegments shouldBe SegmentId.ALL_SECTORS.size

    segments = segments.shutdownAll()
    segments.howManySegments shouldBe 0

    TestProbe[Int]().expectNoMessage(1000.milli)
  }

  test("One Segment - Add five, Shutdown One") {
    val range    = 1 to 5
    var segments = SegmentManager.createSectorSegments(testCreator, A, range, log)

    segments.howManySegments shouldBe range.max

    segments = segments.shutdownOne(SegmentId(A, 1))
    segments.howManySegments shouldBe range.max - 1

    segments = segments.shutdownAll()
    segments.howManySegments shouldBe 0

    TestProbe[Int]().expectNoMessage(1000.milli)
  }

  test("All Segments - All Sectors - Shutdown") {
    val expectedSize = SegmentId.ALL_SECTORS.size * SegmentId.MAX_SEGMENT_NUMBER

    var segments = SegmentManager.createSegments(testCreator, 1 to SegmentId.MAX_SEGMENT_NUMBER, log)
    segments.howManySegments shouldBe expectedSize

    segments = segments.shutdownAll()
    segments.howManySegments shouldBe 0

    TestProbe[Int]().expectNoMessage(1000.milli)
  }

  test("All Segments - All Sectors - Shutdown - Create Again") {
    val expectedSize = SegmentId.ALL_SECTORS.size * SegmentId.MAX_SEGMENT_NUMBER

    var segments = SegmentManager.createSegments(testCreator, 1 to SegmentId.MAX_SEGMENT_NUMBER, log)
    segments.howManySegments shouldBe expectedSize

    segments = segments.shutdownAll()
    segments.howManySegments shouldBe 0

    // Should be able to create again with no issues
    segments = SegmentManager.createSegments(testCreator, 1 to SegmentId.MAX_SEGMENT_NUMBER, log)
    segments.howManySegments shouldBe expectedSize

    segments = segments.shutdownAll()
    segments.howManySegments shouldBe 0

    TestProbe[Int]().expectNoMessage(1000.milli)
  }

  test("Get A Segment and All Segments") {
    val expectedSize = SegmentId.ALL_SECTORS.size * SegmentId.MAX_SEGMENT_NUMBER

    var segments = SegmentManager.createSegments(testCreator, 1 to SegmentId.MAX_SEGMENT_NUMBER, log)
    segments.howManySegments shouldBe expectedSize

    // Get One Segment
    val seg = segments.getSegment(SegmentId("A3"))
    seg.segments.size shouldBe 1
    seg.segments.head.path.name shouldBe "A3"

    // Get all segments
    val all = segments.getAllSegments
    all.segments.size shouldBe expectedSize

    segments = segments.shutdownAll()
    segments.howManySegments shouldBe 0

    TestProbe[Int]().expectNoMessage(1000.milli)
  }

}
