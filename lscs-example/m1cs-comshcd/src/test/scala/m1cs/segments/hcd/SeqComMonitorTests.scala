package m1cs.segments.hcd

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern.*
import akka.util.Timeout
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.CommandResponse.{Completed, SubmitResponse}
import csw.params.core.models.Id
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.hcd
import m1cs.segments.shared.{A, Sector, SegmentId}
import m1cs.segments.streams.server.SocketServerStream
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class SeqComMonitorTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike {

  private val testKit = ActorTestKit()

  import frameworkTestKit.*

  private val cn1     = "ACTUATOR"
  private val cn1full = "ACT_ID=ALL,MODE=SLEW,TARGET=22.3"

  private val cn2     = "CFG_CUR_LOOP"
  private val cn2full = "ACT_ID=(1,2),MOTOR=SNUB,MODE=ON,BUS_VOLTAGE=43.2,CTRL_PARAMS=(1.2,2.3,3.4)"

  private val logSystem = LoggingSystemFactory.forTestingOnly()
  private val log       = GenericLoggerFactory.getLogger

  // Used for asks
  implicit val timeout: Timeout = 10.seconds

  override def beforeAll(): Unit = {
    // Start an external socket server
    println("Starting an external socket server")
    new SocketServerStream()(testKit.internalSystem.classicSystem)
//    Thread.sleep(2000)
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    Await.ready(logSystem.stop, 2.seconds)
  }

  // External flag means use the stream actor
  def makeSegmentsOld(sector: Sector, range: Range, external: Boolean = false): List[ActorRef[SegmentActor.Command]] = {
    val segments: List[ActorRef[SegmentActor.Command]] = range.map { i =>
      log.debug(s"Creating segment: $i")
      val segmentId = SegmentId(sector, i)
      if (external)
        testKit.spawn(hcd.SegmentStreamActor(segmentId, log), segmentId.toString)
      else
        testKit.spawn(hcd.SegmentActor(segmentId, log), segmentId.toString)
    }.toList
    segments
  }

  def makeAllSegments(segmentNumber: Range, external: Boolean = false): Set[ActorRef[SegmentActor.Command]] = {
    val segList = SegmentId.makeSegments(segmentNumber)
    val segments = if (external) {
      println("External")
      segList.map { i => testKit.spawn(hcd.SegmentStreamActor(i, log), i.toString) }
    }
    else {
      println("Internal")
      segList.map { i => testKit.spawn(hcd.SegmentActor(i, log), i.toString) }
    }
    //println(s"XX: $segments")
    Thread.sleep(3000)
    segments
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

    val sm = testKit.spawn(hcd.SegmentManager(log, external = false), "sm")
    // Create one segment
    sm ! SegmentManager.CreateSectorSegments(A, range)
    val segments = Await.result(sm.ask(SegmentManager.GetAllSegments), 10.seconds).segments

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId = Id()
    val mon   = testKit.spawn(SegComMonitor(cn1, cn1full, segments, runId, tester, log))
    mon ! SegComMonitor.Start

    com1Response.expectMessage(5.seconds, Completed(runId))

    val boolResponse = TestProbe[Boolean]()
    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)
    testKit.stop(sm, 5.seconds)
  }

  test("Ten Segments - send 1 command -- successful") {
    val range = 1 to 10

    val sm = testKit.spawn(hcd.SegmentManager(log, external = false), "sm")
    // Create segments
    sm ! SegmentManager.CreateSectorSegments(A, range)
    val segments = Await.result(sm.ask(SegmentManager.GetAllSegments), 10.seconds).segments

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId = Id()
    val mon   = testKit.spawn(hcd.SegComMonitor(cn1, cn1full, segments, runId, tester, log))
    mon ! SegComMonitor.Start

    com1Response.expectMessage(5.seconds, Completed(runId))

    val boolResponse = TestProbe[Boolean]()
    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)
    testKit.stop(sm, 5.seconds)
  }
  /*
  test("Ten Segments - send special error message with seg 6 makes error") {
    val range    = 1 to 10
    val segments = makeSegments(A, range)

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId = Id()
    val mon   = testKit.spawn(hcd.SegComMonitor(SegmentActor.ERROR_COMMAND_NAME, cn1full, segments, runId, tester, log))
    mon ! SegComMonitor.Start

    val r1 = com1Response.expectMessageType[CommandResponse.Error](5.seconds)
    r1.message shouldBe "Fake Error Message"
    r1.runId shouldBe runId
    log.info(s"Failed with message: ${r1.message}")

    segments.foreach(tup => testKit.stop(tup))
  }
   */
  test("82 segments - send 2 overlapping commands") {
    val range = 1 to SegmentId.MAX_SEGMENT_NUMBER

    val sm = testKit.spawn(hcd.SegmentManager(log, external = false), "sm")
    // Create segments
    sm ! SegmentManager.CreateSectorSegments(A, range)
    val segments = Await.result(sm.ask(SegmentManager.GetAllSegments), 10.seconds).segments

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId1 = Id()
    val mon1   = testKit.spawn(hcd.SegComMonitor(cn1, cn1full, segments, runId1, tester, log))
    mon1 ! SegComMonitor.Start

    val runId2 = Id()
    val mon2   = testKit.spawn(hcd.SegComMonitor(cn2, cn2full, segments, runId2, tester, log))
    mon2 ! SegComMonitor.Start

    // This verifies that both commands finished successfully
    val messages = com1Response.receiveMessages(2, 15.seconds)
    messages.size shouldBe 2
    val resultRunIds = Set(messages.head.runId, messages(1).runId)
    resultRunIds.contains(runId1) shouldBe true
    resultRunIds.contains(runId2) shouldBe true

    val boolResponse = TestProbe[Boolean]()
    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)
    testKit.stop(sm, 5.seconds)
  }

  // External tests - Should have SocketServer running
  test("One Segment - send 1 command - external") {
    //new SocketServerStream()(testKit.internalSystem.classicSystem)

    val range = 1 to 1
    val sm    = testKit.spawn(hcd.SegmentManager(log, external = true), "sm")
    // Create segments
    sm ! SegmentManager.CreateSectorSegments(A, range)
    val segments = Await.result(sm.ask(SegmentManager.GetAllSegments), 10.seconds).segments

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId = Id()
    val mon   = testKit.spawn(SegComMonitor(cn1, cn1full, segments, runId, tester, log))
    mon ! SegComMonitor.Start

    com1Response.expectMessage(5.seconds, Completed(runId))

    val boolResponse = TestProbe[Boolean]()
    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)
    testKit.stop(sm, 5.seconds)
  }

  test("Ten Segments - send 1 command -- successful - external") {
    //new SocketServerStream()(testKit.internalSystem.classicSystem)

    val range = 1 to 10
    val sm    = testKit.spawn(hcd.SegmentManager(log, external = true), "sm")
    // Create segments
    sm ! SegmentManager.CreateSectorSegments(A, range)
    val segments = Await.result(sm.ask(SegmentManager.GetAllSegments), 10.seconds).segments

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId = Id()
    val mon   = testKit.spawn(hcd.SegComMonitor(cn1, cn1full, segments, runId, tester, log))
    mon ! SegComMonitor.Start

    com1Response.expectMessage(5.seconds, Completed(runId))

    val boolResponse = TestProbe[Boolean]()
    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)
    testKit.stop(sm, 5.seconds)
  }
  /*
  test("Ten Segments - send special error message with seg 6 makes error external") {
    //new SocketServerStream()(testKit.internalSystem.classicSystem)

    val range    = 1 to 10
    val segments = makeSegments(A, range, external = true)

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId = Id()
    val mon   = testKit.spawn(hcd.SegComMonitor(SegmentActor.ERROR_COMMAND_NAME, cn1full, segments, runId, tester, log))
    mon ! SegComMonitor.Start

    val r1 = com1Response.expectMessageType[CommandResponse.Error](5.seconds)
    r1.message shouldBe "Fake Error Message"
    r1.runId shouldBe runId
    log.info(s"Failed with message: ${r1.message}")

    segments.foreach(tup => testKit.stop(tup))
  }
   */
  test("82 segments - send 2 overlapping commands - external") {
    //new SocketServerStream()(testKit.internalSystem.classicSystem)

    val range = 1 to SegmentId.MAX_SEGMENT_NUMBER
    val sm    = testKit.spawn(hcd.SegmentManager(log, external = true), "sm")
    // Create segments
    sm ! SegmentManager.CreateSectorSegments(A, range)
    val segments = Await.result(sm.ask(SegmentManager.GetAllSegments), 10.seconds).segments

    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId1 = Id()
    val mon1   = testKit.spawn(hcd.SegComMonitor(cn1, cn1full, segments, runId1, tester, log))
    mon1 ! SegComMonitor.Start

    val runId2 = Id()
    val mon2   = testKit.spawn(hcd.SegComMonitor(cn2, cn2full, segments, runId2, tester, log))
    mon2 ! SegComMonitor.Start

    // This verifies that both commands finished successfully
    val messages = com1Response.receiveMessages(2, 10.seconds)
    messages.size shouldBe 2
    val resultRunIds = Set(messages.head.runId, messages(1).runId)
    resultRunIds.contains(runId1) shouldBe true
    resultRunIds.contains(runId2) shouldBe true

    val boolResponse = TestProbe[Boolean]()
    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)
    testKit.stop(sm, 5.seconds)
  }

  test("492 segments - send 1 - external") {
    //new SocketServerStream()(testKit.internalSystem.classicSystem)

    val range = 1 to SegmentId.MAX_SEGMENT_NUMBER
    val sm    = testKit.spawn(hcd.SegmentManager(log, external = true), "sm")
    // Create segments
    sm ! SegmentManager.CreateSegments(range)
    val segments = Await.result(sm.ask(SegmentManager.GetAllSegments), 10.seconds).segments
    segments.size shouldBe 492
    val com1Response = TestProbe[SubmitResponse]()
    val tester       = makeTester(com1Response)

    val runId1 = Id()
    val mon1   = testKit.spawn(hcd.SegComMonitor(cn1, cn1full, segments, runId1, tester, log))
    mon1 ! SegComMonitor.Start

    // This verifies that both commands finished successfully
    com1Response.expectMessage(10.seconds, Completed(runId1))

    val boolResponse = TestProbe[Boolean]()
    sm ! SegmentManager.ShutdownAll(boolResponse.ref)
    boolResponse.expectMessage(true)
    testKit.stop(sm, 5.seconds)
  }
}
