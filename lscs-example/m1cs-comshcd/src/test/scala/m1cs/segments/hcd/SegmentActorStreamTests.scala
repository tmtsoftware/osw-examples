package m1cs.segments.hcd

import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.hcd
import m1cs.segments.shared.{A, SegmentId}
import m1cs.segments.streams.server.SocketServerStream
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class SegmentActorStreamTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike {

  private val testKit = ActorTestKit()

  private val cn1     = "ACTUATOR"
  private val cn1args = "ACT_ID=ALL,MODE=SLEW,TARGET=22.3"

  private val cn2     = "CFG_CUR_LOOP"
  private val cn2args = "ACT_ID=(1,2),MOTOR=SNUB,MODE=ON,BUS_VOLTAGE=43.2,CTRL_PARAMS=Kp"

  // Causes error on segment 5
  private val CN_ERROR     = SegmentStreamActor.ERROR_COMMAND_NAME
  private val CN_ERROR_SEG = SegmentStreamActor.ERROR_SEG_ID

  import frameworkTestKit._

  private val logSystem = LoggingSystemFactory.forTestingOnly()
  private val log       = GenericLoggerFactory.getLogger

  override def beforeAll(): Unit = {
    // Start the server
    // XXX TODO FIXME: Use typed system
    new SocketServerStream()(testKit.system.classicSystem)
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
//    Await.ready(logSystem.stop, 2.seconds)
  }

  def waitForCompleted(
      probe: TestProbe[SegmentActor.Response],
      timeout: FiniteDuration = 5.seconds
  ): List[SegmentActor.Response] = {
    var responses = List.empty[SegmentActor.Response]

    probe.fishForMessagePF(timeout) {
      case r @ SegmentActor.Completed(_, segNo, segmentId) =>
        responses = r :: responses
        FishingOutcome.Complete
      case r =>
        responses = r :: responses
        FishingOutcome.Continue
    }
    responses
  }

  test("One Segment - terminate") {
    val s1id = SegmentId(A, 1)
    val s1   = testKit.spawn(hcd.SegmentStreamActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.Send(cn1, cn1args, com1Response.ref)

    // Finished from short command
    val responses = waitForCompleted(com1Response)
    println(s"Responses: $responses")
    responses.head.commandName shouldBe cn1

    s1 ! SegmentActor.ShutdownSegment
    // Sending a message after terminate should cause error

    s1 ! SegmentActor.Send(cn1, cn1args, com1Response.ref)

    testKit.stop(s1, 5.seconds)
  }

  test("One Segment - send 1 command") {
    val s1id = SegmentId(A, 1)
    val s1   = testKit.spawn(hcd.SegmentStreamActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.SendWithTime(cn1, cn1args, 500.milli, com1Response.ref)

    // Finished from short command
    val r1 = com1Response.expectMessageType[SegmentActor.Completed]
    r1.commandName shouldBe cn1

    s1 ! SegmentActor.ShutdownSegment
    testKit.stop(s1, 5.seconds)
  }

  test("One Segment - long with started") {
    val s1id = SegmentId(A, 1)
    val s1   = testKit.spawn(hcd.SegmentStreamActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.SendWithTime(cn1, cn1args, 1200.milli, com1Response.ref)

    // Started from long command
    val r1 = com1Response.expectMessageType[SegmentActor.Started]
    r1.commandName shouldBe cn1
    // Finished from long
    val r2 = com1Response.expectMessageType[SegmentActor.Completed]
    r2.commandName shouldBe cn1

    s1 ! SegmentActor.ShutdownSegment
    testKit.stop(s1, 5.seconds)
  }

  test("One Segment - interleave") {
    val s1id = SegmentId(A, 1)
    val s1   = testKit.spawn(hcd.SegmentStreamActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.SendWithTime(cn1, cn1args, 1200.milli, com1Response.ref)
    s1 ! SegmentActor.SendWithTime(cn2, cn2args, 250.milli, com1Response.ref)

    // Started from long command
    val r1 = com1Response.expectMessageType[SegmentActor.Started]
    r1.commandName shouldBe cn1

    // Finished from short
    var r2 = com1Response.expectMessageType[SegmentActor.Completed]
    r2.commandName shouldBe cn2
    // Finished from long
    r2 = com1Response.expectMessageType[SegmentActor.Completed]
    r2.commandName shouldBe cn1

    s1 ! SegmentActor.ShutdownSegment
    testKit.stop(s1, 5.seconds)
  }

  test("One Segment - makes an error") {
    val s1id = SegmentId(A, SegmentStreamActor.ERROR_SEG_ID)
    val s1   = testKit.spawn(hcd.SegmentStreamActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.SendWithTime(SegmentStreamActor.ERROR_COMMAND_NAME, cn1args, 500.milli, com1Response.ref)

    // Finished for error command
    val r1 = com1Response.expectMessageType[SegmentActor.Error]
    r1.commandName shouldBe SegmentStreamActor.ERROR_COMMAND_NAME

    testKit.stop(s1, 5.seconds)
  }

}
