package m1cs.segments.support.segcommands

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.KeyType.ChoiceKey
import csw.params.core.generics.{GChoiceKey, Key, KeyType}
import csw.params.core.models.{Choice, Choices}
import csw.prefix.models.Prefix

//noinspection DuplicatedCode
object CFG_CUR_LOOP {
  import m1cs.segments.support.segcommands.Common.*

  val COMMAND_NAME: CommandName = CommandName("CFG_CUR_LOOP")

  val MIN_BUS_VOLTAGE = 0
  val MAX_BUS_VOLTAGE = 24

  object CfgCurLoopMotor extends Enumeration {
    type MotorChan = Value

    val VC: Value    = Value(1, "VC")
    val OFFLD: Value = Value(2, "OFFLD")
    val SNUB: Value  = Value(3, "SNUB")
    val ALL: Value   = Value(4, "ALL")
  }
  import CfgCurLoopMotor.*
  val cfgCurLoopMotorChoices: Choices = Choices.from(VC.toString, OFFLD.toString, SNUB.toString, ALL.toString)
  val cfgCurLoopMotorKey: GChoiceKey  = ChoiceKey.make("MOTOR", cfgCurLoopMotorChoices)

  val busVoltageKey: Key[Float] = KeyType.FloatKey.make("BUS_VOLTAGE")
  val ctrlParamsKey: Key[Float] = KeyType.FloatKey.make("CTRL_PARAMS")

  case class toCfgActCurLoop(prefix: Prefix, actId: Set[Int], motorChan: MotorChan)
      extends BaseCommand[toCfgActCurLoop](prefix, COMMAND_NAME) {

    setup = addActuators(setup, actId)

    // Add the mandatory params
    setup = setup.madd(
      actuatorIdKey.setAll(actId.toArray),
      cfgCurLoopMotorKey.set(Choice(motorChan.toString))
    )

    def withLoopMode(loopMode: CfgLoopModes.CfgLoopMode): toCfgActCurLoop = {
      setup = setup.add(cfgLoopModeKey.set(Choice(loopMode.toString)))
      this
    }

    def withBusVoltage(busVoltage: Double): toCfgActCurLoop = {
      // Require bus voltage to be less than 24 (TBD)
      require(busVoltage <= 24.0 && busVoltage >= 0.0, "Supply voltage to motor driver bridge chip range is: 0.0 <= V <= 24.0")
      setup = setup.add(busVoltageKey.set(busVoltage.toFloat))
      this
    }

    def withCtrlParams(ctrlParams: Array[Double]): toCfgActCurLoop = {
      // Check that the size of the ctrlParams is 3
      require(ctrlParams.length == 3, "There must be 3 ctrlParam values for Kp, Ki, Kd")
      setup = setup.add(ctrlParamsKey.setAll(ctrlParams.map(_.toFloat)))
      this
    }

    // Make a copy -- do any checks here
    override def asSetup: Setup = {
      val mode       = setup.get(cfgLoopModeKey)
      val busVoltage = setup.get(busVoltageKey)
      val ctrlParams = setup.get(ctrlParamsKey)

      // Check that there is at least one
      require(
        mode.isDefined || busVoltage.isDefined || ctrlParams.isDefined,
        "CfgCurLoop must have one or more of: mode, bus voltage, or ctrlParams."
      )

      // Should require a segment set
      Setup(setup.source, setup.commandName, setup.maybeObsId, setup.paramSet)
    }
  }

  /**
   * Returns a formatted CFG_CUR_LOOP command from a Setup
   *
   * @param setup Setup created with toActuator
   * @return String command ready to send
   */
  def toCommand(setup: Setup): String = {
    require(setup.commandName == COMMAND_NAME, s"The provided Setup is not a: $COMMAND_NAME")
    val actId    = setup(actuatorIdKey)
    val actIdVal = if (actId.size == 3) "ALL" else valuesToString(actId.values)

    val modeExists       = setup.exists(cfgLoopModeKey)
    val busVoltageExists = setup.exists(busVoltageKey)
    val ctrlParamsExists = setup.exists(ctrlParamsKey)

    val sb = new StringBuilder(s"${setup.commandName.name} ACT_ID=$actIdVal, MOTOR=${setup(cfgCurLoopMotorKey).head.name}")
    if (modeExists) sb ++= s", MODE=${setup(cfgLoopModeKey).head.name}"
    if (busVoltageExists) sb ++= s", BUS_VOLTAGE=${setup(busVoltageKey).head}"
    if (ctrlParamsExists) sb ++= s", CTRL_PARAMS=${valuesToString(setup(ctrlParamsKey).values)}"
    sb.result()
  }
}
