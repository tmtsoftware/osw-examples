# M1CS Lower Segment Box Server Simulator 

This project contains a simulator for the lower segment box command server (CmdSrvSim), a command line test client (CmdTool), as well as a copy of the network services library (libnet.a).

The Command Server Simulator's functionality is limited to: 
1. Accepting client connections (up to 20 at a time).
2. Receiving generic CmdMsg's and returning RspMsg's.
3. Currently it doesn't parse the command syntax.  The RspMsg will be formated as: <first word>": Completed."

CmdSrvSim currently processes CmdMsg's in the order they are received.  Future versions will add:
1. Integrating the LSCS command parser.
2. Multithreading the command processing and randomizing the RspMsg delay.
3. Returning the various DataMsg's for the commands that would generate them.

CmdTool provides a basic command line interface that will allow the user to enter commands from the keyboard, and print out the RspMsg results.

## Building the Server

To build both the server and client, run:

```
make
```

## Running the Server

To start the server, run:

```
$ bin/CmdSrvSim
...
CmdSrvSim: Listening on socket 3...
CmdSrvSim: Connection accepted...
```

The --help option displays the command line options:

```
Usage: CmdSrvSim [-d] [-s server]

  -d               Print additional debugging info 
  -s <server>      The server to connect as
```

## Running the command line client

The command line client requires the server to be running.
Then:
```
$ bin/CmdTool -h localhost -p 3
localhost > 
```

The --help option displays the commmand line options:

```
Usage: CmdTool [-d] [-h hostname] [-s server] [-p port]

  -d               Print additional debugging info 
  -h <hostname>    The host name or ip address of server.
  -s <server>      The server endpoint to connect.
  -p <port>        The port number to use for the server. 
```

