package m1cs.segments.segcommands

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{GChoiceKey, Key, KeyType}
import csw.params.core.generics.KeyType.ChoiceKey
import csw.params.core.models.{Choice, Choices}
import csw.prefix.models.Prefix

object SET_PARAM_ACT {
  import Common.*

  val COMMAND_NAME: CommandName = CommandName("SET_PARAM_ACT")

  object Motors extends Enumeration {
    type Motor = Value

    val VC: Value    = Value(1, "VC")
    val OFFLD: Value = Value(2, "OFFLD")
    val SNUB: Value  = Value(3, "SNUB")
  }

  import Motors.*
  val motorChoices: Choices           = Choices.from(VC.toString, OFFLD.toString, SNUB.toString)
  val motorKey: GChoiceKey            = ChoiceKey.make("MOTOR", motorChoices)
  val coilResistanceKey: Key[Float]   = KeyType.FloatKey.make("COIL_RESISTANCE")
  val operatingCurrentKey: Key[Float] = KeyType.FloatKey.make("OPER_CUR")
  val standbyCurrentKey: Key[Float]   = KeyType.FloatKey.make("STBY_CUR")

  case class toActMotorCharacteristics(prefix: Prefix, actId: Set[Int], motor: Motor)
      extends BaseCommand[toActMotorCharacteristics](prefix, COMMAND_NAME) {

    setup = addActuators(setup, actId)
    setup = setup.add(motorKey.set(Choice(motor.toString)))

    def withCoilResistance(coilResistance: Double): toActMotorCharacteristics = {
      setup = setup.add(coilResistanceKey.set(coilResistance.toFloat))
      this
    }

    def withOperatingCurrent(operatingCurrent: Double): toActMotorCharacteristics = {
      setup = setup.add(operatingCurrentKey.set(operatingCurrent.toFloat))
      this
    }

    def withStandbyCurrent(standbyCurrent: Double): toActMotorCharacteristics = {
      setup = setup.add(standbyCurrentKey.set(standbyCurrent.toFloat))
      this
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

    val coilExists           = setup.exists(coilResistanceKey)
    val opCurrentExists      = setup.exists(operatingCurrentKey)
    val standbyCurrentExists = setup.exists(standbyCurrentKey)

    val sb = new StringBuilder(s"${setup.commandName.name} ACT_ID=$actIdVal")
    sb ++= s", MOTOR=${setup(motorKey).head.name}"
    if (coilExists) sb ++= s", COIL_RESISTANCE=${setup(coilResistanceKey).head}"
    if (opCurrentExists) sb ++= s", OPER_CUR=${setup(operatingCurrentKey).head}"
    if (standbyCurrentExists) sb ++= s", STBY_CUR=${setup(standbyCurrentKey).head}"
    sb.result()
  }

}
