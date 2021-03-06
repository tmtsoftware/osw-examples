# LSCS Segments HCD

The project includes a Segments HCD that processes segments commands. As discussed in the @ref[overview](index.md), the Segments HCD
receives HCD Setups from the Assembly (or other sources), validates them and then sends them off to one or more segments. It
monitors each of the segments for a response, which is hopefully success, but may be an error.  Once all the segments complete
successfully or if any segment completes with an Error, the HCD command is completed and a response is passed back to the caller.
There are no events of any kind produced by the HCD.

## Top Level Actor

The Top Level Actor for Segments HCD can be found in the `lscsComps` project in the file named `SegmentsHcdHandlers.scala`,
which contains the majority of the HCD code. As with all CSW components, the TLA for Segments HCD implements handlers as needed. 
In this HCD, only `initialize`, `validateCommand`, and `onSubmit` handlers are implemented.  Unlike the Assembly,
the HCD does not use the `onLocationTrackingevent` handler.

## Initialization
Initialization in the Segments HCD does the important job of creating the Segments, which is discussed later. Here
only the job of initializing the segments is done.

Scala
: @@snip [Initialize]($lscs.base$/lscsComps/src/main/scala/m1cs/segments/hcd/SegmentsHcdHandlers.scala) { #initialize }

Any state that is initialized in the `initialize` handler must be global to the TLA. In this case, there is a 
variable called `createdSegments` of type `Segments` that is set during initialize. The segments value is managed 
by the SegmentManager object, but it is essentially a Map of SegmentId values to SegmentActor references. The 
variable `createdSegments` is used by the onSubmit handler.

As shown, the code gets the `maxSegments` value from the reference.conf file and hands it to the SegmentManager to create segments. 
As mentioned before, the Segment Manager will create that number of segments in each sector.  Therefore, the maximum value for
maxSegments is MAX_SEGMENT_NUMBER and once one is happy with the simulator, the check can either be eliminated or
set to 82 in the reference.conf file.

## HCD Command Validation

As in the Assembly, an `Observe` is rejected and the Setup is passed to another function for validation.
The `validateCommand` handler is shown below.

Scala
: @@snip [Validation]($lscs.base$/lscsComps/src/main/scala/m1cs/segments/hcd/SegmentsHcdHandlers.scala) { #handle-validation }

The HCD can process two commands: lscsDirectCommand and shutdownCommand. For lscsDirectCommand `handleValidation` does three things:

1. Is the `lscsCommandKey` present?  If not, return Invalid.
2. Is a `segmentIdKey` included?  If not, return Invalid.
3. Finally, if the destination is a single segment, check to see if the destination segment is "online".  Since we can 
configure the number of segments to create, it's possible to send a command to a segment that does not exist. 
If this check fails, return Invalid.

If these three things pass, the Setup is Accepted.  There is probably more that could be done but that's it for now.
We are assuming that the input/output library is used to constructing the Setups so no checks for required parameters
are done.

The HCD also accepts the HcdShutdown.shutdownCommand. This command is always accepted.

If any other command is received by the HCD, it is rejected and Invalid is returned.

## Segments HCD Command Execution
Once validated, the `onSubmit` handler is called. Like the Assembly, the HCD rejects Observes and hands off
execution to `handleSetup`. Checking for Observe again isn't needed since it is done in validation. 
HandleSetup is reproduced below.  This is the crux of the HCD.

Scala
: @@snip [Submit]($lscs.base$/lscsComps/src/main/scala/m1cs/segments/hcd/SegmentsHcdHandlers.scala) { #handle-submit }

There are two commands, `lscsDirectCommand`, and `shutdownCommand`.

## lscsDirectCommand Implementation
The first thing that occurs is the command name, the segment destination, and the command String are extracted from the
Setup. Then the segmentKeyValue, which is either ALL_SEGMENTS or a specific segment ID is tested for and used
to access a list of segments for the command. It calls SegmentManager to return on or all.

Then a `SegComMonitor` actor is created, which takes the command String, the list of segments, the runId of
the request, and a function to execute when all the segments have completed. The `SegComMonitor.Start` message is 
sent to the monitor, which causes the sending of the command to all the segments.

Just as a note in case it is not clear. These lscsDirectCommands executed by the HCD are totally asynchronous. As you can see
from the code, once a monitor is started, the submit-handler returns `Started` indicating to the caller that a long-
running command is started. The HCD is then available to accept and process another HCD/segment command, which starts
its own SegComMonitor. The tests demonstrate that this works.

## Segment Monitor
Segment Monitor is the longest piece of code in the project. Segment Monitor is an actor implementing a two state
finite state machine with the job of executing a segment command and waiting for responses. The code is long but is included here.

Scala
: @@snip [Submit]($lscs.base$/lscsComps/src/main/scala/m1cs/segments/hcd/SegComMonitor.scala) { #seg-mon }

`SegComMonitor` is created in the `starting` state, awaiting the `start` message from the HCD TLA. When received, it sends the full segment
command to each of the SegmentActors on the list passed into the monitor from the TLA (it will be 1 segment or a list of all segments). 
Once the commands are sent, it moves to the `waiting` state.

The state is called `waiting` because it is waiting for the responses from the SegmentActors. First note that waiting starts
a timer so that if something goes wrong in a SegmentActor, after a timeout period, the CommandTimeout message will be sent
to itself, which will cause an Error SubmitResponse to be returned to the runId through the `replyTo` function.

The monitor is written to assume the SegmentActor will return Completed or Error responses as described in the M1CS docs
(Started and Processing are not handled). The happy case is the SegmentActor.Completed message. All the monitor does is 
count happy responses until it receives the correct number, it then sends Completed to the runId through the replyTo 
function. 

If a single Error is returned, the monitor stops immediately--if a single segment produces an error out of all segments,
it means the command fails immediately, and an Error is returned to the client through the runId and replyTo function.

The important part for completion is the code around the SegmentActor.Completed message. Whenever a segment returns a positive response, the
count is incremented and waiting is called again (which appears recursive, but actually isn't). Once all the responses are received, 
the Completed SubmitResponse is sent to the caller through the replyTo function. The Behavior of the monitor 
becomes `Behaviors.stopped`, which causes the monitor actor to stop.

@@@ note { title=Note }
The SegComMonitor is written in the functional typed actor style. See [akka.io](https://doc.akka.io/docs/akka/current/typed/index.html).
@@@

@@@ warning
Neither simulator implements the protocol that includes: Processing and Started messages. We suggest they not be implemented
and keep the low-level protocol simple.
@@@

`SegComMonitor` tests are a good way to see how testing works and how we validate the M1CS requirement to complete all
segment commands and complete a CSW command.

The following test shows sending a command to 492 segments and waiting for completion.  First the `SegmentManager` is used
to create a full mirror configuration.  This is what the HCD does during initialization  The `tester` is a function that
notifies the com1Response TestProbe when the command completes.

Then a `SegComMonitor` is created with a test command and the list of 492 segments. It is then started, which causes the
command to be sent to all the segments.  Then the test code waits for the Completion message.  Once received as a precaution
it waits to see if any other messages arrive, then shutsdown all the segment connections and quits.

Scala
: @@snip [SegMon1]($lscs.base$/lscsComps/src/test/scala/m1cs/segments/hcd/SegComMonitorTests.scala) { #test1 }

The following code is similar but in this case, the test starts and executes two overlapping segment commands and waits for them both
to asynchronously complete either successfully or with an error.  

Scala
: @@snip [SegMon2]($lscs.base$/lscsComps/src/test/scala/m1cs/segments/hcd/SegComMonitorTests.scala) { #test2 }

At least with the JVM simulator, it is not known which command will complete first because of the randomized delays, 
so the test waits for 2 messages and then checks that they are the correct messages.

## Segment Actor
The Segments HCD creates a SegmentActor instance for each configured segment. The SegmentActor
is a wrapper for the class called `SocketClientStream`, which is the low-level communication with the segment
and which implements the JPL-library protocol.  The code below is the interesting part of SegmentActor.

Scala
: @@snip [SegActor]($lscs.base$/lscsComps/src/main/scala/m1cs/segments/hcd/SegmentActor.scala) { #segment-actor }

First, when the SegmentActor is created in the `apply` def function. This is where the lower level `SocketClientStream`
class is added and where the socket connection to the LSCS occurs.

SegmentActor has two messages for sending a message to the LSCS simulator. The first is Send, which is the message
meant for the operating use case. It sends a command String to the segment LSCS.  The second is SendWithTime, which allows 
sending specified delay to the simulator allowing predictable testing of things like multiple commands and overlapping commands.

At this time both segment commands are implemented as a variable delay on the segment side of the socket. With
the `Send` command, the command String is sent to the simulator, which in the JVM simulator, returns the `Completed` response
after a random delay.  With the `sendWithTime` message, we send the
command: DELAY MILLIS such as DELAY 1234, to the socket client. The desired delay is an argument of the message. The
JVM simulator waits for the specified time before returning `Completed`.

Tthe `send` method of the socket client sends the command to the segment socket. This is an asynchronous call using a Future. 
When the response is received, the SegmentActor sends the response to the caller, which is a `SegComMon` instance, to be counted.

The ShutdownSegment command is also handled. When this occurs, the client is terminated, which closes the socket
connection to the segment. These shutdown commands are present so that tests always work correctly. In the
operations case, the sockets would probably stay open and would probably not need to be closed. It might be
worth-while to investigate created the sockets during a command and closing them after if commands are relatively
infrequent.