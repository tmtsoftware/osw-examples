package m1cs.segments.segcommands

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.KeyType.ChoiceKey
import csw.params.core.generics.{GChoiceKey, Key, KeyType}
import csw.params.core.models.{Choice, Choices}
import csw.prefix.models.Prefix

//noinspection DuplicatedCode
//#example-command
object ACTUATOR {
  import Common.*

  // ACTUATOR command
  val COMMAND_NAME: CommandName = CommandName("ACTUATOR")

  object ActuatorModes extends Enumeration {
    type ActuatorMode = Value

    val OFF: Value       = Value(1, "OFF")
    val TRACK: Value     = Value(2, "TRACK")
    val SLEW: Value      = Value(3, "SLEW")
    val CALIBRATE: Value = Value(4, "CALIBRATE")
  }

  import ActuatorModes.*

  val actuatorChoices: Choices    = Choices.from(OFF.toString, TRACK.toString, SLEW.toString, CALIBRATE.toString)
  val actuatorModeKey: GChoiceKey = ChoiceKey.make("MODE", actuatorChoices)
  val targetKey: Key[Float]       = KeyType.FloatKey.make("TARGET")

  case class toActuator(prefix: Prefix, actId: Set[Int]) extends BaseCommand[toActuator](prefix, COMMAND_NAME) {

    setup = Common.addActuators(setup, actId)

    def withMode(mode: ActuatorMode): toActuator = {
      setup = setup.add(actuatorModeKey.set(Choice(mode.toString)))
      this
    }

    def withTarget(target: Double): toActuator = {
      setup = setup.add(targetKey.set(target.toFloat))
      this
    }

    // Make a copy -- do any checks here
    override def asSetup: Setup = {
      val mode   = setup.get(actuatorModeKey)
      val target = setup.get(targetKey)

      // Check that there is at least one
      require(mode.isDefined || target.isDefined, "Actuator must have either a mode or target or both.")

      // Should require a segment set
      Setup(setup.source, setup.commandName, setup.maybeObsId, setup.paramSet)
    }
  }

  /**
   * Returns a formatted ACTUATOR command from a [Setup]
   *
   * @param setup Setup created with toActuator
   * @return String command ready to send
   */
  def toCommand(setup: Setup): String = {
    require(setup.commandName == COMMAND_NAME, s"The provided Setup is not a: $COMMAND_NAME")
    val actId        = setup(actuatorIdKey)
    val modeExists   = setup.exists(actuatorModeKey)
    val targetExists = setup.exists(targetKey)
    require(targetExists || modeExists, "ACTUATOR requires either a mode or a target or both.")
    val actIdVal = if (actId.size == 3) "ALL" else valuesToString(actId.values)
    val sb       = new StringBuilder(s"${setup.commandName.name} ACT_ID=$actIdVal")
    if (modeExists) sb ++= s", MODE=${setup(actuatorModeKey).head.name}"
    if (targetExists) sb ++= s", TARGET=${setup(targetKey).head}"
    sb.result()
  }
}
//#example-command
