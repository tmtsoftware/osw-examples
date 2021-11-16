package m1cs.segments.segcommands

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.KeyType.ChoiceKey
import csw.params.core.generics.{GChoiceKey, Key, KeyType}
import csw.params.core.models.Choices
import csw.prefix.models.Prefix

object Common {
  // Used when identifying a single segment for a command - may be [A-F][1-82] or ALL
  val segmentIdKey: Key[String] = KeyType.StringKey.make("SegmentId")
  val ALL_SEGMENTS              = "ALL"

  // Used to provide one or more ranges to receive a command
  val segmentRangeKey: Key[String] = KeyType.StringKey.make("SegmentRanges")
  // Shared keys
  val actuatorIdKey: Key[Int] = KeyType.IntKey.make("ACT_ID")
  // Shortcut for full Set
  val ALL_ACTUATORS = Set(1, 2, 3)

  // Use by commands that have actuators to check for basic consistency
  def addActuators(setup: Setup, actId: Set[Int]): Setup = {
    require(actId.size <= 3 && actId.nonEmpty, "Actuator ID Set must have three members or less but must not be empty.")
    // Check that they are within range
    require(actId.max <= 3 && actId.min >= 1, "Actuator ID values must be: 1, 2, or 3")

    setup.add(actuatorIdKey.setAll(actId.toArray))
  }

  // Constants for warping harness commands
  val HARNESS_RANGE                    = 1 to 21
  val ALL_HARNESS: String              = "ALL"
  val MIN_HARNESS: Int                 = HARNESS_RANGE.min
  val MAX_HARNESS: Int                 = HARNESS_RANGE.max
  val warpingHarnessIdKey: Key[String] = KeyType.StringKey.make("warpingHarnessId")

  // These verifies that a warping harness is within range
  def checkWarpingHarnessId(warpingHarnessId: Int): String = {
    require(HARNESS_RANGE.contains(warpingHarnessId), "Warping harness ID must be between 1 and 21 inclusive.")
    warpingHarnessId.toString
  }

  //#command-support
  /**
   * This map is used by the Assembly to access the correct toCommand for an incoming command Setup
   * It maps command name to a function that returns the formatted command
   */
  val CommandMap: Map[CommandName, Setup => String] = Map(
    ACTUATOR.COMMAND_NAME          -> ACTUATOR.toCommand,
    TARG_GEN_ACT.COMMAND_NAME      -> TARG_GEN_ACT.toCommand,
    CFG_CUR_LOOP.COMMAND_NAME      -> CFG_CUR_LOOP.toCommand,
    CFG_ACT_VC.COMMAND_NAME        -> CFG_ACT_VC.toCommand,
    CFG_ACT_OFFLD.COMMAND_NAME     -> CFG_ACT_OFFLD.toCommand,
    CFG_ACT_SNUB.COMMAND_NAME      -> CFG_ACT_SNUB.toCommand,
    SET_LIMIT_ACT.COMMAND_NAME     -> SET_LIMIT_ACT.toCommand,
    SET_PARAM_ACT.COMMAND_NAME     -> SET_PARAM_ACT.toCommand,
    CAL_WH_DEADBANDWH.COMMAND_NAME -> CAL_WH_DEADBANDWH.toCommand,
    MOVE_WH.COMMAND_NAME           -> MOVE_WH.toCommand
  )

  // This is used by validation of Assembly and HCD to verify that the received command is currently supported. Could
  // be removed when all commands are supported
  val ALL_COMMANDS: List[CommandName] = CommandMap.keys.toList
  //#command-support

  // Used by every command that has an array of values to print in the correct format x=(1,2,3)
  def valuesToString[A](items: Array[A]): String = items.mkString("(", ",", ")")

  // Used by multiple commands
  object CfgLoopModes extends Enumeration {
    type CfgLoopMode = Value

    val OFF: Value  = Value(1, "OFF")
    val ON: Value   = Value(2, "ON")
    val OPEN: Value = Value(3, "OPEN")
  }
  val cfgLoopModeChoices: Choices =
    Choices.from(CfgLoopModes.OFF.toString, CfgLoopModes.ON.toString, CfgLoopModes.OPEN.toString)
  val cfgLoopModeKey: GChoiceKey = ChoiceKey.make("MODE", cfgLoopModeChoices)

  // Used by multiple commands
  object ControllerModes extends Enumeration {
    type ControllerMode = Value

    val OFF: Value        = Value(1, "OFF")
    val CONTINUOUS: Value = Value(2, "CONTINUOUS")
    val DISCRETE: Value   = Value(3, "DISCRETE")
  }

  // Trait describes base class of all commands
  private[segcommands] trait LscsCommand[T] {
    def toSegment(segmentId: SegmentId): T
    def toAll: T
    def asSetup: Setup
  }

  private[segcommands] class BaseCommand[T](prefix: Prefix, commandName: CommandName) extends LscsCommand[T] {

    protected var setup: Setup = Setup(prefix, commandName, None).add(segmentIdKey.set(ALL_SEGMENTS))

    def toSegment(segmentId: SegmentId): T = {
      setup = setup.add(segmentIdKey.set(segmentId.toString))
      this.asInstanceOf[T]
    }

    def toAll: T = {
      setup = setup.add(segmentIdKey.set(ALL_SEGMENTS))
      this.asInstanceOf[T]
    }

    def asSetup: Setup = {
      // Should require a segment set
      Setup(setup.source, setup.commandName, setup.maybeObsId, setup.paramSet)
    }
  }
}
