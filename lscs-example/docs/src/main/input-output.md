# Setup Input/Output 

This section describes how commands are encoded as Setups and how they are unpacked and transformed into commands
that go to the individual segments.

## Input: Setup Description

The Segment Assembly receives CSW Setups, which may come from a variety of sources. In this project we demonstrate
Setups from test code and using esw-shell (in a later section on testing).  Input describes how Setups are created.

The strategy is to build a library of functions that make it relatively easy to construct Setups. The API is based
on the previously referenced document. In this document each command is documented with a set of parameters that are
required or optional. 

The set of parameters for each command is generally different, although some share parameters.  For instance, many
commands include a selection of actuators. Each command also has a common parameter that indicates if the command
should go to one specified segment or to all segments.

The creation of Setups is implemented in a separate project with only the code needed for the job so that a JAR
file can be created that can be loaded into esw-shell.  The `lscsCommands` JAR only depends on CSW libraries, which
are included in esw-shell; therefore, scripts can be written using the library functions. 

## Implementing Commands

A representative subset of the commands have been implemented.  The plan was to do them all, but the documentation
gets less reliable towards the end and is missing command examples for a few important commands.

There is a subproject called `lscsCommands`. This project contains all the code to create Assembly Setups and
to extract and convert an Assembly Setup to a segment command string.

Under the package `m1cs.segments.segcommands` there is a file for each implemented command that is the name of the
command. There are currently 10 commands implemented.  Examples are: ACTUATOR, CFG_ACT_OFFLD, etc.

In each command file there is an object with the same name (i.e. ACTUATOR). An example is the Actuator command
shown below, which includes all the features of the command implementation. Within the object is all the code to
create a Setup and to extract a segment command. All commands are constructed the same way.

