package m1cs.segments.hcd

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.util.Timeout
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.CommandResponse
import csw.params.commands.CommandResponse.{Completed, SubmitResponse}
import csw.params.core.models.Id
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.hcd
import m1cs.segments.streams.server.SocketServerStream
import m1cs.segments.segcommands.{A, SegmentId}
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.duration.DurationInt

class SegComMonitorTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike {

  private val testKit = ActorTestKit()

  import frameworkTestKit.*

  private val cn1     = "ACTUATOR"
  private val cn1full = "ACTUATOR ACT_ID=ALL,MODE=SLEW,TARGET=22.3"

  private val cn2     = "CFG_CUR_LOOP"
  private val cn2full = "CFG_CUR_LOOP ACT_ID=(1,2),MOTOR=SNUB,MODE=ON,BUS_VOLTAGE=43.2,CTRL_PARAMS=(1.2,2.3,3.4)"

  LoggingSystemFactory.forTestingOnly()
  private val log = GenericLoggerFactory.getLogger

  private val testCreator: SegmentManager.SegmentCreator = (s, log) => testKit.spawn(hcd.SegmentActor(s, log), s.toString)

  // Used for asks
  implicit val timeout: Timeout = 10.seconds

  override def beforeAll(): Unit = {
    super.beforeAll()

    // Start an internal socket server
    log.debug("Starting an external socket server")
    // Add a -DsimulatorHost property to use external simulator
    val _ = new SocketServerStream()(testKit.internalSystem)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  def makeTester(testProbe: TestProbe[SubmitResponse]): SubmitResponse => Unit = {
    def tester(response: SubmitResponse): Unit = {
      log.info(s"Tester received: $response")
      testProbe.ref ! response
    }
    tester
  }

  test("One Segment - send 1 command") {
    val range = 1 to 1

    // Create one segment
    val segments      = SegmentManager.createSectorSegments(testCreator, A, range, log)
    val segmentActors = segments.getAllSegments.segments

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId = Id()
    val mon   = testKit.spawn(SegComMonitor(cn1, cn1full, segmentActors, runId, tester, log))
    mon ! SegComMonitor.Start

    com1Response.expectMessage(5.seconds, Completed(runId))

    segments.shutdownAll()

    testKit.stop(mon, 5.seconds)
  }

  test("Ten Segments - same segment - send 1 command -- successful") {
    val range = 1 to 10

    // Create segments
    val segments      = SegmentManager.createSectorSegments(testCreator, A, range, log)
    val segmentActors = segments.getAllSegments.segments

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId = Id()
    val mon   = testKit.spawn(SegComMonitor(cn1, cn1full, segmentActors, runId, tester, log))
    mon ! SegComMonitor.Start

    com1Response.expectMessage(5.seconds, Completed(runId))

    segments.shutdownAll()

    testKit.stop(mon, 5.seconds)
  }

  test("Ten Segments - send special error message with seg 6 makes error") {
    // This was useful at the beginning but probably not a great test now.  Can remove.
    val range = 1 to 10

    // Create segments
    val segments      = SegmentManager.createSectorSegments(testCreator, A, range, log)
    val segmentActors = segments.getAllSegments.segments

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId = Id()
    val mon   = testKit.spawn(hcd.SegComMonitor(SegmentActor.ERROR_COMMAND_NAME, cn1full, segmentActors, runId, tester, log))
    mon ! SegComMonitor.Start

    val r1 = com1Response.expectMessageType[CommandResponse.Error](5.seconds)
    r1.message shouldBe SegmentActor.FAKE_ERROR_MESSAGE
    r1.runId shouldBe runId
    log.info(s"Failed with message: ${r1.message}")

    // Wait for final logs
    com1Response.expectNoMessage(100.milli)
    segments.shutdownAll()

    testKit.stop(mon, 5.seconds)
  }

  test("82 segments - send 3 overlapping commands") {
    val range = 1 to SegmentId.MAX_SEGMENT_NUMBER

    // Create segments
    val segments      = SegmentManager.createSectorSegments(testCreator, A, range, log)
    val segmentActors = segments.getAllSegments.segments

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId1 = Id()
    val mon1   = testKit.spawn(hcd.SegComMonitor(cn1, cn1full, segmentActors, runId1, tester, log))
    mon1 ! SegComMonitor.Start

    val runId2 = Id()
    val mon2   = testKit.spawn(hcd.SegComMonitor(cn2, cn2full, segmentActors, runId2, tester, log))
    mon2 ! SegComMonitor.Start

    val runId3 = Id()
    val mon3   = testKit.spawn(hcd.SegComMonitor(cn2, cn2full, segmentActors, runId3, tester, log))
    mon3 ! SegComMonitor.Start

    // This verifies that all three commands finished successfully
    val messages = com1Response.receiveMessages(3, 15.seconds)
    messages.size shouldBe 3
    val resultRunIds = Set(messages.head.runId, messages(1).runId, messages(2).runId)
    resultRunIds.contains(runId1) shouldBe true
    resultRunIds.contains(runId2) shouldBe true
    resultRunIds.contains(runId3) shouldBe true

    // Wait for final logs
    com1Response.expectNoMessage(100.milli)
    segments.shutdownAll()

    testKit.stop(mon1, 5.seconds)
    testKit.stop(mon2, 5.seconds)
    testKit.stop(mon3, 5.seconds)
  }

  //#test1
  test("492 segments - send 1 - external") {
    // Note: This test fails on Mac, server must be running on Linux due to open file issue?

    val range = 1 to SegmentId.MAX_SEGMENT_NUMBER
    // Create segments
    val segments      = SegmentManager.createSegments(testCreator, range, log)
    val segmentActors = segments.getAllSegments.segments
    segmentActors.size shouldBe 492

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId1 = Id()
    val mon    = testKit.spawn(hcd.SegComMonitor(cn1, cn1full, segmentActors, runId1, tester, log))
    mon ! SegComMonitor.Start

    // This verifies that both commands finished successfully
    com1Response.expectMessage(10.seconds, Completed(runId1))

    // Wait for final logs
    com1Response.expectNoMessage(100.milli)
    segments.shutdownAll()

    testKit.stop(mon, 5.seconds)
  }
  //#test1

  //#test2
  test("492 segments - overlap - external") {
    // Note: This test fails on Mac, server must be running on Linux due to open file issue?

    val range = 1 to SegmentId.MAX_SEGMENT_NUMBER
    // Create segments
    val segments      = SegmentManager.createSegments(testCreator, range, log)
    val segmentActors = segments.getAllSegments.segments
    segmentActors.size shouldBe 492

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId1 = Id()
    val mon1   = testKit.spawn(hcd.SegComMonitor(cn1, cn1full, segmentActors, runId1, tester, log))
    mon1 ! SegComMonitor.Start

    val runId2 = Id()
    val mon2   = testKit.spawn(hcd.SegComMonitor(cn2, cn2full, segmentActors, runId2, tester, log))
    mon2 ! SegComMonitor.Start

    // This verifies that both commands finished successfully
    val messages = com1Response.receiveMessages(2, 15.seconds)
    messages.size shouldBe 2
    val resultRunIds = Set(messages.head.runId, messages(1).runId)
    resultRunIds.contains(runId1) shouldBe true
    resultRunIds.contains(runId2) shouldBe true

    // Wait for final logs or shutdown
    com1Response.expectNoMessage(100.milli)
    segments.shutdownAll()

    testKit.stop(mon1, 5.seconds)
    testKit.stop(mon2, 5.seconds)
  }
  //#test2
}
