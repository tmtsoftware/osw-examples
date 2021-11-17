// This is needed to resolve TMT libraries (even though they are already present)
import $repo.`https://jitpack.io`

// This loads the lscscommands jar file.  If you change the version, you must update this also.
interp.load.ivy("com.github.tmtsoftware.m1cs" %% "lscscommands" % "0.0.1-SNAPSHOT")

// These imports are needed to send the 10 implemented commands
import m1cs.segments.segcommands._
import m1cs.segments.segcommands.Common._
import Common.CfgLoopModes._
import Common.ControllerModes._
import ACTUATOR.ActuatorModes._
import TARG_GEN_ACT.TargetGenModes._
import TARG_GEN_ACT.TargetShapes._
import CFG_CUR_LOOP.CfgCurLoopMotor._
import SET_LIMIT_ACT.PositionSensors._
import SET_PARAM_ACT.Motors._
import MOVE_WH.MoveTypes._
import MOVE_WH.Torques._
import MOVE_WH.Boosts._

// I've added this just to play with the strain commands
val testStrains: Array[Double] = Array(1, 2, 3, 4, 5, 6, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)

// A test prefix, can be anything following rules
val prefix = Prefix("ESW.test")

// Create an ACTUATOR command.
val to = ACTUATOR.toActuator(prefix, Set(1,3)).withMode(TRACK)

val s = to.asSetup

s: Setup = Setup(
  source = Prefix(subsystem = ESW, componentName = "test"),
  commandName = CommandName(name = "ACTUATOR"),
  maybeObsId = None,
  paramSet = Set(
    Parameter(keyName = "SegmentId", keyType = StringKey, items = ArraySeq("ALL"), units = none),
    Parameter(keyName = "ACT_ID", keyType = IntKey, items = ArraySeq(1, 3), units = none),
    Parameter(keyName = "MODE", keyType = ChoiceKey, items = ArraySeq(Choice(name = "TRACK")), units = none)
  )
)

val c = ACTUATOR.toCommand(s)

c: String = "ACTUATOR ACT_ID=(1,3), MODE=TRACK"

val segA = assemblyCommandService("M1CS.segmentsAssembly")
segA: csw.command.api.scaladsl.CommandService = csw.command.client.internal.CommandServiceImpl@2645d6fd

segA.submitAndWait(s)
segA.submitAndWait(s).get