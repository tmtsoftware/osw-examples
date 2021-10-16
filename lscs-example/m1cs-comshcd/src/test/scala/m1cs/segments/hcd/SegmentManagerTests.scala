package m1cs.segments.hcd

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.AskPattern.*
import akka.util.Timeout
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.hcd
//import m1cs.segments.hcd.SegmentActor.{ERROR_COMMAND_NAME, ERROR_SEG_ID}
import m1cs.segments.hcd.SegmentManager.Print
import m1cs.segments.shared.{A, SegmentId}
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration.*

class SegmentManagerTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike {

  private val testKit = ActorTestKit()

  import frameworkTestKit.*

  private val logSystem = LoggingSystemFactory.forTestingOnly()
  private val log       = GenericLoggerFactory.getLogger

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
//    Await.ready(logSystem.stop, 2.seconds)
  }

  test("One Segment - one Sector") {
    val sm = testKit.spawn(hcd.SegmentManager(log, external = false), "sm")

    val boolResponse = TestProbe[Boolean]()
    val intResponse  = TestProbe[Int]()

    // Create one segment
    sm ! SegmentManager.CreateSectorSegments(A, 1 to 1)

    // Verify
    sm ! SegmentManager.HowManySegments(intResponse.ref)
    // Finished from short command
    intResponse.expectMessage(1)

    // Now shutdown with all
    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(0)

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    testKit.stop(sm, 5.seconds)
  }

  test("Shutdown with no actors") {
    val sm = testKit.spawn(hcd.SegmentManager(log, external = false), "sm")

    val boolResponse = TestProbe[Boolean]()

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    testKit.stop(sm, 5.seconds)
  }

  test("One Segment - All Sectors") {
    val sm = testKit.spawn(hcd.SegmentManager(log, external = false), "sm")

    val boolResponse = TestProbe[Boolean]()
    val intResponse  = TestProbe[Int]()

    // Should create 1 in each sector
    sm ! SegmentManager.CreateSegments(1 to 1)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(SegmentId.ALL_SECTORS.size)

    sm ! Print

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(0)

    testKit.stop(sm, 5.seconds)
  }

  test("One Segment - Add five, Shutdown One") {
    val sm = testKit.spawn(hcd.SegmentManager(log, external = false), "sm")

    val boolResponse = TestProbe[Boolean]()
    val intResponse  = TestProbe[Int]()

    val range = 1 to 5
    sm ! SegmentManager.CreateSectorSegments(A, range)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(range.max)

    sm ! SegmentManager.ShutdownOne(SegmentId(A, 1), boolResponse.ref)
    boolResponse.expectMessage(true)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(range.max - 1)

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    testKit.stop(sm, 5.seconds)
  }

  test("All Segments - All Sectors - Shutdown") {
    val sm = testKit.spawn(hcd.SegmentManager(log, external = false), "sm")

    val boolResponse = TestProbe[Boolean]()
    val intResponse  = TestProbe[Int]()

    sm ! SegmentManager.CreateSegments(1 to SegmentId.MAX_SEGMENT_NUMBER)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    // Finished from short command
    val expectedSize = SegmentId.ALL_SECTORS.size * SegmentId.MAX_SEGMENT_NUMBER
    intResponse.expectMessage(expectedSize)

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(0)

    testKit.stop(sm, 5.seconds)
  }

  test("All Segments - All Sectors - Shutdown - internal - again") {
    val sm = testKit.spawn(hcd.SegmentManager(log, external = false), "sm")

    val boolResponse = TestProbe[Boolean]()
    val intResponse  = TestProbe[Int]()

    val maxSegment = SegmentId.MAX_SEGMENT_NUMBER

    sm ! SegmentManager.CreateSegments(1 to maxSegment)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    // Finished from short command
    val expectedSize = SegmentId.ALL_SECTORS.size * maxSegment
    intResponse.expectMessage(expectedSize)

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(0)

    // Should be able to create again with no issues
    sm ! SegmentManager.CreateSegments(1 to maxSegment)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    // Finished from short command
    intResponse.expectMessage(expectedSize)

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(0)

    testKit.stop(sm, 5.seconds)
  }

  test("Get A Segment and All Segments") {

    val sm = testKit.spawn(hcd.SegmentManager(log, external = false), "sm")

    val boolResponse = TestProbe[Boolean]()
    val intResponse  = TestProbe[Int]()

    sm ! SegmentManager.CreateSegments(1 to SegmentId.MAX_SEGMENT_NUMBER)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    // Finished from short command
    val expectedSize = SegmentId.ALL_SECTORS.size * SegmentId.MAX_SEGMENT_NUMBER
    intResponse.expectMessage(expectedSize)

    // Get One Segment
    implicit val timeout: Timeout = 10.seconds
    // Await here so test doesn't end
    val seg = Await.result(sm.ask(SegmentManager.GetSegment(SegmentId("A3"), _)), 10.seconds)
    seg shouldBe a[SegmentManager.Segments]
    seg.segments.size shouldBe 1

    val segs = Await.result(sm.ask(SegmentManager.GetAllSegments(_)), 10.seconds)
    // segs shouldBe non
    segs shouldBe a[SegmentManager.Segments]
    segs.segments.size shouldBe expectedSize

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(0)

    testKit.stop(sm, 5.seconds)
  }

  // EXTERNAL TESTS
  test("One Segment - one Sector - External") {
    val sm = testKit.spawn(hcd.SegmentManager(log, external = true), "sm")

    val boolResponse = TestProbe[Boolean]()
    val intResponse  = TestProbe[Int]()

    // Create one segment
    sm ! SegmentManager.CreateSectorSegments(A, 1 to 1)

    // Verify
    sm ! SegmentManager.HowManySegments(intResponse.ref)
    // Finished from short command
    intResponse.expectMessage(1)

    // Now shutdown with all
    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(0)

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    testKit.stop(sm, 5.seconds)
  }

  test("All Segments - All Sectors - external - Shutdown") {
    val sm = testKit.spawn(hcd.SegmentManager(log, external = true), "sm")

    val boolResponse = TestProbe[Boolean]()
    val intResponse  = TestProbe[Int]()

    sm ! SegmentManager.CreateSegments(1 to SegmentId.MAX_SEGMENT_NUMBER)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    // Finished from short command
    val expectedSize = SegmentId.ALL_SECTORS.size * SegmentId.MAX_SEGMENT_NUMBER
    intResponse.expectMessage(expectedSize)

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(0)

    testKit.stop(sm, 5.seconds)
  }

  test("All Segments - All Sectors - Shutdown - external - again") {
    val sm = testKit.spawn(hcd.SegmentManager(log, external = true), "sm")

    val boolResponse = TestProbe[Boolean]()
    val intResponse  = TestProbe[Int]()

    val maxSegment = SegmentId.MAX_SEGMENT_NUMBER

    sm ! SegmentManager.CreateSegments(1 to maxSegment)

    sm ! SegmentManager.HowManySegments(intResponse.ref)

    val expectedSize = SegmentId.ALL_SECTORS.size * maxSegment
    intResponse.expectMessage(expectedSize)

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(0)

    // Should be able to create again with no issues
    sm ! SegmentManager.CreateSegments(1 to maxSegment)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    // Finished from short command
    intResponse.expectMessage(expectedSize)

    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)

    sm ! SegmentManager.HowManySegments(intResponse.ref)
    intResponse.expectMessage(0)

    testKit.stop(sm, 5.seconds)
  }
}
