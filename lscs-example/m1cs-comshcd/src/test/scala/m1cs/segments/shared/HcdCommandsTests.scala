package m1cs.segments.shared

import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.prefix.models.Prefix
import csw.testkit.FrameworkTestKit
import m1cs.segments.shared.HcdCommands.{toAllSegments, toOneSegment}
import m1cs.segments.shared.SegmentCommands.ACTUATOR.ActuatorModes.TRACK
import m1cs.segments.shared.SegmentCommands.ACTUATOR.{toActuator, toActuatorCommand}
import m1cs.segments.shared.SegmentCommands.{AllSegments, OneSegment}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class HcdCommandsTests extends AnyFunSuite with Matchers with BeforeAndAfterAll {
  private val frameworkTestKit = FrameworkTestKit()
  import frameworkTestKit._

  val prefix: Prefix = Prefix("M1CS.hcdClient") // TEMP

  private val logSystem = LoggingSystemFactory.forTestingOnly()
  private val log       = GenericLoggerFactory.getLogger

  override def afterAll(): Unit = {
    frameworkTestKit.shutdown()
    // Await.ready(logSystem.stop, 5.seconds)
  }

  test("toCommandSetup 1") {
    log.info("Starting Tests")
    val to      = toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).toSegment(SegmentId("A5"))
    val command = toActuatorCommand(to.asSetup)

    val hcdSetup = command match {
      case AllSegments(command) =>
        toAllSegments(prefix, command)
      case OneSegment(segmentId, command) =>
        toOneSegment(prefix, segmentId, command)
    }
    hcdSetup.commandName.name shouldBe "ACTUATOR"

    Thread.sleep(3000)
  }

}
