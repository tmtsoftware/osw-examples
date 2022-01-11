package m1cs.segments.hcd

import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.hcd
import m1cs.segments.streams.server.SocketServerStream
import m1cs.segments.segcommands.{A, SegmentId}
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class SegmentActorTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike {

  private val testKit = ActorTestKit()

  private val cn1     = "ACTUATOR"
  private val cn1full = "ACTUATOR ACT_ID=ALL,MODE=SLEW,TARGET=22.3"

  private val cn2     = "CFG_CUR_LOOP"
  private val cn2full = "CFG_CUR_LOOP ACT_ID=(1,2),MOTOR=SNUB,MODE=ON,BUS_VOLTAGE=43.2,CTRL_PARAMS=Kp"

  import frameworkTestKit.*

  LoggingSystemFactory.forTestingOnly()
  private val log = GenericLoggerFactory.getLogger

  override def beforeAll(): Unit = {
    super.beforeAll()

    // Start a local simulator
    val _ = new SocketServerStream()(testKit.system)
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  def waitForCompleted(
      probe: TestProbe[SegmentActor.Response],
      timeout: FiniteDuration = 5.seconds
  ): List[SegmentActor.Response] = {
    var responses = List.empty[SegmentActor.Response]

    probe.fishForMessagePF(timeout) {
      case r @ SegmentActor.Completed(_, _, _) =>
        responses = r :: responses
        FishingOutcome.Complete
      case r =>
        responses = r :: responses
        FishingOutcome.Continue
    }
    responses
  }

  test("One Segment - send 1 command") {
    val s1id = SegmentId(A, 1)
    val s1   = testKit.spawn(hcd.SegmentActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.SendWithTime(cn1, cn1full, 500.milli, com1Response.ref)

    // Finished from short command
    val r1 = com1Response.expectMessageType[SegmentActor.Completed]
    r1.commandName shouldBe cn1

    val boolResponse = TestProbe[Boolean]()
    s1 ! SegmentActor.ShutdownSegment2(boolResponse.ref)
    testKit.stop(s1, 5.seconds)
  }

  test("One Segment - terminate") {
    val s1id = SegmentId(A, 1)
    val s1   = testKit.spawn(hcd.SegmentActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.Send(cn1, cn1full, com1Response.ref)

    // Finished from short command
    val responses = waitForCompleted(com1Response)
    responses.head.commandName shouldBe cn1

    val boolResponse = TestProbe[Boolean]()
    s1 ! SegmentActor.ShutdownSegment2(boolResponse.ref)

    // Sending a message after terminate should cause error
    s1 ! SegmentActor.Send(cn1, cn1full, com1Response.ref)
    com1Response.expectNoMessage(200.milli)

    testKit.stop(s1, 5.seconds)
  }

  test("One Segment - interleave") {
    val s1id = SegmentId(A, 1)
    val s1   = testKit.spawn(hcd.SegmentActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.SendWithTime(cn1, cn1full, 1200.milli, com1Response.ref)
    s1 ! SegmentActor.SendWithTime(cn2, cn2full, 250.milli, com1Response.ref)

    // Finished from short
    var r2 = com1Response.expectMessageType[SegmentActor.Completed]
    r2.commandName shouldBe cn2
    // Finished from long
    r2 = com1Response.expectMessageType[SegmentActor.Completed]
    r2.commandName shouldBe cn1

    val boolResponse = TestProbe[Boolean]()
    s1 ! SegmentActor.ShutdownSegment2(boolResponse.ref)
    testKit.stop(s1, 5.seconds)
  }

  test("One Segment - makes an error") {
    val s1id = SegmentId(A, SegmentActor.ERROR_SEG_ID)
    val s1   = testKit.spawn(hcd.SegmentActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.Send(SegmentActor.ERROR_COMMAND_NAME, cn1full, com1Response.ref)

    // Finished for error command
    val r1 = com1Response.expectMessageType[SegmentActor.Error]
    r1.commandName shouldBe SegmentActor.ERROR_COMMAND_NAME

    testKit.stop(s1, 5.seconds)
  }

}