Scala
: @@snip [ACTUATOR](../../../lscsCommands/src/main/scala/m1cs/segments/segcommands/ACTUATOR.scala) { #example-command }

At the top of the object common code is imported. Following this is the name of the command, which is again, the
name of the file. 

@@@ note { title=Note }
In this example, I decided that there would be a unique Setup for each command. So the CommandName is the name of the
segment command. An alternative would be to have a single Setup type and include a parameter called CommandName. There
are pros/cons of each approach. For the HCD, I selected the second approach.
@@@

@@@ note { title=Note }
Each command is implemented as a case class with parameters that are the command's required parameters. There is
a base class for all commands that includes a `Setup` and support for sending the command to one or all the segments
with a segmentId key.
@@@

In this case, ACTUATOR includes the prefix of the sender, and a Set of
integers indicating the actuators to influence. Examples are Set(1), Set(1,3). To indicate all actuators you can
say Set(1,2,3) or ALL_ACTUATORS, which is an alias for Set(1,2,3).  If the source is prefix: M1CS.client, the
minimal Actuator command is:


```scala
val prefix=Prefix("M1CS.client") 
toActuator(prefix, Set(1))
```
This command is somewhat meaningless, because to be a correct ACTUATOR command it must have at least one of the
optional parameters (see below).

## Optional Parameters
The value returned by toActuator is a `toActuator` instance. Optional values are added using "with" methods using a
`fluid-style` API so options can be added as needed.  For example to add the optional actuator mode and target, the following
are all possible:

```scala
val prefix=Prefix("M1CS.client")

toActuator(prefix, Set(1)).withMode(SLEW)

toActuator(prefix, Set(1)).withTarget(22.3)

toActuator(prefix, ALL_ACTUATORS).withMode(SLEW).withTarget(22.3)
```

The case classes include `with` methods to add optional parameters as in:

```scala
 def withMode(mode: ActuatorMode): toActuator = {
  setup = setup.add(actuatorModeKey.set(Choice(mode.toString)))
  this
}

def withTarget(target: Double): toActuator = {
  setup = setup.add(targetKey.set(target.toFloat))
  this
}
```
Each method returns `this`, which is in this case an `toActuator` instance, allowing the fluid style.  This is a 
reasonable way to support optional parameters in a typeable API.

## Choice Parameters
There are quite a few choice parameters. I've implemented them as enumerations as shown below for `ActuatorMode`:

```scala
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
```

The values must be imported. This is also true with the API is used externally.  Each enumeration is supported
with a `GChoiceKey` and the Choices are made up of the enumeration values as Strings.

The last line shows that there is a Float key for the TARGET value. Note that the API takes a Double, not a Float.
This is because in Scala (and Java) you must add an `f` to make a value a Float. The conversion from a Double to a
Float is done inside the code to make it a little more friendly to typing.

## Conversion to Setup
After creating a command instance like `toActutator`, it can then be converted to a Setup for submission to the
Segment Assembly. Each command includes a method called `asSetup` that returns a Setup. This is the method
that verifies that all the information has been entered that is required.

```scala
override def asSetup: Setup = {
  val mode   = setup.get(actuatorModeKey)
  val target = setup.get(targetKey)

  // Check that there is at least one
  require(mode.isDefined || target.isDefined, "Actuator must have either a mode or target or both.")

  // Should require a segment set
  Setup(setup.source, setup.commandName, setup.maybeObsId, setup.paramSet)
}
```
In the ACTUATOR command, when calling `asSetup` a check is done to verify that at least the mode or target is 
included.  If neither of these parameters is provided, an exception is thrown.  If it is good, a copy of the
internal Setup is returned.

@@@ warning
Many of the commands in the documentation have optional commands there is no information on what combinations are
legal or which ones must really be provided as in the above ACTUATOR case. This can be fixed in the same way as the
above was done once the documentation is improved.
@@@

In summary, to create a Setup to send to the Segment Assembly for the ACTUATOR command, the following is an example:

```scala
val setup = toActuator(prefix, ALL_ACTUATORS).withMode(SLEW).withTarget(22.3).asSetup
```

## Output: Converting a Setup to a Segment Command

In the implementation approach mentioned on the overview page, the strategy is that the Assembly Segment receives
the assembly Setup and converts it to an HCD Setup. The HCD Setup is a single command that has an argument
that is the command that is sent to the segment as a String.

The output then is to extract the segment command from the Assembly Setup.  Each command also implements a
method called `toCommand`, which uses the parameters of the Setup to create a well-formed command.  The
following is the `toCommand` method for the ACTUATOR command.

```scala
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
```
To recap, a command includes required parameters and optional parameters.  The above shows extracting
the values for actuator, which is required. It creates a Boolean to check to see if actuator mode and/or
target exists by checking for `actuatorModeKey` and `targetKey`. 

Then a StringBuilder is created that creates a String for the command. First, the command is extracted using
the Scala String interpolator syntax (${parameter}). The like starting with actIdVal checks to see whether
there is a subset or all of the actuators. The valuesToString method formats a proper value for the command (as
in (1,2)). Finally, if the mode and target exist, parameters are added to the String for each.

The example below is shows the output command that goes with the created Assembly Setup.
```scala
println(toCommand(toActuator(prefix, AllActuators).withMode(TRACK).withTarget(22.34).asSetup))

ACTUATOR ACT_ID=ALL, MODE=TRACK, TARGET=22.34
```
### Segment Destination
Each command requires a segment location, but the location does not appear in the output command. The
Setup contains a parameter for the segment destination.  The following is the printed value of an 
ACTUATOR Setup for the example above.

```scala
println(toActuator(prefix, AllActuators).withMode(TRACK).withTarget(22.34).asSetup)

Setup(paramSet=Set(SegmentId((ALL)none), ACT_ID((1,2,3)none), MODE((TRACK)none), TARGET((22.34)none)), 
      source=M1CS.client, commandName=CommandName(ACTUATOR), maybeObsId=None)
```
There is an extra SegmentId parameter with the value ALL, indicating the command will be sent to all 
segments. This parameter is used by the HCD to do the right thing. The SegmentId parameter is handled within
the command base class. By default, a command goes to all segments. 

The base class also provides two methods called `toAll`, and `toSegment` to add a destination to any command
as shown here:

```scala
to = toActuator(prefix, Set(1, 3)).withMode(SLEW).toSegment(SegmentId("B22"))
```
or
```scala
to = toActuator(prefix, Set(1, 3)).withMode(SLEW).toAll
```

A SegmentId instance must be created to send to a specific segment. The SegmentId type verifies that the segment
sector is A-F and segment number is 1-82.  An exception is thrown if not true.

## Testing Commands
Tests exist for each command to verify that it is working properly.  There is one file called SegmentCommandsTests
in the lscsCommands test area.  Each command has similar tests. The following shows the tests for Actuator.

Scala
: @@snip [ACTUATOR_TESTS](../../../lscsCommands/src/test/scala/m1cs/segments/segcommands/SegmentCommandsTests.scala){ #example-tests }

The tests create Setups with varied parameters and test that the output command is correct. Following are tests
to verify that exceptions are thrown for bad conditions. For instance, note the test for the lack of an
optional parameter.

```scala
 // Check for no optional
assertThrows[IllegalArgumentException] {
  toActuator(prefix, Set(1, 2, 3)).asSetup
}
```
This verifies that an exception is thrown if neither `withMode` nor `withTarget` is included.

## Input/Output Summary
Each command is created in the same way. A file is created in the `m1cs.segments.segcommands` package with
the name of the command. A case class is created for the command with the required parameters. With methods
are added for each optional parameter.

A `asSetup` method is included to check the Setup for consistency or constraints. A `toCommand` function is
included to use the Setup parameters and output a segment command.
