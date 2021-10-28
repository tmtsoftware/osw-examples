package m1cs.segments.support.segcommands

import csw.prefix.models.Prefix
import m1cs.segments.support.SegmentId
import m1cs.segments.support.segcommands.Common.{ALL_SEGMENTS, AllActuators, segmentIdKey, segmentRangeKey}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SegmentCommandsTests extends AnyFunSuite with Matchers {

  val prefix: Prefix = Prefix("M1CS.client") // TEMP

  test("Handling of SegmentIds") {
    import m1cs.segments.support.segcommands.ACTUATOR.*
    import m1cs.segments.support.segcommands.ACTUATOR.ActuatorModes.*

    var to = toActuator(prefix, Set(1, 3)).withMode(TRACK)
    // Verify segmentId is all by default
    to.asSetup(segmentIdKey).head shouldBe ALL_SEGMENTS

    // Override with specific segment
    val testSegment = "B22"
    to = toActuator(prefix, Set(1, 3)).withMode(SLEW).toSegment(SegmentId(testSegment))
    to.asSetup(segmentIdKey).head shouldBe testSegment

    // Currently not handling ranges
  }

  test("To From ACTUATOR") {
    import m1cs.segments.support.segcommands.ACTUATOR.*
    import m1cs.segments.support.segcommands.ACTUATOR.ActuatorModes.*

    var to = toActuator(prefix, Set(1, 3)).withMode(TRACK)
    // Verify segmentId is all by default
    to.asSetup(segmentIdKey).head shouldBe ALL_SEGMENTS

    // Verify override works
    to = toActuator(prefix, Set(1, 3)).withMode(TRACK).toSegment(SegmentId("A22"))
    to.asSetup(segmentIdKey).head shouldBe "A22"

    // Only 2 actuators
    to = toActuator(prefix, Set(1, 3)).withMode(TRACK)
    ACTUATOR.toCommand(to.asSetup) shouldBe "ACTUATOR ACT_ID=(1,3), MODE=TRACK"

    to = toActuator(prefix, Set(1, 3)).withTarget(22.34)
    ACTUATOR.toCommand(to.asSetup) shouldBe "ACTUATOR ACT_ID=(1,3), TARGET=22.34"

    to = toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34)
    ACTUATOR.toCommand(to.asSetup) shouldBe "ACTUATOR ACT_ID=(1,3), MODE=TRACK, TARGET=22.34"

    // Verify All
    to = toActuator(prefix, AllActuators).withMode(TRACK).withTarget(22.34)
    ACTUATOR.toCommand(to.asSetup) shouldBe "ACTUATOR ACT_ID=ALL, MODE=TRACK, TARGET=22.34"

    // Check for too big set
    assertThrows[IllegalArgumentException] {
      toActuator(prefix, Set(1, 2, 3, 4))
    }

    // Check for empty set
    assertThrows[IllegalArgumentException] {
      toActuator(prefix, Set())
    }

    // Check for out of range ID
    assertThrows[IllegalArgumentException] {
      toActuator(prefix, Set(1, 2, 4))
    }
  }

  test("To From TARG_GEN_ACT") {
    import m1cs.segments.support.segcommands.TARG_GEN_ACT.*
    import m1cs.segments.support.segcommands.TARG_GEN_ACT.TargetShapes.*
    import m1cs.segments.support.segcommands.TARG_GEN_ACT.TargetGenModes.*

    // Only 2 actuators with LoopMode
    var setup = toActTargetGen(prefix, Set(1, 3)).withMode(ON).asSetup
    toCommand(setup) shouldBe "TARG_GEN_ACT ACT_ID=(1,3), MODE=ON"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // All segments and mode
    setup = toActTargetGen(prefix, AllActuators).withMode(ON).asSetup
    toCommand(setup) shouldBe "TARG_GEN_ACT ACT_ID=ALL, MODE=ON"

    // All segments and shape
    setup = toActTargetGen(prefix, AllActuators).withShape(TRI).asSetup
    toCommand(setup) shouldBe "TARG_GEN_ACT ACT_ID=ALL, TYPE=TRI"

    // All segments and amplitude
    setup = toActTargetGen(prefix, AllActuators).withAmplitude(2.3).asSetup
    toCommand(setup) shouldBe "TARG_GEN_ACT ACT_ID=ALL, AMPL=2.3"

    // All segments and period
    setup = toActTargetGen(prefix, AllActuators).withPeriod(1.0).asSetup
    toCommand(setup) shouldBe "TARG_GEN_ACT ACT_ID=ALL, PERIOD=1.0"

    // All segments and offset
    setup = toActTargetGen(prefix, AllActuators).withOffset(12.4).asSetup
    toCommand(setup) shouldBe "TARG_GEN_ACT ACT_ID=ALL, OFFSET=12.4"

    // Some segments and all options
    setup =
      toActTargetGen(prefix, Set(1, 2)).withMode(ON).withShape(TRI).withAmplitude(2.3).withPeriod(1.0).withOffset(12.4).asSetup
    toCommand(setup) shouldBe "TARG_GEN_ACT ACT_ID=(1,2), MODE=ON, TYPE=TRI, AMPL=2.3, PERIOD=1.0, OFFSET=12.4"

    // Check for too big set
    assertThrows[IllegalArgumentException] {
      toActTargetGen(prefix, Set(1, 2, 3, 4))
    }

    // Check for empty set
    assertThrows[IllegalArgumentException] {
      toActTargetGen(prefix, Set())
    }

    // Check for out of range ID
    assertThrows[IllegalArgumentException] {
      toActTargetGen(prefix, Set(1, 2, 6))
    }
  }

  test("To From CFG_CUR_LOOP") {
    import m1cs.segments.support.segcommands.CFG_CUR_LOOP.*
    import m1cs.segments.support.segcommands.CFG_CUR_LOOP.CfgCurLoopMotor.*
    import m1cs.segments.support.segcommands.Common.CfgLoopModes.*

    // Only 2 actuators with LoopMode
    var setup = toCfgActCurLoop(prefix, Set(1, 3), SNUB).withLoopMode(ON).asSetup
    toCommand(setup) shouldBe "CFG_CUR_LOOP ACT_ID=(1,3), MOTOR=SNUB, MODE=ON"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // All segments and bus voltage
    setup = toCfgActCurLoop(prefix, AllActuators, SNUB).withBusVoltage(12.3).asSetup
    toCommand(setup) shouldBe "CFG_CUR_LOOP ACT_ID=ALL, MOTOR=SNUB, BUS_VOLTAGE=12.3"

    // All segments and ctrlParams
    setup = toCfgActCurLoop(prefix, AllActuators, SNUB).withCtrlParams(Array(1.2, 3.4, 5.6)).asSetup
    toCommand(setup) shouldBe "CFG_CUR_LOOP ACT_ID=ALL, MOTOR=SNUB, CTRL_PARAMS=(1.2,3.4,5.6)"

    // Check for too big set
    assertThrows[IllegalArgumentException] {
      toCfgActCurLoop(prefix, Set(1, 2, 3, 4), OFFLD).withLoopMode(OFF)
    }

    // Check for empty set
    assertThrows[IllegalArgumentException] {
      toCfgActCurLoop(prefix, Set(), OFFLD).withLoopMode(OFF)
    }

    // Check for out of range ID
    assertThrows[IllegalArgumentException] {
      toCfgActCurLoop(prefix, Set(1, 2, 6), OFFLD).withLoopMode(OFF)
    }

    // Check for bad number of ctrlParams
    assertThrows[IllegalArgumentException] {
      toCfgActCurLoop(prefix, Set(1, 2, 6), OFFLD).withCtrlParams(Array(1.5, 2.5))
    }

    // Check for out of range bus voltage > 24
    assertThrows[IllegalArgumentException] {
      toCfgActCurLoop(prefix, Set(1, 2, 6), OFFLD).withBusVoltage(28.0)
    }

    // Check for out of range bus voltage < 0
    assertThrows[IllegalArgumentException] {
      toCfgActCurLoop(prefix, Set(1, 2, 6), OFFLD).withBusVoltage(-22.3)
    }
  }

  test("To From CFG_ACT_VC") {
    import m1cs.segments.support.segcommands.CFG_ACT_VC.*
    import m1cs.segments.support.segcommands.Common.CfgLoopModes.*

    // All 3 actuators
    var setup = toCfgActVc(prefix, AllActuators).withLoopMode(OPEN).asSetup
    toCommand(setup) shouldBe "CFG_ACT_VC ACT_ID=ALL, MODE=OPEN"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // All actuators with slewParams
    setup = toCfgActVc(prefix, AllActuators).withSlewParams(Array(1.0, 2.0, 3.0, 4.0)).asSetup
    toCommand(setup) shouldBe "CFG_ACT_VC ACT_ID=ALL, SLEW_CTRL_PARAMS=(1.0,2.0,3.0,4.0)"

    // One actuator with trkParams
    setup = toCfgActVc(prefix, Set(3)).withTrkParams(Array(1.0, 2.0, 3.0, 4.0)).asSetup
    toCommand(setup) shouldBe "CFG_ACT_VC ACT_ID=(3), TRK_CTRL_PARAMS=(1.0,2.0,3.0,4.0)"

    // All actuator with all
    setup = toCfgActVc(prefix, Set(1, 2, 3))
      .withTrkParams(Array(1.0, 2.0, 3.0, 4.0))
      .withLoopMode(ON)
      .withSlewParams(Array(5.0, 6.0, 7.0, 8.0))
      .asSetup
    toCommand(
      setup
    ) shouldBe "CFG_ACT_VC ACT_ID=ALL, MODE=ON, SLEW_CTRL_PARAMS=(5.0,6.0,7.0,8.0), TRK_CTRL_PARAMS=(1.0,2.0,3.0,4.0)"

    // Check for too big set
    assertThrows[IllegalArgumentException] {
      toCfgActVc(prefix, Set(1, 2, 3, 4)).withLoopMode(OPEN)
    }

    // Check for empty set
    assertThrows[IllegalArgumentException] {
      toCfgActVc(prefix, Set()).withLoopMode(OPEN)
    }

    // Check for bad number of ctrlParams
    assertThrows[IllegalArgumentException] {
      toCfgActVc(prefix, Set(2, 1, 3, 5)).withLoopMode(OPEN)
    }

    // Check for out of range ID
    assertThrows[IllegalArgumentException] {
      toCfgActVc(prefix, Set(2, 1, 4)).withLoopMode(OPEN)
    }

    // Check for bad number of slew params
    var caught = intercept[IllegalArgumentException] {
      toCfgActVc(prefix, AllActuators).withSlewParams(Array(1.0, 2.0, 3.0))
    }
    // Check for right error message
    caught.getMessage contains "4"

    // Check for bad number of trk params
    caught = intercept[IllegalArgumentException] {
      toCfgActVc(prefix, Set(3)).withTrkParams(Array(1.0, 2.0, 3.0, 4.0, 5.0, 6.0))
    }
    // Check for right error message
    caught.getMessage contains "4"
  }

  test("To From CFG_ACT_OFFLD") {
    import m1cs.segments.support.segcommands.CFG_ACT_OFFLD.*
    import m1cs.segments.support.segcommands.Common.ControllerModes.*

    // All 3 actuators
    var setup = toActOffldCtrl(prefix, AllActuators).asSetup
    toCommand(setup) shouldBe "CFG_ACT_OFFLD ACT_ID=ALL"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // All actuators + offload mode
    setup = toActOffldCtrl(prefix, AllActuators).withOffloadMode(CONTINUOUS).asSetup
    toCommand(setup) shouldBe "CFG_ACT_OFFLD ACT_ID=ALL, MODE=CONTINUOUS"

    // All actuators + gain
    setup = toActOffldCtrl(prefix, AllActuators).withGain(5).asSetup
    toCommand(setup) shouldBe "CFG_ACT_OFFLD ACT_ID=ALL, CONT_GAIN=5.0"

    // All actuators + thresh
    setup = toActOffldCtrl(prefix, AllActuators).withThresh(22.3).asSetup
    toCommand(setup) shouldBe "CFG_ACT_OFFLD ACT_ID=ALL, CONT_THRESH=22.3"

    // All actuators + discreteStart
    setup = toActOffldCtrl(prefix, AllActuators).withDiscreteStart(101.23).asSetup
    toCommand(setup) shouldBe "CFG_ACT_OFFLD ACT_ID=ALL, DISCRETE_START=101.23"

    // All actuators + discreteEnd
    setup = toActOffldCtrl(prefix, AllActuators).withDiscreteStop(-101.23).asSetup
    toCommand(setup) shouldBe "CFG_ACT_OFFLD ACT_ID=ALL, DISCRETE_STOP=-101.23"

    // Some actuators + all options
    setup = toActOffldCtrl(prefix, Set(1))
      .withOffloadMode(CONTINUOUS)
      .withGain(5)
      .withThresh(22.3)
      .withDiscreteStart(101.23)
      .withDiscreteStop(-101.23)
      .asSetup
    toCommand(
      setup
    ) shouldBe "CFG_ACT_OFFLD ACT_ID=(1), MODE=CONTINUOUS, CONT_GAIN=5.0, CONT_THRESH=22.3, DISCRETE_START=101.23, DISCRETE_STOP=-101.23"

    // Check for too big set
    assertThrows[IllegalArgumentException] {
      toActOffldCtrl(prefix, Set(1, 2, 3, 4))
    }

    // Check for empty set
    assertThrows[IllegalArgumentException] {
      toActOffldCtrl(prefix, Set())
    }

    // Check for bad number of ctrlParams
    assertThrows[IllegalArgumentException] {
      toActOffldCtrl(prefix, Set(2, 1, 3, 5))
    }

    // Check for out of range ID
    assertThrows[IllegalArgumentException] {
      toActOffldCtrl(prefix, Set(2, 1, 4))
    }
  }

}
