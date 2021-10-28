package m1cs.segments.support.segcommands

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.KeyType.ChoiceKey
import csw.params.core.generics.{GChoiceKey, Key, KeyType}
import csw.params.core.models.{Choice, Choices}
import csw.prefix.models.Prefix

//noinspection DuplicatedCode
object CFG_ACT_OFFLD {
  import m1cs.segments.support.segcommands.Common.*
  import m1cs.segments.support.segcommands.Common.ControllerModes.*

  val COMMAND_NAME: CommandName     = CommandName("CFG_ACT_OFFLD")
  val offloaderModeChoices: Choices = Choices.from(OFF.toString, CONTINUOUS.toString, DISCRETE.toString)
  val offloaderModeKey: GChoiceKey  = ChoiceKey.make("MODE", offloaderModeChoices)

  val contGainKey: Key[Float]      = KeyType.FloatKey.make("CONT_GAIN")
  val contThreshKey: Key[Float]    = KeyType.FloatKey.make("CONT_THRESH")
  val discreteStartKey: Key[Float] = KeyType.FloatKey.make("DISCRETE_START")
  val discreteStopKey: Key[Float]  = KeyType.FloatKey.make("DISCRETE_STOP")

  case class toActOffldCtrl(prefix: Prefix, actId: Set[Int]) extends BaseCommand[toActOffldCtrl](prefix, COMMAND_NAME) {

    setup = addActuators(setup, actId)

    def withOffloadMode(loopMode: ControllerModes.ControllerMode): toActOffldCtrl = {
      setup = setup.add(offloaderModeKey.set(Choice(loopMode.toString)))
      this
    }

    def withGain(contGain: Double): toActOffldCtrl = {
      setup = setup.add(contGainKey.set(contGain.toFloat))
      this
    }

    def withThresh(contThresh: Double): toActOffldCtrl = {
      setup = setup.add(contThreshKey.set(contThresh.toFloat))
      this
    }

    def withDiscreteStart(discreteStart: Double): toActOffldCtrl = {
      setup = setup.add(discreteStartKey.set(discreteStart.toFloat))
      this
    }

    def withDiscreteStop(discreteStop: Double): toActOffldCtrl = {
      setup = setup.add(discreteStopKey.set(discreteStop.toFloat))
      this
    }

    // Make a copy -- do any checks here
    override def asSetup: Setup = {
      // Check that there is at least one
      //require( )

      // This causes a copy
      Setup(setup.source, setup.commandName, setup.maybeObsId, setup.paramSet)
    }
  }

  /**
   * Returns a formatted CFG_ACT_OFFLD command from a Setup
   *
   * @param setup Setup created with toActuator
   * @return String command ready to send
   */
  def toCommand(setup: Setup): String = {
    require(setup.commandName == COMMAND_NAME, s"The provided Setup is not a: $COMMAND_NAME")
    val actId    = setup(actuatorIdKey)
    val actIdVal = if (actId.size == 3) "ALL" else valuesToString(actId.values)

    val modeExists     = setup.exists(offloaderModeKey)
    val contGainExists = setup.exists(contGainKey)
    val threshExists   = setup.exists(contThreshKey)
    val startExists    = setup.exists(discreteStartKey)
    val stopExists     = setup.exists(discreteStopKey)

    val sb = new StringBuilder(s"${setup.commandName.name} ACT_ID=$actIdVal")
    if (modeExists) sb ++= s", MODE=${setup(offloaderModeKey).head.name}"
    if (contGainExists) sb ++= s", CONT_GAIN=${setup(contGainKey).head}"
    if (threshExists) sb ++= s", CONT_THRESH=${setup(contThreshKey).head}"
    if (startExists) sb ++= s", DISCRETE_START=${setup(discreteStartKey).head}"
    if (stopExists) sb ++= s", DISCRETE_STOP=${setup(discreteStopKey).head}"
    sb.result()
  }
}
