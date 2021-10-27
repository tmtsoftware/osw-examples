package m1cs.segments.assembly

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.client.CommandServiceFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.CommandResponse.*
import csw.params.commands.Setup
import csw.prefix.models.Prefix
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.shared.SegmentCommands.ACTUATOR.ActuatorModes.TRACK
import m1cs.segments.shared.SegmentCommands.ACTUATOR.toActuator
import m1cs.segments.shared.{HcdShutdown, SegmentId}
import m1cs.segments.streams.server.SocketServerStream
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.*

class SegmentsAssemblyIntTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike with Matchers {
  import frameworkTestKit.*

  private val testKit = ActorTestKit()

  // Load the config to fetch prefix
  private val assemblyConfig = ConfigFactory.load("SegmentsAssemblyStandalone.conf")
  private val hcdConfig   = ConfigFactory.load("SegmentsHcdStandalone.conf")
  private val assemConfig = ConfigFactory.load("SegmentsAssemblyStandalone.conf")
  private val hcdConfig   = ConfigFactory.load("SegmentsHcdStandalone.conf")

  // Hard-coding HCD and Assembly prefixes because they are not easily available
  private val clientPrefix              = Prefix("ESW.client")
  private val hcdPrefix                 = Prefix("M1CS.segmentsHCD")
  private val hcdConnection             = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))
  private val assemblyPrefix            = Prefix("M1CS.segmentsAssembly")
  private val assemblyConnection        = AkkaConnection(ComponentId(assemblyPrefix, ComponentType.Assembly))
  private implicit val timeout: Timeout = 5.seconds

  private val shutdownSetup             = Setup(clientPrefix, HcdShutdown.shutdownCommand)

  LoggingSystemFactory.forTestingOnly()
  private val log = GenericLoggerFactory.getLogger

  private val simulatorExternal = testKit.system.settings.config.getBoolean("m1cs.simulatorExternal")
  private val maybeSocketServerStream = if (!simulatorExternal) {
    log.info(">>>>>STARTING an external socket server<<<<")
    // Comment out this line to use an external server (scala or C version)
    Some(new SocketServerStream()(testKit.internalSystem))
  }
  else None

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Assembly used for all tests
    spawnStandalone(assemblyConfig)
    // Create the HCD here for all tests, may change
    spawnStandalone(hcdConfig)
  }

  override def afterAll(): Unit = {
    val assemblyLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val cs     = CommandServiceFactory.make(assemblyLocation)
    log.info("Shutting down segments")
    Await.ready(cs.submitAndWait(shutdownSetup), 10.seconds)

    maybeSocketServerStream.foreach(_.terminate())
    super.afterAll()
  }

  test("Assembly should be locatable using Location Service") {
    val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe assemblyConnection
  }

  test("Assembly should see HCD") {
    val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    akkaLocation.connection shouldBe assemblyConnection

    val hcdLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
    hcdLocation.connection shouldBe hcdConnection

    Thread.sleep(2000)
  }

  test("Assembly receives a command all the way to HCD -- One Segment") {
    val assemblyLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val hcdLocation   = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
    assemblyLocation.connection shouldBe assemblyConnection
    hcdLocation.connection shouldBe hcdConnection

    // Form the external command going to the Assembly
    val to    = toActuator(assemblyPrefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).toSegment(SegmentId("A5"))
    val setup = to.asSetup

    val cs     = CommandServiceFactory.make(assemblyLocation)
    val result = Await.result(cs.submitAndWait(setup), 10.seconds)

    result shouldBe a[Completed]
  }

  test("Assembly receives a command all the way to HCD -- All Segments") {
    val assemblyLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val hcdLocation   = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
    assemblyLocation.connection shouldBe assemblyConnection
    hcdLocation.connection shouldBe hcdConnection

    // Form the external command going to the Assembly
    val setup    = toActuator(assemblyPrefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).asSetup

    val cs     = CommandServiceFactory.make(assemblyLocation)
    val result = Await.result(cs.submitAndWait(setup), 10.seconds)

    result shouldBe a[Completed]
  }

}
