# Lower Segment Control System Demonstration

@@@ index

- @ref:[Input/Output](input-output.md)
- @ref:[LSCS Assembly](LSCSAssembly.md)
- @ref:[LSCS HCD](LSCSHcd.md)
- @ref:[LSCS Simulators](LSCSSimulator.md)
- @ref:[Testing and ESW-shell](TestingAndShell.md)

@@@

This is documentation for the Lower Segment Control System (LSCS) demonstration project that is present in 
the [TMT GitHub site](https://github.com/tmtsoftware/osw-examples/tree/master/lscs-example). This project
was created to research and demonstrate potental issues around the M1CS subsystem.

In the M1CS design, there is an CSW Assembly/HCD that accepts commands that are then passed to one or more
mirror segments. Each of the 492 segments includes a Lower Segment Control System, which is running on a
specialized processor running compiled C-code. The M1CS group has defined a socket-based protocol for the LSCS, 
based on a JPL library, that wraps String-based commands.

The following figure shows the architecture targeted by this demonstration, which is based on the design of the M1CS group
placed within the CSW architecture planned for the telescope site. 

![layers](./images/M1CSFigure1.png)

In this figure a future browser-based M1CS Engineering User Interface issues commands to the LSCS Assembly through the ESW UI Gateway.
The commands flow through the UI Gateway to the LSCS Assembly in the form of CSW Setup commands. The commands are passed to the HCD,
which understands the LSCS-protocol and has established a TCP-based connection to each of the 492 LSC segment systems. As mentioned,
the commands are Strings in the format documented in SegmentHcdCmdDict_20210902.pdf.  An example command String is:

![command1](./images/Command1.png)

The protocol is not described here, but involves completion information and updates when the commands take a long time. Interesting
requirements of this system is that: 1) Each LSCS can execute more than one command at a time, 2) The commands may complete
in a different order than started. 3) A command issued to the LSCS Assembly may be directed to one segment or all of the 492 segments, 
and 4) The command issued to the Assembly is only complete when the command to all 492 segments have completed. The system 
is highly asynchronous and non-blocking.

The following figure shows what is present in LSCS Demonstration.

![layers](./images/M1CSFigure2.png)

The deliverables of this demonstration project include:

* LCSC Assembly
* LSCS HCD
* LSCS Simulator
* Test Code

The following issues are addressed:

* The structure of the M1CS end-to-end system
* Demonstration of how to create Setups that map to LSCS commands
* Provide examples of implementing the LSCS commands (not all commands in the dictionary are provided)
* Support for sending a command to one or all segments in the HCD
* Support for sending commands to Segment using JPL protocol
* Demonstration that commands to all segments can complete when all segment commands complete
* Demonstrate that individual segment commands can complete asynchronously
* Demonstrate that a HCD can handle overlapping asynchronous commands
* Demonstrate that the CSW HCD can make 492 socket connections to the project-provided LSCS simulator
* Demonstrate that the CSW HCD can communicate with the M1CS-team's simulator
* Show how to test the code at various levels
* Demonstrate how to send commands to the LSCS Assembly from esw-shell

