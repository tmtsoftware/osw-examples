package m1cs.segments.shared

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
import m1cs.segments.segcommands.ACTUATOR.ActuatorModes.TRACK
import m1cs.segments.segcommands.{ACTUATOR, SegmentId}
import m1cs.segments.streams.server.SocketServerStream
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration.*

class SegmentsContainerTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike {
  import frameworkTestKit.*

  private val testKit = ActorTestKit()

  // Load the config to fetch prefix
  private val config = ConfigFactory.load("SegmentsContainer.conf")

  // Hard-coding HCD and Assembly prefixes because they are not easily available
  private val clientPrefix              = Prefix("ESW.client")
  private val hcdPrefix                 = Prefix("M1CS.segmentsHCD")
  private val hcdConnection             = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))
  private val assemblyPrefix            = Prefix("M1CS.segmentsAssembly")
  private val assemblyConnection        = AkkaConnection(ComponentId(assemblyPrefix, ComponentType.Assembly))
  private implicit val timeout: Timeout = 5.seconds

  private val shutdownSetup = Setup(clientPrefix, HcdShutdown.shutdownCommand)

  // Used for waiting for submits
//  private implicit val timeout: Timeout = 10.seconds

  LoggingSystemFactory.forTestingOnly()
  private val log = GenericLoggerFactory.getLogger

  log.info(">>>>>STARTING socket server -- add -DsimulatorHost for external <<<<")
  val socketServer = new SocketServerStream()(testKit.internalSystem)

  override def beforeAll(): Unit = {
    super.beforeAll()
    val _ = spawnContainer(config) // I guess this works, beforeAll runs after constructor, which is logical
  }

  override def afterAll(): Unit = {
    val assemblyLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val cs               = CommandServiceFactory.make(assemblyLocation)
    log.info("Shutting down segments")
    Await.ready(cs.submitAndWait(shutdownSetup), 10.seconds)

    socketServer.terminate()
    super.afterAll()
  }

  test("HCD and Assembly should be locatable using Location Service") {
    val akkaAssemblyLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    akkaAssemblyLocation.connection shouldBe assemblyConnection

    val akkaHcdLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
    akkaHcdLocation.connection shouldBe hcdConnection
  }

  test("Try sending one command to assembly to HCD to Segment") {
    val assemblyLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get

    // Form the external command going to the Assembly
    val setup =
      ACTUATOR.toActuator(clientPrefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).toSegment(SegmentId("A5")).asSetup
    val cs     = CommandServiceFactory.make(assemblyLocation)
    val result = Await.result(cs.submitAndWait(setup), 10.seconds)

    result shouldBe a[Completed]
  }
}
