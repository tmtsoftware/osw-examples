package m1cs.segments.shared

import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.prefix.models.Prefix
import csw.testkit.FrameworkTestKit
import m1cs.segments.shared.HcdDirectCommand.{lscsCommandKey, lscsCommandNameKey, lscsDirectCommand}
import m1cs.segments.support.segcommands.Common.{ALL_SEGMENTS, CommandMap, segmentIdKey}
import m1cs.segments.support.SegmentId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class HcdCommandsTests extends AnyFunSuite with Matchers with BeforeAndAfterAll {
  import m1cs.segments.support.segcommands.ACTUATOR
  import m1cs.segments.support.segcommands.ACTUATOR.ActuatorModes.*

  private val frameworkTestKit = FrameworkTestKit()
  import frameworkTestKit.*

  val prefix: Prefix = Prefix("M1CS.hcdClient") // TEMP

  LoggingSystemFactory.forTestingOnly()
  private val log = GenericLoggerFactory.getLogger

  test("test that command map is working okay") {
    // Create a setup for Assembly
    val setup = ACTUATOR.toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).asSetup

    val command: String = CommandMap(setup.commandName)(setup)
    command shouldBe "ACTUATOR ACT_ID=(1,3), MODE=TRACK, TARGET=22.34"
  }

  test("prepare an HCD command for one segment") {
    // Create an assembly Setup to Segment
    val testSegment   = SegmentId("A23")
    val assemblySetup = ACTUATOR.toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).toSegment(testSegment).asSetup

    val testCommand     = "ACTUATOR ACT_ID=(1,3), MODE=TRACK, TARGET=22.34"
    val command: String = CommandMap(assemblySetup.commandName)(assemblySetup)
    command shouldBe testCommand

    val hcdSetup = HcdDirectCommand.toHcdDirectCommand(prefix, assemblySetup)
    hcdSetup.commandName shouldBe lscsDirectCommand
    hcdSetup(lscsCommandKey).head shouldBe testCommand
    hcdSetup(lscsCommandNameKey).head shouldBe assemblySetup.commandName.name
    hcdSetup(segmentIdKey).head shouldBe testSegment.toString
  }

  test("prepare an HCD command for all segments") {
    // Create an assembly Setup to Segment -- default is ALL
    val assemblySetup = ACTUATOR.toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).asSetup

    log.info(s"Assembly setup: $assemblySetup")

    val testCommand     = "ACTUATOR ACT_ID=(1,3), MODE=TRACK, TARGET=22.34"
    val command: String = CommandMap(assemblySetup.commandName)(assemblySetup)
    command shouldBe testCommand

    val hcdSetup = HcdDirectCommand.toHcdDirectCommand(prefix, assemblySetup)
    hcdSetup.commandName shouldBe lscsDirectCommand
    hcdSetup(lscsCommandKey).head shouldBe testCommand
    hcdSetup(lscsCommandNameKey).head shouldBe assemblySetup.commandName.name
    hcdSetup(segmentIdKey).head shouldBe ALL_SEGMENTS
  }
}
