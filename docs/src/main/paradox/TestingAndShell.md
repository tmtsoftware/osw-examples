# Testing LSCS Code and Using esw-shell

This section describes the testing and ties the goals of the demonstration to test verification. It also shows how
to use esw-shell to send commands to the assembly.

## Testing Strategy
The strategy here demonstrates one approach to testing. Others rely on mocks to minimize integration tests, but with the
level of difficulty in an Assembly and HCD, direct testing is not too difficult.

The approach in this project is that every major class has its own unit tests. As the project progresses higher in
the chain more components are involved, which have been unit tested, resulting in a tested component.  The
following table shows classes and tests:

| Class | Test| Project | Description |
|-------|------|------|------------|
| SegmentId | SegmentIdTests| lscsCommands | Tests for the SegmentId type, Sector, and SegmentRange |
| command classes | SegmentCommandsTests | lscsCommands | Tests for all commands are in this one file. This could be broken out. |
| SocketMessage | SocketMessageTest | lscsComps | Tests for JPL message format. |
| SocketClientStream | SocketClientStreamTest | lscsComps | Tests the protocol to the simulator |
| SegmentActor | SegmentActorTests | lscsComps | Tests basic functionality of Segment Actor talking over client stream |
| SegComMonitor | SegComMonitorTests | lscsComps | Tests multi-segment commands and completion |
| SegmentsHcdHandlers | SegmentsHcdTests | lscsComps | Tests that HCD receives Setups and sends to segments properly |
| SegmentsAssemblyHandlers | SegmentsAssemblyIntTests | lscsComps | Integration tests of end-to-end Assembly receives Setups and sends on to HCD, etc. |
| SegmentsAssemblyHandlers | SegmentsAssemblyTests | lscsComps | Tests that Assembly outputs the correct Setup when processing. Uses "mock" HCD |

Typically, the tests are run from Intellij. In most cases all tests run without issues, but there are some cases where tests will fail because
a previous test is not finished completely.  I have not taken the time to wring out all these issues.  

There is also the issue with 492 segment tests failing on macOS as mentioned in other places.

## Use of esw-shell for Testing 

ESW-shell is an application that is distributed as part of ESW (it was previously known as csw-shell). It
is a command-line interactive user interface that can be used to communicate and control CSW and ESW services and applications.
The installation of ESW-shall is described in the ESW documents [here](https://tmtsoftware.github.io/esw/technical/apps/esw-shell.html).

### Setting Up To Use esw-shell

Setting things up can be a bit complicated. There are a number of considerations when testing:

* Do I want to use the JVM-based simulator or the external JPL simulator?
* Am I on Linux?
* Should the JVM-based simulator run from within IDE or not?
* Is csw-services started?
* Should I start the Assembly and HCD separately or in a container?

So before using esw-shell successfully, CSW services must be running, a simulator must be running, and the Segments Assembly 
and HCD must be running.  The following will show one approach.

### Install and Run csw-services

Follow the instructions [here](https://tmtsoftware.github.io/csw/apps/cswservices.html) to install csw-services.
Only Location Service is needed, but you must start with an option.

```scala
csw-services -e
```
This starts csw-services with Location Service and Event Service

### Start the LSCS Simulator

The JVM-based Simulator can be started using `sbt`:

```
sbt "lscsComps/runMain m1cs.segments.streams.server.SocketServerStream"
```
or 
```
sbt
project lscsComps
run
```

And select the SocketServerStream app.

@@@ note
You may also start the simulator on a different machine and use the -DsimulatorHost option to indicate
where the simulator is located.  This causes the SegmentActor to connect to the port 8023 on the host indicated. 
This is a good way to keep a simulator runing on a Linux machine.
@@@

### Starting the Segments Assembly and Segments HCD

There are component configuration files for standalone HCD and Assembly as well as a container configuration
file that includes both.  That is the most convenient.  Use sbt to start the two in a container as discussed in
the deploy section.

```
sbt "lscs-deploy/runMain m1cs.segments.deploy.SegmentsContainerCmdApp --local src/main/resources/SegmentsContainer.conf"
```
or for a remote LSCS Simulator:

```
sbt "lscs-deploy/runMain m1cs.segments.deploy.SegmentsContainerCmdApp --local src/main/resources/SegmentsContainer.conf -DsimulatorHost=192.168.1.230" 
```

### Loading and Executing Segment Commands

At this point, CSW services are running, the LSCS Simulator is running, and the Segments Assembly/HCD are running.

The `lscsCommands` project is created to only include the functions that create Segment Assembly Setups. This
means it can be loaded into esw-shell and used to send commands to the Segments Assembly.

The file `scripts/commands.sc` contains a small introduction that shows how to use `esw-shell` to 
send commands to SegmentsAssembly.

By default, esw-shell includes imports of all the major CSW libraries. It also includes some helpers for doing
typical operations.  First you must import the code needed to create commands.  This is present in the example
script, so you can copy/paste.

```scala
// This is needed to resolve TMT libraries (even though they are already present)
import $repo.`https://jitpack.io`

// This loads the lscscommands JAR file.  If you change the version, you must update this also.
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
```
At this point, any of the commands can be executed.  Here is how to send an `ACTUATOR` command to segment C9.

```scala
// A test prefix 
val prefix = Prefix("ESW.test")

// Create an ACTUATOR command.
val ac = ACTUATOR.toActuator(prefix, Set(1,3)).withMode(TRACK).toSegment(SegmentId("C9"))

// Create a Command Service for the Segments Assembly. This uses the Location Service to find the Assembly
val segA = assemblyCommandService("M1CS.segmentsAssembly")

// Get the Assembly Setup for the command and send it to the Assembly 
val result = segA.submitAndWait(ac.asSetup).get
```

The submitAndWait stops and waits until the command is completed. The `get` on the code waits for the Future
to complete and returns the value.  

Along the way, the entered lines will print out results (and error messages).  For instance the commands above
also printed out:

```scala
val ac = ACTUATOR.toActuator(prefix, Set(1,3)).withMode(TRACK).toSegment(SegmentId("C9"))
ac: ACTUATOR.toActuator = toActuator(prefix = Prefix(subsystem = ESW, componentName = "test"), actId = Set(1, 3))

val s = ACTUATOR.toActuator(prefix, Set(1,3)).withMode(TRACK).toSegment(SegmentId("C9")).asSetup
s: Setup = Setup(
  source = Prefix(subsystem = ESW, componentName = "test"),
  commandName = CommandName(name = "ACTUATOR"),
  maybeObsId = None,
  paramSet = Set(
    Parameter(keyName = "ACT_ID", keyType = IntKey, items = ArraySeq(1, 3), units = none),
    Parameter(keyName = "MODE", keyType = ChoiceKey, items = ArraySeq(Choice(name = "TRACK")), units = none),
    Parameter(keyName = "SegmentId", keyType = StringKey, items = ArraySeq("C9"), units = none)
  )
)

// Note that the segmentId does not appear in the command sent to the segment
val c = ACTUATOR.toCommand(s)
c: String = "ACTUATOR ACT_ID=(1,3), MODE=TRACK"
```

 

Commands such as the above can be saved into "scripts", that can then be loaded.  There is a learning
curve to using esw-shell, but usually, the issues are around imports and syntax.
