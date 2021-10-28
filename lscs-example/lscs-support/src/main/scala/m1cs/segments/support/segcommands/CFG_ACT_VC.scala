package m1cs.segments.support.segcommands

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Choice
import csw.prefix.models.Prefix

//noinspection DuplicatedCode
object CFG_ACT_VC {
  import m1cs.segments.support.segcommands.Common.*

  val COMMAND_NAME: CommandName = CommandName("CFG_ACT_VC")

  val slewCtrlParamsKey: Key[Float] = KeyType.FloatKey.make("SLEW_CTRL_PARAMS")
  val trkCtrlParamsKey: Key[Float]  = KeyType.FloatKey.make("TRK_CTRL_PARAMS")

  case class toCfgActVc(prefix: Prefix, actId: Set[Int]) extends BaseCommand[toCfgActVc](prefix, COMMAND_NAME) {

    setup = addActuators(setup, actId)

    def withLoopMode(loopMode: CfgLoopModes.CfgLoopMode): toCfgActVc = {
      setup = setup.add(cfgLoopModeKey.set(Choice(loopMode.toString)))
      this
    }

    def withSlewParams(slewCtrlParams: Array[Double]): toCfgActVc = {
      // Check that the size of the slewCtrlParams is 4
      require(slewCtrlParams.length == 4, "There must be 4 slewCtrlParam values for Kp, Ki, Kd, FD.")
      setup = setup.add(slewCtrlParamsKey.setAll(slewCtrlParams.map(_.toFloat)))
      this
    }

    def withTrkParams(trackParams: Array[Double]): toCfgActVc = {
      // Check that the size of the trackParams is 4
      require(trackParams.length == 4, "There must be 4 trackParam values for Kp, Ki, Kd, FD")
      setup = setup.add(trkCtrlParamsKey.setAll(trackParams.map(_.toFloat)))
      this
    }

    // Make a copy -- do any checks here
    override def asSetup: Setup = {
      val modeExists = setup.exists(cfgLoopModeKey)
      val slewExists = setup.exists(slewCtrlParamsKey)
      val trkExists  = setup.exists(trkCtrlParamsKey)
      // Check that there is at least one
      require(modeExists || slewExists || trkExists, "CfgActVc must have one or more of: mode, slewParams, or trkParams.")

      // Should require a segment set
      Setup(setup.source, setup.commandName, setup.maybeObsId, setup.paramSet)
    }
  }

  /**
   * Returns a formatted CFG_ACT_VC command from a Setup
   *
   * @param setup Setup created with toActuator
   * @return String command ready to send
   */
  def toCommand(setup: Setup): String = {
    require(setup.commandName == COMMAND_NAME, s"The provided Setup is not a: $COMMAND_NAME")

    val actId    = setup(actuatorIdKey)
    val actIdVal = if (actId.size == 3) "ALL" else valuesToString(actId.values)

    val modeExists = setup.exists(cfgLoopModeKey)
    val slewExists = setup.exists(slewCtrlParamsKey)
    val trkExists  = setup.exists(trkCtrlParamsKey)

    val sb = new StringBuilder(s"${setup.commandName.name} ACT_ID=$actIdVal")
    if (modeExists) sb ++= s", MODE=${setup(cfgLoopModeKey).head.name}"
    if (slewExists) sb ++= s", SLEW_CTRL_PARAMS=${valuesToString(setup(slewCtrlParamsKey).values)}"
    if (trkExists) sb ++= s", TRK_CTRL_PARAMS=${valuesToString(setup(trkCtrlParamsKey).values)}"
    sb.result()
  }
}
