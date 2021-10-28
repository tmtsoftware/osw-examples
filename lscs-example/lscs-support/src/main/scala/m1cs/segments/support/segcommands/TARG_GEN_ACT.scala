package m1cs.segments.support.segcommands

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.KeyType.ChoiceKey
import csw.params.core.generics.{GChoiceKey, Key, KeyType}
import csw.params.core.models.{Choice, Choices}
import csw.prefix.models.Prefix

//noinspection DuplicatedCode
object TARG_GEN_ACT {
  import m1cs.segments.support.segcommands.Common.*

  val COMMAND_NAME = CommandName("TARG_GEN_ACT")

  object TargetGenModes extends Enumeration {
    type TargetGenMode = Value

    val ON: Value  = Value(1, "ON")
    val OFF: Value = Value(2, "OFF")
  }
  import TargetGenModes.*
  val targetGenModeChoices: Choices = Choices.from(ON.toString, OFF.toString)
  val targetGenModeKey: GChoiceKey  = ChoiceKey.make("MODE", targetGenModeChoices)

  object TargetShapes extends Enumeration {
    type TargetShape = Value

    val SQ: Value  = Value(1, "SQ")
    val TRI: Value = Value(2, "TRI")
    val SIN: Value = Value(3, "SIN")
  }
  import TargetShapes.*
  val targetShapeChoices: Choices    = Choices.from(SQ.toString, TRI.toString, SIN.toString)
  val targetShapeKey: GChoiceKey     = ChoiceKey.make("SHAPE", targetShapeChoices)
  val targetAmplitudeKey: Key[Float] = KeyType.FloatKey.make("AMPL")   // no Units.nm yet
  val targetPeriodKey: Key[Float]    = KeyType.FloatKey.make("PERIOD") //
  val targetOffsetKey: Key[Float]    = KeyType.FloatKey.make("OFFSET") // no Units.nm yet

  case class toActTargetGen(prefix: Prefix, actId: Set[Int]) extends BaseCommand[toActTargetGen](prefix, COMMAND_NAME) {

    setup = addActuators(setup, actId)

    def withMode(mode: TargetGenMode): toActTargetGen = {
      setup = setup.add(targetGenModeKey.set(Choice(mode.toString)))
      this
    }

    def withShape(mode: TargetShape): toActTargetGen = {
      setup = setup.add(targetShapeKey.set(Choice(mode.toString)))
      this
    }

    def withAmplitude(targetAmplitude: Double): toActTargetGen = {
      setup = setup.add(targetAmplitudeKey.set(targetAmplitude.toFloat))
      this
    }

    def withPeriod(targetPeriod: Double): toActTargetGen = {
      // Require target period to be greater tan 0
      require(targetPeriod >= 0.0, "The target period must be >= 0")
      setup = setup.add(targetPeriodKey.set(targetPeriod.toFloat))
      this
    }

    def withOffset(targetOffset: Double): toActTargetGen = {
      setup = setup.add(targetOffsetKey.set(targetOffset.toFloat))
      this
    }

    // Make a copy -- do any checks here
    override def asSetup: Setup = {
      //val mode       = s.get(targetGenModeKey)
      //val shape      = s.get(targetShapeKey)
      //val amplitude  = s.get(targetAmplitudeKey)
      //val period     = s.get(targetPeriodKey)
      //val offset     = s.get(targetOffsetKey)

      // Put any checks here - not sure this is true...
      //require(mode.isDefined || shape.isDefined, " must have one or more of: mode, or shape.")

      // Should require a segment set
      Setup(setup.source, setup.commandName, setup.maybeObsId, setup.paramSet)
    }
  }

  /**
   * Returns a formatted TARG_GEN_ACT command from a [Setup]
   *
   * @param setup Setup created with toActTargetGen
   * @return String command ready to send
   */
  def toCommand(setup: Setup): String = {
    require(setup.commandName == COMMAND_NAME, s"The provided Setup is not a: $COMMAND_NAME")

    val actId           = setup(actuatorIdKey)
    val modeExists      = setup.exists(targetGenModeKey)
    val shapeExists     = setup.exists(targetShapeKey)
    val amplitudeExists = setup.exists(targetAmplitudeKey)
    val periodExists    = setup.exists(targetPeriodKey)
    val offsetExists    = setup.exists(targetOffsetKey)

    val actIdVal = if (actId.size == 3) "ALL" else Common.valuesToString(actId.values)
    val sb       = new StringBuilder(s"${setup.commandName.name} ACT_ID=$actIdVal")
    if (modeExists) sb ++= s", MODE=${setup(targetGenModeKey).head.name}"
    if (shapeExists) sb ++= s", TYPE=${setup(targetShapeKey).head.name}"
    if (amplitudeExists) sb ++= s", AMPL=${setup(targetAmplitudeKey).head}"
    if (periodExists) sb ++= s", PERIOD=${setup(targetPeriodKey).head}"
    if (offsetExists) sb ++= s", OFFSET=${setup(targetOffsetKey).head}"
    sb.result()
  }
}
