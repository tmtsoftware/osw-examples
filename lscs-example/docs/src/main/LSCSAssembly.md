#LSCS Segments Assembly

The project includes a simplified Segments Assembly. As discussed in the @ref[overview](index.md), the Segments Assembly
job is to receive Assembly Setups, convert them to HCD Setups, and forward them to the Segments HCD. There are no events produced
by the Assembly and no Setup commands that are handled within the Assembly itself.

## Top Level Actor

The Top Level Actor for Segments Assembly can be found in the `lscsComps` project in the file named `SegmentsAssemblyHandlers.scala`,
which contains the entire Assembly. As with all CSW Assemblies, the TLA for Segments Assembly implements handlers as needed. In
this Assembly, only the `validateCommand` and `onSubmit` handlers are implemented. 

## Assembly Command Validation
The `validateCommand` handler is shown below.

Scala
: @@snip [Validation](../../../lscsComps/src/main/scala/m1cs/segments/assembly/SegmentsAssemblyHandlers.scala) { #handle-validation }

The `validateCommand` checks that the command is a `Setup` and returns an error if an `Observe` is received; otherwise it passes
on validation of the Setup to `handleValidation`. Remember that each Setup from the outside has a Setup with the command name
equal to the name of the segment command. Therefore, the validation code looks up the command name in the list of implemented
commands in Common. This code is shown below:

Scala
: @@snip [CommandSupport](../../../lscsCommands/src/main/scala/m1cs/segments/segcommands/Common.scala) { #command-support }

If the command name is not present, validation fails with an UnsupportedCommandIssue. Otherwise, the command is Accepted.
Validation could be improved, but we assume that if the command name is within our list, then the Setup contains all the
correct parameters. An error is returned during execution if the handling fails.

## Assembly Command Execution
Once validated, the `onSubmit` handler is called. The submit-related code for the Segments Assembly is shown below:

Scala
: @@snip [Validation](../../../lscsComps/src/main/scala/m1cs/segments/assembly/SegmentsAssemblyHandlers.scala) { #important-code }

As before, the `Observes` return an Error (even through we can not get to this code in this example.) The `handleSetup`
command must handle any named commands first. In this case, there is a command called `shutdownCommand` that when sent to the
HCD, it causes all the segment connections to be closed and the HCD shutdown. This is useful for testing.

The second case matches on any other command. In this case, the function `HcdDirectCommand.toHcdDirectCommand` is called.
If it does not fail (it is wrapped in a try clause), a Setup formatted for the HCD is returned and passed to the
`submitAndWaitHCD` function. All this is doing is calling Command Service submitAndWait, but it is checking that the
CommandService instance created for the HCD is valid before sending.  (This will be covered later.)  It also provides
a custom timeout, which has been arbitrarily set to 15 seconds for all the segments to complete any command.  If this
time is exceeded, the submitAndWait will time out and return an error.  Note that if the Command Service is not available,
because the HCD is not available, an Error is also returned.

The function HcdDirectCommand.toHcdDirectCommand is shown below:

Scala
: @@snip [toDirect](../../../lscsComps/src/main/scala/m1cs/segments/shared/HcdCommands.scala) { #hcd-direct }

First this function ensures that some required parameters are present in the Setup received by the Assembly.  A `require`
will throw an IllegalArgument exception if the condition is false. It then uses the CommandMap structure from Common
(shown as part of validation) to extract the `toCommand` function for the command. This returns the String segment
command as discussed in @ref[input](./input-output.md).  It checks that the command String is not empty.

Then the HCD setup is constructed using parameters from the Assembly Setup and new ones from HcdDirectCommand. The
command string is passed with the lscsCommandKey parameter, and the command name is within lscsCommandNameKey. The
last entry pulls the segmentIdKey from the Assembly Setup and inserts it into the HCD setup.

The HCD Setup for the Assembly Setup:

```scala
Setup(paramSet=Set(ACT_ID((1,3)none), MODE((TRACK)none), TARGET((22.34)none), 
  SegmentId((A23)none)), source=M1CS.hcdClient, commandName=CommandName(ACTUATOR), maybeObsId=None)
```
is:
```scala
Setup(paramSet=Set(lscsCommand((ACTUATOR ACT_ID=(1,3), MODE=TRACK, TARGET=22.34)none), 
      lscsCommandName((ACTUATOR)none), SegmentId((ALL)none)), 
      source=M1CS.segmentAssembly, commandName=CommandName(lscsDirectCommand), maybeObsId=None)
```

### Handling SubmitResponse from the Segments HCD
One last thing is that the Assembly must handle the SubmitResponse from the HCD. When the Assembly sends the HCD Setup
to the HCD, a new `runId` is created for the command. When the command completes, the Assembly needs to pass an
appropriate response back to the caller.  This is handled by the following piece of code that is repeated fro above:

```scala
submitAndWaitHCD(runId, hcdSetup) map { sr =>
  cswCtx.commandResponseManager.updateCommand(sr.withRunId(runId))
}
Started(runId)
```
The onSubmit handler sends the command using submitAndWaitHCD and then returns `Started` to the caller. This is a CSW
long-running command (as opposed to an immediate-completion command). The HCD command runs asynchronously and returns
a value in the future. When that occurs, the result is mapped to the closure shown, which calls the
Assembly's Command Response Manager with the SubmitResponse from the HCD, but it replaces the HCD runId with the
Assembly Setup's runId using `withRunId`.  That's all that is needed to handle the response from the HCD to the Assembly
caller.

That's the extent of the Setup processing in the Assembly.

## Tracking the HCD and Creation of Command Service
The last bit of interesting code in the SegmentsAssemblyHandlers is how the SegmentsAssembly gets connection information
about the Segments HCD, which it must have to send it commands.

As a reminder, connections (i.e. hosts and ports) are not hard-coded in CSW. When a component starts up, its
Supervisor registers itself with the Location Service on behalf of the TLA
and that location information includes enough information so that one component can create an
appropriate connection to the other. CSW supports Akka-based connections and HTTP-based connections.

When the Segments Assembly starts up its Component Configuration File contains an entry that indicates to the Supervisor that it wants
to `track` the HCD. The "SegmentsAssemblyStandalone.conf" conf file is shown here.

```scala
prefix = "m1cs.segmentsAssembly"
componentType = assembly
behaviorFactoryClassName = "m1cs.segments.assembly.SegmentsAssemblyBehaviorFactory"
locationServiceUsage = RegisterAndTrackServices
connections = [
  {
    prefix: "m1cs.segmentsHCD"
    componentType: hcd
    connectionType: akka
  }
]
```
This file is discussed in the CSW documentation.  The key in this discussion is that the `connections` array has an entry for the
an HCD with prefix `m1cs.segmentsHCD` and connectionType: Akka.  This indicates to CSW and the Supervisor of the Assembly
that it should track the Segments HCD and deliver events to the Assembly when the Segments
HCD is available and also when/if it shuts down or crashes.  To receive tracking events, the assembly overrides the
`onLocationTrackingEvent` handler as shown here.

Scala
: @@snip [Tracking](../../../lscsComps/src/main/scala/m1cs/segments/assembly/SegmentsAssemblyHandlers.scala) { #tracking-events }

This code shows that the Assembly is handling two events: `locationUpdated` and `locationRemoved`. The locationUpdated is
delivered when the HCD is registered and running. When this happens, the Assembly creates a CommandService instance for the HCD.

In the constructor of the Assembly is the following line:
```scala
private var hcdCS: Option[CommandService] = None // Initially, there is no CommandService for HCD
```
Initially this is set to None, meaning there is no Command Service (i.e. the HCD is not available). When
the HCD is available, a CommandService instance is created and this variable is set to its value as shown.

Then, when a command is sent and processed by the `submitAndWaitHCD` call way up in the Assembly Command Execution section,
it checks the value of this Option. If present, the command is sent to the HCD. If None, an Error is returned to the caller.

This is an excellent way to track the availability of an Assembly or HCD using the builtin CSW functionality.