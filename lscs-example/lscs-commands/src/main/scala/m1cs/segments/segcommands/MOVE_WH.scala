package m1cs.segments.segcommands

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{GChoiceKey, Key, KeyType}
import csw.params.core.generics.KeyType.ChoiceKey
import csw.params.core.models.{Choice, Choices}
import csw.prefix.models.Prefix

object MOVE_WH {
  import Common.*

  val COMMAND_NAME: CommandName = CommandName("MOVE_WH")

  object MoveTypes extends Enumeration {
    type MoveType = Value

    val ABSOLUTE: Value = Value(1, "ABS")
    val RELATIVE: Value = Value(2, "REL")
  }
  import MoveTypes.*
  val moveTypeChoices: Choices     = Choices.from(ABSOLUTE.toString, RELATIVE.toString)
  val moveTypeKey: GChoiceKey      = ChoiceKey.make("TYPE", moveTypeChoices)
  val desiredStrainKey: Key[Float] = KeyType.FloatKey.make("STRAIN")

  object Torques extends Enumeration {
    type Torque = Value

    val T50: Value  = Value(1, "50")
    val T75: Value  = Value(2, "75")
    val T100: Value = Value(3, "100")
  }
  import Torques.*
  val torqueChoices: Choices = Choices.from(T50.toString, T75.toString, T100.toString)
  val torqueKey: GChoiceKey  = ChoiceKey.make("TORQUE", torqueChoices)

  object Boosts extends Enumeration {
    type Boost = Value

    val ON: Value  = Value(1, "ON")
    val OFF: Value = Value(2, "OFF")
  }
  import Boosts.*
  val boostChoices: Choices = Choices.from(Boosts.ON.toString, Boosts.OFF.toString)
  val boostKey: GChoiceKey  = ChoiceKey.make("BOOST", boostChoices)

  private[MOVE_WH] case class toMoveWarpingHarness(
      prefix: Prefix,
      warpingHarness: String,
      moveType: MoveType,
      desiredStrain: Array[Double]
  ) extends BaseCommand[toMoveWarpingHarness](prefix, COMMAND_NAME) {
    setup = setup.add(warpingHarnessIdKey.set(warpingHarness))
    setup = setup.add(moveTypeKey.set(Choice(moveType.toString)))

    require(desiredStrain.length.equals(MAX_HARNESS), s"The array of strain values must be length $MAX_HARNESS")

    setup = setup.add(desiredStrainKey.setAll(desiredStrain.map(_.toFloat)))

    def withTorque(torque: Torque): toMoveWarpingHarness = {
      setup = setup.add(torqueKey.set(Choice(torque.toString)))
      this
    }

    def withBoost(boost: Boost): toMoveWarpingHarness = {
      setup = setup.add(boostKey.set(Choice(boost.toString)))
      this
    }

    // Make a copy -- do any checks here
    override def asSetup: Setup = {
      // Should require a segment set
      Setup(setup.source, setup.commandName, setup.maybeObsId, setup.paramSet)
    }
  }

  // These are the constructors for the commands that have a warping harness ID or ALL
  object toMoveWarpingHarness {
    // This constructor takes an int as a harnessID
    def apply(prefix: Prefix, warpingHarnessId: Int, moveType: MoveType, desiredStrain: Array[Double]): toMoveWarpingHarness = {
      val s = checkWarpingHarnessId(warpingHarnessId)
      toMoveWarpingHarness(prefix, s, moveType, desiredStrain)
    }

    // Without the ID means ALL
    def apply(prefix: Prefix, moveType: MoveType, desiredStrain: Array[Double]): toMoveWarpingHarness = {
      toMoveWarpingHarness(prefix, ALL_HARNESS, moveType, desiredStrain)
    }
  }

  /**
   * Returns a formatted MOVE_WH command from a Setup
   *
   * @param setup Setup created with toMoveWarpingHarness
   * @return String command ready to send
   */
  def toCommand(setup: Setup): String = {
    require(setup.commandName == COMMAND_NAME, s"The provided Setup is not a: $COMMAND_NAME")

    require(setup.exists(warpingHarnessIdKey), "Setup must have a warping harness ID.")
    require(setup.exists(moveTypeKey), "Setup must contain a moveType value.")

    val torqueExists = setup.exists(torqueKey)
    val boostExists  = setup.exists(boostKey)

    val sb = new StringBuilder(
      s"${setup.commandName.name} WH_ID=${setup(warpingHarnessIdKey).head}, TYPE=${setup(moveTypeKey).head.name}"
    )
    sb ++= s", STRAIN=${valuesToString(setup(desiredStrainKey).values)}"

    if (torqueExists) sb ++= s", TORQUE=${setup(torqueKey).head.name}"
    if (boostExists) sb ++= s", BOOST=${setup(boostKey).head}"

    sb.result()
  }
}
