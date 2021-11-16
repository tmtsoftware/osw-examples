package m1cs.segments.segcommands

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{GChoiceKey, Key, KeyType}
import csw.params.core.generics.KeyType.ChoiceKey
import csw.params.core.models.{Choice, Choices}
import csw.prefix.models.Prefix

object SET_LIMIT_ACT {
  import Common.*

  val COMMAND_NAME: CommandName = CommandName("SET_LIMIT_ACT")

  object PositionSensors extends Enumeration {
    type PositionSensor = Value

    val ENCODER: Value = Value(1, "ENCODER")
    val OFFLD: Value   = Value(2, "OFFLD")
    val SNUB: Value    = Value(3, "SNUB")
  }

  import PositionSensors.*

  val positionSensorChoices: Choices = Choices.from(ENCODER.toString, OFFLD.toString, SNUB.toString)
  val positionSensorKey: GChoiceKey  = ChoiceKey.make("SENSOR", positionSensorChoices)
  val upperLimitKey: Key[Float]      = KeyType.FloatKey.make("POS_LIM")
  val lowerLimitKey: Key[Float]      = KeyType.FloatKey.make("NEG_LIM")

  case class toActPosLimit(prefix: Prefix, actId: Set[Int], positionSensor: PositionSensor)
      extends BaseCommand[toActPosLimit](prefix, COMMAND_NAME) {

    setup = addActuators(setup, actId)
    setup = setup.add(positionSensorKey.set(Choice(positionSensor.toString)))

    def withUpperLimit(upperLimit: Double): toActPosLimit = {
      setup = setup.add(upperLimitKey.set(upperLimit.toFloat))
      this
    }

    def withLowerLimit(lowerLimit: Double): toActPosLimit = {
      setup = setup.add(lowerLimitKey.set(lowerLimit.toFloat))
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

    val upperExists = setup.exists(upperLimitKey)
    val lowerExists = setup.exists(lowerLimitKey)

    val sb = new StringBuilder(s"${setup.commandName.name} ACT_ID=$actIdVal")
    sb ++= s", SENSOR=${setup(positionSensorKey).head.name}"
    if (upperExists) sb ++= s", POS_LIM=${setup(upperLimitKey).head}"
    if (lowerExists) sb ++= s", NEG_LIM=${setup(lowerLimitKey).head}"
    sb.result()
  }
}
