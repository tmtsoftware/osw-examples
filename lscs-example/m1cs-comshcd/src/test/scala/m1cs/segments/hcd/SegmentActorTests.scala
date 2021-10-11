package m1cs.segments.hcd

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.hcd
import m1cs.segments.hcd.SegmentActor.{ERROR_COMMAND_NAME, ERROR_SEG_ID}
import m1cs.segments.shared.{A, SegmentId}
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class SegmentActorTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike {

  private val testKit = ActorTestKit()

  private val cn1     = "ACTUATOR"
  private val cn1args = "ACT_ID=ALL,MODE=SLEW,TARGET=22.3"

  private val cn2     = "CFG_CUR_LOOP"
  private val cn2args = "ACT_ID=(1,2),MOTOR=SNUB,MODE=ON,BUS_VOLTAGE=43.2,CTRL_PARAMS=Kp"

  // Causes error on segment 5
  private val CN_ERROR     = SegmentActor.ERROR_COMMAND_NAME
  private val CN_ERROR_SEG = SegmentActor.ERROR_SEG_ID

  import frameworkTestKit._

  private val logSystem = LoggingSystemFactory.forTestingOnly()
  private val log       = GenericLoggerFactory.getLogger

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    Await.ready(logSystem.stop, 2.seconds)
  }

  test("One Segment - send 1 command") {
    val s1id = SegmentId(A, 1)
    val s1   = testKit.spawn(hcd.SegmentActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.SendWithTime(cn1, cn1args, 500.milli, com1Response.ref)

    // Finished from short command
    val r1 = com1Response.expectMessageType[SegmentActor.Completed]
    r1.commandName shouldBe cn1

    testKit.stop(s1, 5.seconds)
  }

  test("One Segment - long with started") {
    val s1id = SegmentId(A, 1)
    val s1   = testKit.spawn(hcd.SegmentActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.SendWithTime(cn1, cn1args, 1200.milli, com1Response.ref)

    // Started from long command
    val r1 = com1Response.expectMessageType[SegmentActor.Started]
    r1.commandName shouldBe cn1
    // Finished from long
    val r2 = com1Response.expectMessageType[SegmentActor.Completed]
    r2.commandName shouldBe cn1

    testKit.stop(s1, 5.seconds)
  }

  test("One Segment - interleave") {
    val s1id = SegmentId(A, 1)
    val s1   = testKit.spawn(hcd.SegmentActor(s1id, log), s1id.toString)

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

    testKit.stop(s1, 5.seconds)
  }

  test("One Segment - makes an error") {
    val s1id = SegmentId(A, ERROR_SEG_ID)
    val s1   = testKit.spawn(hcd.SegmentActor(s1id, log), s1id.toString)

    val com1Response = TestProbe[SegmentActor.Response]()

    s1 ! SegmentActor.SendWithTime(ERROR_COMMAND_NAME, cn1args, 500.milli, com1Response.ref)

    // Finished for error command
    val r1 = com1Response.expectMessageType[SegmentActor.Error]
    r1.commandName shouldBe ERROR_COMMAND_NAME

    testKit.stop(s1, 5.seconds)
  }

}
