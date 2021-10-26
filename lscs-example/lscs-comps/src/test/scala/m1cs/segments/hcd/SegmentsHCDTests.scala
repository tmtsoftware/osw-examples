package m1cs.segments.hcd

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.client.CommandServiceFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.CommandResponse.Completed
import csw.params.commands.Setup
import csw.prefix.models.Prefix
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.shared.SegmentCommands.ACTUATOR.ActuatorModes.TRACK
import m1cs.segments.shared.SegmentCommands.ACTUATOR.{toActuator, toCommand}
import m1cs.segments.shared.{A, HcdDirectCommand, HcdShutdown, SegmentId}
import m1cs.segments.streams.server.SocketServerStream
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration.*

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

    val simulatorExternal = testKit.system.settings.config.getBoolean("m1cs.simulatorExternal")
    if (!simulatorExternal) {
      println("Starting an external socket server")
      // Comment out this line to use an external server (scala or C version)
      new SocketServerStream()(testKit.internalSystem)
    }

    // uncomment if you want one HCD run for all tests
    spawnStandalone(config)
  }

  override def afterAll(): Unit = {
    frameworkTestKit.shutdown()
    testKit.shutdownTestKit()
  }

  test("HCD should be locatable using Location Service") {

    val akkaLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe hcdConnection
  }

  test("Try sending one command") {
    val hcdLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    // This simulates sending to Assembly

    val assemblySetup = toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).toSegment(SegmentId(A, 1)).asSetup
    log.info(s"Setup: $assemblySetup")

    // This simulates what the Assembly does to send to HCD - has received above Setup
    val hcdSetup:Setup = HcdDirectCommand.toHcdDirectCommand(prefix, assemblySetup)
    // Assembly creates an HCD setup from
    log.info(s"HCD Setup: $hcdSetup")
    val commandService = CommandServiceFactory.make(hcdLocation)
    var result         = Await.result(commandService.submitAndWait(hcdSetup), 5.seconds)
    result shouldBe a[Completed]

    result = Await.result(commandService.submitAndWait(HcdShutdown.toHcdShutdown(prefix)), 5.seconds)
    result shouldBe a[Completed]
  }

  test("Try sending command to All") {
    val hcdLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    // This simulates sending to Assembly -- default is ALL
    val assemblySetup = toActuator(prefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).asSetup
    log.info(s"Setup: $assemblySetup")

    // This simulates what the Assembly does to send to HCD
    val hcdSetup:Setup = HcdDirectCommand.toHcdDirectCommand(prefix, assemblySetup)
    log.info(s"HCD Setup: $hcdSetup")

    val commandService = CommandServiceFactory.make(hcdLocation)
    var result = Await.result(commandService.submitAndWait(hcdSetup), 5.seconds)
    result shouldBe a[Completed]

    result = Await.result(commandService.submitAndWait(HcdShutdown.toHcdShutdown(prefix)), 5.seconds)
    result shouldBe a[Completed]
  }
}
