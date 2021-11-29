# Goals and Addressed Issues Summary

The following provides follow-up of the goals of lscs-example first discussed 
in the @ref[document overview](index.md#issues-addressed-in-the-demonstration) and references to any supporting documentation or code.

####The structure of the M1CS end-to-end system

The suggested structure of the M1CS segments control is described in the @ref[document overview](index.md) in the first section.

####Demonstration of how to create Setups that map to LSCS commands

A suggested approach for creating Setups for Assembly commands is presented @ref[here](input-output.md#implementing-commands). 
This approach also shows how to go from an Assembly Setup to an SCMS segment command in the Assembly. Other approaches are possible, of course.

####Provide examples of implementing the LSCS commands (not all commands in the dictionary are provided)

Ten example commands were implemented as part of the lscs-example. The others, once fully documented, can
be implemented by following the pattern. The commands are implemented in the files in the `lscsCommands` project.

####Support for sending a command to one or all segments in the HCD

All implemented commands support sending to 1 segment or ALL segments. A SegmentId type is available for addressing a single
segment. The support for 1 or ALL segments is supported uniformly for all commands.

The HCD also supports sending and completing commands to 1 or ALL segments.

See @ref[here](input-output.md#segment-destination) and processing in the HCD @ref[here](LSCSHcd.md#segments-hcd-command-execution).

####* Support for sending commands to Segment using JPL protocol and communicate with the M1CS-team's simulator

Allan has written a JVM-based simulator that obeys the JPL C-library protocol.  It is possible to replace the JVM-based simulator
with the C-based simulator in [m1cs-lscs-sim](https://github.com/tmtsoftware/m1cs-lscs-sim) and test, etc continue to work although 
the behavior is not the same. The C-based simulator returns immediately for all commands.  See @ref[this page](LSCSSimulator.md) (this is a private repo and may give 404).

####Demonstration that commands to all segments can complete when all segment commands complete

This is one of the most important goals of the demonstration project. The actor `SegComMonitor` was created to handle the responses from 
1 or All segments. The way `SegComMonitor` satisfies this goal is shown @ref[here](LSCSHcd.md#segment-monitor). A test showing completion
for 492 segments is shown in the same section.

####Demonstrate that individual segment commands can complete asynchronously

Handling asynchronous commands is an important feature. The JVM-based LSCS simulator demonstrates this by delaying a random
amount of time for every segment command. This allows the higher-level software to demonstrate proper functionality with asynchronous commands. The `SegComMonitor`
starts 1 or 492 commands without blocking and waits asynchrnously for their completion.  All CSW framework messages are asynchronous so the command from the Assembly to
the HCD is also asynchronous and the command to the Assembly can also be asynchronous.  This is shown in the `SegComMonitor` @ref[reference](LSCSHcd.md#segment-monitor) 
as well as the test shown (other tests are present in the SegComMonitorTests class).

####Demonstrate that a HCD can handle overlapping asynchronous commands

Similar to the previous point, the asynchronous nature of CSW makes overlapping commands operate naturally with no extra logic or
special cases. The constraint on overlapping commands will come down to the functionality in the LSCS segment sofware itself.
Overlapping asynchronous commands are shown in the `SegComMonitor` @ref[reference](LSCSHcd.md#segment-monitor)
as well as the overlapping command test shown in that section.

####Demonstrate that the CSW HCD can make 492 socket connections to the project-provided LSCS simulator

This is the requirement that started this demonstration project. Currently, creating 492 HCDs CSW is not possible, but that is not really needed, because
the interface to the LSCS is simple and creating a socket connection turns out to be lightweight (forgetting the issue with macOS, of course). It
might be reasonable to create the connections only when a command is sent.  The ability to make 492 segments can be tested at a very low level. 
The `SocketClientStreamTest` shows creating 492 segments. See the simulator @ref[page](LSCSSimulator.md).

####Show how to test the code at various levels

The demonstration project shows how to create CSW tests starting at the lowest level with tests involving
more and more functionality following the TMT Testing Plan.  See the @ref[testing page](TestingAndShell.md) for a list of all tests.
Also see the `test` directory under each of the projects: lscsCommands, lscsComps, lscsDeploy.

####Demonstrate how to send commands to the LSCS Assembly from esw-shell

Examples showing the use of the lscsCommands JAR file and esw-shell are shown in the testing page: @ref[testing page](TestingAndShell.md#loading-and-executing-segment-commands).