package m1cs.segments.shared

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.KeyType.ChoiceKey
import csw.params.core.generics.{GChoiceKey, Key, KeyType}
import csw.params.core.models.{Choice, Choices}
import csw.prefix.models.Prefix

object SegmentCommands {

  // Used when identifying a single segment for a command - may be 1 - a22 or ALL
  val segmentIdKey: Key[String] = KeyType.StringKey.make("SegmentId")
  val ALL_SEGMENTS              = "ALL"

  // Used to provide one or more ranges to receive a command
  val segmentRangeKey: Key[String] = KeyType.StringKey.make("SegmentRanges")
  // Shared keys
  val actuatorIdKey: Key[Int] = KeyType.IntKey.make("ACT_ID")
  // Used by every command
  private def valuesToString[A](items: Array[A]): String = items.mkString("(", ",", ")")

  val AllActuators = Set(1, 2, 3)

  // The following is awful, but I can't yet see a more elegant way
  private val ACTUATOR_COMMAND_NAME      = CommandName("ACTUATOR")
  private val TARG_GEN_ACT_COMMAND_NAME  = CommandName("TARG_GEN_ACT")
  private val CFG_CURR_LOOP_COMMAND_NAME = CommandName("CFG_CUR_LOOP")
  private val CFG_ACT_VC_COMMAND_NAME    = CommandName("CFG_ACT_VC")
  private val CFG_ACT_OFFLD_COMMAND_NAME = CommandName("CFG_ACT_OFFLD")

  val CommandMap: Map[CommandName, Setup => String] = Map(
    ACTUATOR_COMMAND_NAME      -> ACTUATOR.toCommand,
    TARG_GEN_ACT_COMMAND_NAME  -> TARG_GEN_ACT.toCommand,
    CFG_CURR_LOOP_COMMAND_NAME -> CFG_CUR_LOOP.toCommand,
    CFG_ACT_VC_COMMAND_NAME    -> CFG_ACT_VC.toCommand,
    CFG_ACT_OFFLD_COMMAND_NAME -> CFG_ACT_OFFLD.toCommand
  )

  // This is used by validation to verify that the sent command is currently supported. Could
  // be removed when all commands are supported
  val ALL_COMMANDS: List[CommandName] = CommandMap.keys.toList

  object CfgLoopModes extends Enumeration {
    type CfgLoopMode = Value

    val OFF: Value  = Value(1, "OFF")
    val ON: Value   = Value(2, "ON")
    val OPEN: Value = Value(3, "OPEN")
  }
  val cfgLoopModeChoices: Choices =
    Choices.from(CfgLoopModes.OFF.toString, CfgLoopModes.ON.toString, CfgLoopModes.OPEN.toString)
  val cfgLoopModeKey: GChoiceKey = ChoiceKey.make("MODE", cfgLoopModeChoices)

  object ControllerModes extends Enumeration {
    type ControllerMode = Value

    val OFF: Value        = Value(1, "OFF")
    val CONTINUOUS: Value = Value(2, "CONTINUOUS")
    val DISCRETE: Value   = Value(3, "DISCRETE")
  }

  private[SegmentCommands] trait LscsCommand[T] {
    def toSegment(segmentId: SegmentId): T
    def toAll: T
    def asSetup: Setup
  }

  private[SegmentCommands] class BaseCommand[T](prefix: Prefix, commandName: CommandName) extends LscsCommand[T] {

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

  def addActuators(setup: Setup, actId: Set[Int]): Setup = {
    require(actId.size <= 3 && actId.nonEmpty, "Actuator ID Set must have three members or less but must not be empty.")
    // Check that they are within range
    require(actId.max <= 3 && actId.min >= 1, "Actuator ID values must be: 1, 2, or 3")

    setup.add(actuatorIdKey.setAll(actId.toArray))
  }

  // ACTUATOR command
  object ACTUATOR {
    private val COMMAND_NAME = ACTUATOR_COMMAND_NAME

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

      setup = addActuators(setup, actId)

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
      val actIdVal     = if (actId.size == 3) "ALL" else valuesToString(actId.values)
      val sb           = new StringBuilder(s"${setup.commandName.name} ACT_ID=$actIdVal")
      if (modeExists) sb ++= s", MODE=${setup(actuatorModeKey).head.name}"
      if (targetExists) sb ++= s", TARGET=${setup(targetKey).head}"
      sb.result()
    }
  }

  /* ---------------------------------------------- */
  object TARG_GEN_ACT {
    private val COMMAND_NAME = TARG_GEN_ACT_COMMAND_NAME

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

      val actIdVal = if (actId.size == 3) "ALL" else valuesToString(actId.values)
      val sb       = new StringBuilder(s"${setup.commandName.name} ACT_ID=$actIdVal")
      if (modeExists) sb ++= s", MODE=${setup(targetGenModeKey).head.name}"
      if (shapeExists) sb ++= s", TYPE=${setup(targetShapeKey).head.name}"
      if (amplitudeExists) sb ++= s", AMPL=${setup(targetAmplitudeKey).head}"
      if (periodExists) sb ++= s", PERIOD=${setup(targetPeriodKey).head}"
      if (offsetExists) sb ++= s", OFFSET=${setup(targetOffsetKey).head}"
      sb.result()
    }
  }

  /* ---------------------------------------------- */
  // CFG_CUR_LOOP
  object CFG_CUR_LOOP {
    private val COMMAND_NAME = CFG_CURR_LOOP_COMMAND_NAME

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

  /* ---------------------------------------------- */
  // CFG_ACT_VC
  object CFG_ACT_VC {
    private val COMMAND_NAME = CFG_ACT_VC_COMMAND_NAME

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

  /* ---------------------------------------------- */
  object CFG_ACT_OFFLD {
    private val COMMAND_NAME = CFG_ACT_OFFLD_COMMAND_NAME

    import ControllerModes.*
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
     * Returns a formatted CFG_ACT_VC command from a Setup
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

}
