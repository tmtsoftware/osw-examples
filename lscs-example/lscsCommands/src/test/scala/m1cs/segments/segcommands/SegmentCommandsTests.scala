package m1cs.segments.segcommands

import csw.prefix.models.Prefix
import m1cs.segments.segcommands.Common.{ALL_SEGMENTS, ALL_ACTUATORS, segmentIdKey, segmentRangeKey, valuesToString}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SegmentCommandsTests extends AnyFunSuite with Matchers {

  val prefix: Prefix = Prefix("M1CS.client") // TEMP

  test("Handling of SegmentIds") {
    import m1cs.segments.segcommands.ACTUATOR.*
    import m1cs.segments.segcommands.ACTUATOR.ActuatorModes.*

    var to = toActuator(prefix, Set(1, 3)).withMode(TRACK)
    // Verify segmentId is all by default
    to.asSetup(segmentIdKey).head shouldBe ALL_SEGMENTS

    // Override with specific segment
    val testSegment = "B22"
    to = toActuator(prefix, Set(1, 3)).withMode(SLEW).toSegment(SegmentId(testSegment))
    to.asSetup(segmentIdKey).head shouldBe testSegment

    // Currently not handling ranges
  }

  //#example-tests
  test("To From ACTUATOR") {
    import m1cs.segments.segcommands.ACTUATOR.*
    import m1cs.segments.segcommands.ACTUATOR.ActuatorModes.*

    var to = toActuator(prefix, Set(1, 3)).withMode(TRACK)
    // Verify segmentId is all by default
    to.asSetup(segmentIdKey).head shouldBe ALL_SEGMENTS

    // Verify override works
    to = toActuator(prefix, Set(1, 3)).withMode(TRACK).toSegment(SegmentId("A22"))
    to.asSetup(segmentIdKey).head shouldBe "A22"

    // Only 2 actuators
    to = toActuator(prefix, Set(1, 3)).withMode(TRACK)
    ACTUATOR.toCommand(to.asSetup) shouldBe s"${COMMAND_NAME.name} ACT_ID=(1,3), MODE=TRACK"

    to = toActuator(prefix, Set(1, 3)).withTarget(22.34)
    ACTUATOR.toCommand(to.asSetup) shouldBe s"${COMMAND_NAME.name} ACT_ID=(1,3), TARGET=22.34"

    to = toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34)
    ACTUATOR.toCommand(to.asSetup) shouldBe s"${COMMAND_NAME.name} ACT_ID=(1,3), MODE=TRACK, TARGET=22.34"

    // Verify All
    to = toActuator(prefix, ALL_ACTUATORS).withMode(TRACK).withTarget(22.34)
    ACTUATOR.toCommand(to.asSetup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MODE=TRACK, TARGET=22.34"

    // Check for no optional
    assertThrows[IllegalArgumentException] {
      toActuator(prefix, Set(1, 2, 3)).asSetup
    }

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
  //#example-tests

  test("To From TARG_GEN_ACT") {
    import m1cs.segments.segcommands.TARG_GEN_ACT.*
    import m1cs.segments.segcommands.TARG_GEN_ACT.TargetGenModes.*
    import m1cs.segments.segcommands.TARG_GEN_ACT.TargetShapes.*

    // Only 2 actuators with LoopMode
    var setup = toActTargetGen(prefix, Set(1, 3)).withMode(ON).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=(1,3), MODE=ON"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // All segments and mode
    setup = toActTargetGen(prefix, ALL_ACTUATORS).withMode(ON).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MODE=ON"

    // All segments and shape
    setup = toActTargetGen(prefix, ALL_ACTUATORS).withShape(TRI).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, TYPE=TRI"

    // All segments and amplitude
    setup = toActTargetGen(prefix, ALL_ACTUATORS).withAmplitude(2.3).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, AMPL=2.3"

    // All segments and period
    setup = toActTargetGen(prefix, ALL_ACTUATORS).withPeriod(1.0).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, PERIOD=1.0"

    // All segments and offset
    setup = toActTargetGen(prefix, ALL_ACTUATORS).withOffset(12.4).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, OFFSET=12.4"

    // Some segments and all options
    setup =
      toActTargetGen(prefix, Set(1, 2)).withMode(ON).withShape(TRI).withAmplitude(2.3).withPeriod(1.0).withOffset(12.4).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=(1,2), MODE=ON, TYPE=TRI, AMPL=2.3, PERIOD=1.0, OFFSET=12.4"

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
    import m1cs.segments.segcommands.CFG_CUR_LOOP.*
    import m1cs.segments.segcommands.CFG_CUR_LOOP.CfgCurLoopMotor.*
    import m1cs.segments.segcommands.Common.CfgLoopModes.*

    // Only 2 actuators with LoopMode
    var setup = toCfgActCurLoop(prefix, Set(1, 3), SNUB).withLoopMode(ON).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=(1,3), MOTOR=SNUB, MODE=ON"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // All segments and bus voltage
    setup = toCfgActCurLoop(prefix, ALL_ACTUATORS, SNUB).withBusVoltage(12.3).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MOTOR=SNUB, BUS_VOLTAGE=12.3"

    // All segments and ctrlParams
    setup = toCfgActCurLoop(prefix, ALL_ACTUATORS, SNUB).withCtrlParams(Array(1.2, 3.4, 5.6)).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MOTOR=SNUB, CTRL_PARAMS=(1.2,3.4,5.6)"

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
    import m1cs.segments.segcommands.CFG_ACT_VC.*
    import m1cs.segments.segcommands.Common.CfgLoopModes.*

    // All 3 actuators
    var setup = toCfgActVc(prefix, ALL_ACTUATORS).withLoopMode(OPEN).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MODE=OPEN"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // All actuators with slewParams
    setup = toCfgActVc(prefix, ALL_ACTUATORS).withSlewParams(Array(1.0, 2.0, 3.0, 4.0)).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, SLEW_CTRL_PARAMS=(1.0,2.0,3.0,4.0)"

    // One actuator with trkParams
    setup = toCfgActVc(prefix, Set(3)).withTrkParams(Array(1.0, 2.0, 3.0, 4.0)).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=(3), TRK_CTRL_PARAMS=(1.0,2.0,3.0,4.0)"

    // All actuator with all
    setup = toCfgActVc(prefix, Set(1, 2, 3))
      .withTrkParams(Array(1.0, 2.0, 3.0, 4.0))
      .withLoopMode(ON)
      .withSlewParams(Array(5.0, 6.0, 7.0, 8.0))
      .asSetup
    toCommand(
      setup
    ) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MODE=ON, SLEW_CTRL_PARAMS=(5.0,6.0,7.0,8.0), TRK_CTRL_PARAMS=(1.0,2.0,3.0,4.0)"

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
      toCfgActVc(prefix, ALL_ACTUATORS).withSlewParams(Array(1.0, 2.0, 3.0))
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
    import m1cs.segments.segcommands.CFG_ACT_OFFLD.*
    import m1cs.segments.segcommands.Common.ControllerModes.*

    // All 3 actuators
    var setup = toActOffldCtrl(prefix, ALL_ACTUATORS).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // All actuators + offload mode
    setup = toActOffldCtrl(prefix, ALL_ACTUATORS).withOffloadMode(CONTINUOUS).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MODE=CONTINUOUS"

    // All actuators + gain
    setup = toActOffldCtrl(prefix, ALL_ACTUATORS).withGain(5).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, CONT_GAIN=5.0"

    // All actuators + thresh
    setup = toActOffldCtrl(prefix, ALL_ACTUATORS).withThresh(22.3).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, CONT_THRESH=22.3"

    // All actuators + discreteStart
    setup = toActOffldCtrl(prefix, ALL_ACTUATORS).withDiscreteStart(101.23).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, DISCRETE_START=101.23"

    // All actuators + discreteEnd
    setup = toActOffldCtrl(prefix, ALL_ACTUATORS).withDiscreteStop(-101.23).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, DISCRETE_STOP=-101.23"

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
    ) shouldBe s"${COMMAND_NAME.name} ACT_ID=(1), MODE=CONTINUOUS, CONT_GAIN=5.0, CONT_THRESH=22.3, DISCRETE_START=101.23, DISCRETE_STOP=-101.23"

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

  test("To From CFG_ACT_SNUB") {
    import m1cs.segments.segcommands.CFG_ACT_SNUB.*
    import m1cs.segments.segcommands.Common.ControllerModes.*

    // All 3 actuators
    var setup = toActSnubCtrl(prefix, ALL_ACTUATORS).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // All actuators + offload mode
    setup = toActSnubCtrl(prefix, ALL_ACTUATORS).withSnubberMode(CONTINUOUS).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MODE=CONTINUOUS"

    // All actuators + gain
    setup = toActSnubCtrl(prefix, ALL_ACTUATORS).withGain(5).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, CONT_GAIN=5.0"

    // All actuators + thresh
    setup = toActSnubCtrl(prefix, ALL_ACTUATORS).withThresh(22.3).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, CONT_THRESH=22.3"

    // All actuators + discreteStart
    setup = toActSnubCtrl(prefix, ALL_ACTUATORS).withDiscreteStart(101.23).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, DISCRETE_START=101.23"

    // All actuators + discreteEnd
    setup = toActSnubCtrl(prefix, ALL_ACTUATORS).withDiscreteStop(-101.23).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, DISCRETE_STOP=-101.23"

    // Some actuators + all options
    setup = toActSnubCtrl(prefix, Set(1))
      .withSnubberMode(CONTINUOUS)
      .withGain(5)
      .withThresh(22.3)
      .withDiscreteStart(101.23)
      .withDiscreteStop(-101.23)
      .asSetup
    toCommand(
      setup
    ) shouldBe s"${COMMAND_NAME.name} ACT_ID=(1), MODE=CONTINUOUS, CONT_GAIN=5.0, CONT_THRESH=22.3, DISCRETE_START=101.23, DISCRETE_STOP=-101.23"

    // Check for too big set
    assertThrows[IllegalArgumentException] {
      toActSnubCtrl(prefix, Set(1, 2, 3, 4))
    }

    // Check for empty set
    assertThrows[IllegalArgumentException] {
      toActSnubCtrl(prefix, Set())
    }

    // Check for bad number of ctrlParams
    assertThrows[IllegalArgumentException] {
      toActSnubCtrl(prefix, Set(2, 1, 3, 5))
    }

    // Check for out of range ID
    assertThrows[IllegalArgumentException] {
      toActSnubCtrl(prefix, Set(2, 1, 4))
    }
  }

  test("To From SET_LIMIT_ACT") {
    import m1cs.segments.segcommands.SET_LIMIT_ACT.*
    import m1cs.segments.segcommands.SET_LIMIT_ACT.PositionSensors.*

    // All 3 actuators
    var setup = toActPosLimit(prefix, ALL_ACTUATORS, ENCODER).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, SENSOR=ENCODER"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // All actuators + positionSenor
    setup = toActPosLimit(prefix, ALL_ACTUATORS, ENCODER).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, SENSOR=ENCODER"

    // All actuators + upper limit
    setup = toActPosLimit(prefix, ALL_ACTUATORS, ENCODER).withUpperLimit(5).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, SENSOR=ENCODER, POS_LIM=5.0"

    // All actuators + lower limit
    setup = toActPosLimit(prefix, ALL_ACTUATORS, SNUB).withLowerLimit(22.3).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, SENSOR=SNUB, NEG_LIM=22.3"

    // Some actuators + all options
    setup = toActPosLimit(prefix, Set(1), SNUB)
      .withUpperLimit(50)
      .withLowerLimit(22.3)
      .asSetup
    toCommand(
      setup
    ) shouldBe s"${COMMAND_NAME.name} ACT_ID=(1), SENSOR=SNUB, POS_LIM=50.0, NEG_LIM=22.3"

    // Check for too big set
    assertThrows[IllegalArgumentException] {
      toActPosLimit(prefix, Set(1, 2, 3, 4), SNUB)
    }

    // Check for empty set
    assertThrows[IllegalArgumentException] {
      toActPosLimit(prefix, Set(), SNUB)
    }

    // Check for bad number of ctrlParams
    assertThrows[IllegalArgumentException] {
      toActPosLimit(prefix, Set(2, 1, 3, 5), SNUB)
    }

    // Check for out of range ID
    assertThrows[IllegalArgumentException] {
      toActPosLimit(prefix, Set(2, 1, 4), SNUB)
    }
  }

  test("To From SET_PARAM_ACT") {
    import m1cs.segments.segcommands.SET_PARAM_ACT.*
    import m1cs.segments.segcommands.SET_PARAM_ACT.Motors.*

    // All 3 actuators
    var setup = toActMotorCharacteristics(prefix, ALL_ACTUATORS, VC).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MOTOR=VC"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // All actuators + positionSenor
    setup = toActMotorCharacteristics(prefix, ALL_ACTUATORS, VC).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MOTOR=VC"

    // All actuators + coil
    setup = toActMotorCharacteristics(prefix, ALL_ACTUATORS, VC).withCoilResistance(5).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MOTOR=VC, COIL_RESISTANCE=5.0"

    // All actuators + operating
    setup = toActMotorCharacteristics(prefix, ALL_ACTUATORS, SNUB).withOperatingCurrent(22.3).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MOTOR=SNUB, OPER_CUR=22.3"

    // All actuators + standby
    setup = toActMotorCharacteristics(prefix, ALL_ACTUATORS, SNUB).withStandbyCurrent(13.8).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} ACT_ID=ALL, MOTOR=SNUB, STBY_CUR=13.8"

    // Some actuators + all options
    setup = toActMotorCharacteristics(prefix, Set(1), SNUB)
      .withCoilResistance(5)
      .withOperatingCurrent(22.3)
      .withStandbyCurrent(13.8)
      .asSetup
    toCommand(
      setup
    ) shouldBe s"${COMMAND_NAME.name} ACT_ID=(1), MOTOR=SNUB, COIL_RESISTANCE=5.0, OPER_CUR=22.3, STBY_CUR=13.8"

    // Check for too big set
    assertThrows[IllegalArgumentException] {
      toActMotorCharacteristics(prefix, Set(1, 2, 3, 4), SNUB)
    }

    // Check for empty set
    assertThrows[IllegalArgumentException] {
      toActMotorCharacteristics(prefix, Set(), SNUB)
    }

    // Check for bad number of ctrlParams
    assertThrows[IllegalArgumentException] {
      toActMotorCharacteristics(prefix, Set(2, 1, 3, 5), SNUB)
    }

    // Check for out of range ID
    assertThrows[IllegalArgumentException] {
      toActMotorCharacteristics(prefix, Set(2, 1, 4), SNUB)
    }
  }

  /** -- Missing  commands -- no documentation on command */

  test("To From CAL_WH_DEADBANDWH") {
    import CAL_WH_DEADBANDWH.*

    // All 3 actuators
    var setup = toCalibrateWarpingHarnessDeadband(prefix, 2).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} CAL_WH_DEADBANDWH_ID=2"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // Some actuators + all options
    setup = toCalibrateWarpingHarnessDeadband(prefix).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} CAL_WH_DEADBANDWH_ID=ALL"

    // Check that out of range warping harness throws
    assertThrows[IllegalArgumentException] {
      toCalibrateWarpingHarnessDeadband(prefix, 22)
    }
  }

  test("To From MOVE_WH") {
    import MOVE_WH.*
    import MOVE_WH.MoveTypes.*
    import MOVE_WH.Torques.*
    import MOVE_WH.Boosts.*

    val testStrains: Array[Double] = Array(1, 2, 3, 4, 5, 6, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)
    val strainString               = valuesToString(testStrains)

    // Test with specific harness
    var setup = toMoveWarpingHarness(prefix, 2, ABSOLUTE, testStrains).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} WH_ID=2, TYPE=ABS, STRAIN=$strainString"
    // Test with all
    setup = toMoveWarpingHarness(prefix, RELATIVE, testStrains).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} WH_ID=ALL, TYPE=REL, STRAIN=$strainString"

    // Check that there is a segmentID or range
    (setup.exists(segmentIdKey) || setup.exists(segmentRangeKey)) shouldBe true

    // with optional torque
    setup = toMoveWarpingHarness(prefix, 2, ABSOLUTE, testStrains).withTorque(T75).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} WH_ID=2, TYPE=ABS, STRAIN=$strainString, TORQUE=75"
    // with optional boost
    setup = toMoveWarpingHarness(prefix, 2, ABSOLUTE, testStrains).withBoost(ON).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} WH_ID=2, TYPE=ABS, STRAIN=$strainString, BOOST=ON"
    // with both boost and torque
    setup = toMoveWarpingHarness(prefix, 2, ABSOLUTE, testStrains).withBoost(ON).withTorque(T100).asSetup
    toCommand(setup) shouldBe s"${COMMAND_NAME.name} WH_ID=2, TYPE=ABS, STRAIN=$strainString, TORQUE=100, BOOST=ON"

    // Test that poorly sized Strain array throws
    // Check for out of range ID
    assertThrows[IllegalArgumentException] {
      toMoveWarpingHarness(prefix, 2, ABSOLUTE, Array[Double](1, 2, 3))
    }

    // Check that out of range warping harness throws
    assertThrows[IllegalArgumentException] {
      toMoveWarpingHarness(prefix, 22, ABSOLUTE, testStrains)
    }
  }
}
