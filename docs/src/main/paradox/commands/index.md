# Commands Example Documentation

There are three supplemental versions of the tutorial Assembly and HCD called: basic, moderate, and full. The
basic version is similar to the tutorial example here with possible best practices. Two other versions are included that introduce
ways of programming components along with increasing functionality.  The following table shows the
features of each version. Moderate adds functionality to Basic, and Full adds functionality to Moderate.

The code for the enhanced tutorials is in the CSW distribution at the following locations:

Scala versions are <!-- @github[here](/command-example/src/main/scala/org/tmt/osw). -->
Scala test code is <!-- @github[here](/command-example/src/test/scala/org/tmt/osw/). -->  

At this time there is no Java versions of moderate and full.

## Basic

### Basic HCD
* Implements a simple sleep worker in HCD using Time Service scheduler.
* Provides basic command validation.
* Shows one way to write onSetup handler.
* Shows how to publish events.

### Basic Assembly
* Provides simple validation in HCD and Assembly.
* Assembly shows how to use onTrackEvent to manage CommandService creation and loss of HCD.
* Code shows how to send a command while noticing if HCD is available.
* Simulates different commands that use the sleep functionality of HCD.
* Shows how to use CommandResponseManager to update a long-running command.
* Shows a "complex" command that uses CommandResponseManager queryFinalAll call.
* Shows how to subscribe to events and process events.

Includes standalone HCD tests and Assembly+HCD integration tests that start a container with both components.

## Moderate
### Moderate HCD
* Sleep worker is interruptable allowing sleep command to be cancelled.
* Supports command that will cancel the "long command".
* Uses validation code shared with Assembly.
* Uses "info" file that is shared between HCD and Assembly.

### Moderate Assembly
* Adds command to cancel "long command". Keeps track of long command runId.
* Uses validation code shared with Assembly.
* Uses "info" file that is shared between HCD and Assembly.

Includes standalone HCD tests and Assembly+HCD integration tests that start a container with both components.
Adds test to start a long command and cancel it.

## Full

### Full HCD
* Adds a worker monitor that tracks data allowing any sleep command to be cancelled.
* Sleep worker enhanced to work with worker monitor.
* Implements cancel "long command" using new functionality.

### Full Assembly
* Uses worker monitor to associate runIds with sub-commandIds.
* Imlements cancel "long command" using worker monitor.

Includes standalone HCD tests and Assembly+HCD integration tests that start a container with both components.
Integration test to start a long command and cancel it.

To be completed!