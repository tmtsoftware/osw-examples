package m1cs.segments.streams

import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import m1cs.segments.streams.client.SocketClientStream
import m1cs.segments.streams.server.SocketServerStream
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SocketServerStreamTest extends AnyFunSuite {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "SocketServerStream")
  import system._
  implicit val timout: Timeout = Timeout(5.seconds)

  // Start the server
  // XXX TODO FIXME: Use typed system

  test("Basic test") {
    val ss = new SocketServerStream()(system.toClassic)

    val client1 = new SocketClientStream("client1")

    def showResult(id: Int, s: String): String = {
      val result = s"$id: $s"
      println(result)
      result
    }

    val f1 = client1.send(1, "DELAY 2000").map(showResult(1, _))

    val result = Await.result(f1, timout.duration)
    println(s"Result: $result")

    val f3 = client1.send(3, "DELAY 2000").map(showResult(3, _))

    val result3 = Await.result(f3, timout.duration)
    println(s"Result3: $result3")

    val f2      = client1.send(2, "BYE").map(showResult(3, _))
    val result2 = Await.result(f2, timout.duration)
    println(s"Result2: $result2")

    client1.terminate()

    val f4 = client1.send(4, "DELAY 2000").map(showResult(4, _))

    val result4 = Await.result(f4, timout.duration)
    println(s"Result4: $result4")
  }

}
