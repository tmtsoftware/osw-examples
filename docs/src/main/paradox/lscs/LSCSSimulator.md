# LSCS Simulator and JPL Simulator

The JVM simulator server is implemented in the `SocketServerStream` class.
It implements the same low level protocol as the JPL C library, located in the `m1cs-lscs-sim` subproject.
The main difference in the current version is that the Scala version of the server supports a "DELAY ms" command, 
which is used to simulate a command that takes `ms` milliseconds to complete.
For testing, it also takes a `q` command that causes the server to shut down.

## Starting the Socket Server

For testing, the JVM socket server can be started with:

```scala
new SocketServerStream()(actorSystem)
```

The C version of the server can be started with the `CmdSrvSim` command.
See the README.md in the `m1cs-lscs-sim` subproject for details on the C version.

## Creating a Socket Client

In order to create a socket client, you need either an ActorSystem[SpawnProtocol] or an ActorContext,
since the client needs to create an actor internally, in order to manage responses.

To use an ActorSystem, use the `SocketClientStream.withSystem()` method:

```scala
implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "SocketServerStream")
  
val client1 = SocketClientStream.withSystem("client1")
```

If you are already in an actor, you can use the ActorContext instead (used only in the same thread to create a child actor):

```scala
    Behaviors.setup[Command] { ctx =>
      val io = SocketClientStream(ctx, name)
      // ...
    }
```

Both methods have optional arguments for the host and port to use to connect to the server.
The deault is localhost:8023, which is the same default used by the C library.

## Sending Messages to the Server

The class used to access the socket server is `SocketClientStream` and it can talk to either the C or the 
Scala version of the server. 

The API for the client is basically just: `send(message)`, which returns a non-blocking Future response:

Scala
: @@snip [Send / Receive]($lscs.base$/lscsComps/src/test/scala/m1cs/segments/streams/SocketClientStreamTest.scala) { #socketClientWithSystem }

The type of the message sent is `SocketMessage`, which has the same layout as the JPL C socket message:

Scala
: @@snip [SocketMessage]($lscs.base$/lscsComps/src/main/scala/m1cs/segments/streams/shared/SocketMessage.scala) { #SocketMessage }

In most cases you don't need to provide the message header, since it is generated automatically from the message text.
It contains information, such as the size of the message and a sequence number, which is also returned as part of the response.
The header is represented by the MsgHdr class, below:

Scala
: @@snip [MsgHdr]($lscs.base$/lscsComps/src/main/scala/m1cs/segments/streams/shared/SocketMessage.scala) { #MsgHdr }

The type of the response to sending a command is the same (SocketMessage).

## Stopping the Server

For testing, you can use the `terminate()` method on the client to cause the server to shutdown, ending the connection.
This is not supported in the C version.

## Wire Format

The wire format for a socket message is based on the C library version and uses the same header and byte order.