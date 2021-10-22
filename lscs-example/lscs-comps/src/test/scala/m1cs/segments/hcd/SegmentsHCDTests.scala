package m1cs.segments.hcd

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.Settings
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.client.CommandServiceFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.CommandResponse.Completed
import csw.prefix.models.Prefix
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.shared.HcdCommands.{toAllSegments, toOneSegment}
import m1cs.segments.shared.SegmentCommands.ACTUATOR.ActuatorModes.TRACK
import m1cs.segments.shared.SegmentCommands.ACTUATOR.{toActuator, toActuatorCommand}
import m1cs.segments.shared.SegmentCommands.{AllSegments, OneSegment}
import m1cs.segments.shared.{A, SegmentId}
import m1cs.segments.streams.server.SocketServerStream
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration._

class SegmentsHCDTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike {
  import frameworkTestKit._

  private val testKit = ActorTestKit()

  // Load the config to fetch prefix
  val config = ConfigFactory.load("SegmentsHcdStandalone.conf")

  private val prefix: Prefix = Prefix(config.getString("prefix")) // TEMP
  private val hcdConnection  = AkkaConnection(ComponentId(prefix, ComponentType.HCD))

  // Used for waiting for submits
  private implicit val timeout: Timeout = 10.seconds

  LoggingSystemFactory.forTestingOnly()
  val log = GenericLoggerFactory.getLogger

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one HCD run for all tests
    spawnStandalone(config)
  }

  override def afterAll(): Unit = {
    frameworkTestKit.shutdown()
  }

  test("HCD should be locatable using Location Service") {

    val akkaLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe hcdConnection
  }

  test("Try sending one command") {
    val hcdLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    val commandService = CommandServiceFactory.make(hcdLocation)
    // This simulates sending to Assembly
    val setup = toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).toSegment(SegmentId(A, 5)).asSetup
    println(s"Setup: $setup")
    // This simulates what the Assembly does to send to HCD
    val command = toActuatorCommand(setup)
    val hcdSetup = command match {
      case AllSegments(command) =>
        toAllSegments(prefix, command)
      case OneSegment(segmentId, command) =>
        toOneSegment(prefix, segmentId, command)
    }

    // Assembly creates an HCD setup from
    println(s"HCD Setup: $hcdSetup")

    val result = Await.result(commandService.submitAndWait(hcdSetup), 5.seconds)
    result shouldBe a[Completed]
  }

  test("Try sending command to All") {
    val hcdLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    val commandService = CommandServiceFactory.make(hcdLocation)
    // This simulates sending to Assembly -- default is ALL
    val setup = toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).asSetup
    println(s"Setup: $setup")
    // This simulates what the Assembly does to send to HCD
    val command = toActuatorCommand(setup)
    val hcdSetup = command match {
      case AllSegments(command) =>
        toAllSegments(prefix, command)
      case OneSegment(segmentId, command) =>
        toOneSegment(prefix, segmentId, command)
    }

    // Assembly creates an HCD setup from
    println(s"HCD Setup: $hcdSetup")

    val result = Await.result(commandService.submitAndWait(hcdSetup), 5.seconds)
    result shouldBe a[Completed]
  }

  test("Try sending one command - external") {
    new SocketServerStream()(testKit.internalSystem)

    val hcdLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    val commandService = CommandServiceFactory.make(hcdLocation)
    // This simulates sending to Assembly
    val setup = toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).toSegment(SegmentId(A, 5)).asSetup
    println(s"Setup: $setup")
    // This simulates what the Assembly does to send to HCD
    val command = toActuatorCommand(setup)
    val hcdSetup = command match {
      case AllSegments(command) =>
        toAllSegments(prefix, command)
      case OneSegment(segmentId, command) =>
        toOneSegment(prefix, segmentId, command)
    }

    // Assembly creates an HCD setup from
    println(s"HCD Setup: $hcdSetup")

    val result = Await.result(commandService.submitAndWait(hcdSetup), 5.seconds)
    result shouldBe a[Completed]
  }

  test("Try sending command to All - external") {
    new SocketServerStream()(testKit.internalSystem)

    val hcdLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    val commandService = CommandServiceFactory.make(hcdLocation)
    // This simulates sending to Assembly -- default is ALL
    val setup = toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).asSetup
    println(s"Setup: $setup")
    // This simulates what the Assembly does to send to HCD
    val command = toActuatorCommand(setup)
    val hcdSetup = command match {
      case AllSegments(command) =>
        toAllSegments(prefix, command)
      case OneSegment(segmentId, command) =>
        toOneSegment(prefix, segmentId, command)
    }

    // Assembly creates an HCD setup from
    println(s"HCD Setup: $hcdSetup")

    val result = Await.result(commandService.submitAndWait(hcdSetup)(25.seconds), 25.seconds)
    result shouldBe a[Completed]
  }
}
