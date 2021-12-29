package m1cs.segments.deploy

import java.net.InetAddress
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.event.api.scaladsl.EventService
import csw.event.client.EventServiceFactory
import csw.location.api.models.{ComponentId, ComponentType}
import csw.location.api.models.Connection.AkkaConnection
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.CommandResponse.{Completed, SubmitResponse}
import csw.prefix.models.Prefix
import m1cs.segments.segcommands.Common.ALL_ACTUATORS
import m1cs.segments.segcommands.{CFG_CUR_LOOP, SegmentId}
import m1cs.segments.streams.server.SocketServerStream
import org.scalatest.Ignore
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.*

//noinspection DuplicatedCode,SameParameterValue

// A client to test locating and communicating with the M1CS HCS and assembly,
// which are expected to be running externally.
// Before running this test:
// 1. Uncomment the Ignore line below,
// 2. Start the SocketServerStream app
// 3. Run the assembly and HCD (using the stage, assembly or graalvm version)
@Ignore
class M1csTestClient extends AnyFunSuite with Matchers {
  import m1cs.segments.segcommands.ACTUATOR
  import m1cs.segments.segcommands.ACTUATOR.ActuatorModes.*

  private val host = InetAddress.getLocalHost.getHostName
  implicit val typedSystem: ActorSystem[SpawnProtocol.Command] =
    ActorSystemFactory.remote(SpawnProtocol(), "M1CSAssemblyClientSystem")
  import typedSystem.executionContext

  LoggingSystemFactory.start("M1CSAssemblyClient", "0.1", host, typedSystem)
  private val log = GenericLoggerFactory.getLogger
  log.info("Starting M1CSAssemblyClient")

  private val locationService = HttpLocationServiceFactory.makeLocalClient(typedSystem)

  implicit val timeout: Timeout = Timeout(10.seconds)

  lazy val eventService: EventService = {
    new EventServiceFactory().make(locationService)(typedSystem)
  }

  // Hard-coding HCD and Assembly prefixes because they are not easily available
  private val hcdPrefix                 = Prefix("M1CS.segmentsHCD")
  private val hcdConnection             = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))
  private val assemblyPrefix            = Prefix("M1CS.segmentsAssembly")
  private val assemblyConnection        = AkkaConnection(ComponentId(assemblyPrefix, ComponentType.Assembly))

  // NOTE: The socket server needs to be running externally, before the container is started!
//  private val socketServer = new SocketServerStream()(typedSystem)

  test("Assembly should be locatable using Location Service") {
    val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe assemblyConnection
  }

  test("Assembly should see HCD") {
    val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    akkaLocation.connection shouldBe assemblyConnection

    val hcdLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
    hcdLocation.connection shouldBe hcdConnection

    Thread.sleep(1000)
  }

  test("Assembly receives a command all the way to HCD -- One Segment") {
    val assemblyLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val hcdLocation      = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
    assemblyLocation.connection shouldBe assemblyConnection
    hcdLocation.connection shouldBe hcdConnection

    // Form the external command going to the Assembly
    val to    = ACTUATOR.toActuator(assemblyPrefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).toSegment(SegmentId("A5"))
    val setup = to.asSetup

    val cs     = CommandServiceFactory.make(assemblyLocation)
    val result = Await.result(cs.submitAndWait(setup), 10.seconds)

    result shouldBe a[Completed]
  }

  test("Assembly receives a command all the way to HCD -- All Segments") {
    val assemblyLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val hcdLocation      = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
    assemblyLocation.connection shouldBe assemblyConnection
    hcdLocation.connection shouldBe hcdConnection

    // Form the external command going to the Assembly
    val setup = ACTUATOR.toActuator(assemblyPrefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).asSetup

    val cs     = CommandServiceFactory.make(assemblyLocation)
    val result = Await.result(cs.submitAndWait(setup), 10.seconds)

    result shouldBe a[Completed]
  }

  test("Assembly receives multiple commands all the way to HCD -- All Segments") {
    import m1cs.segments.segcommands.CFG_CUR_LOOP.CfgCurLoopMotor.*

    val assemblyLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val hcdLocation      = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
    assemblyLocation.connection shouldBe assemblyConnection
    hcdLocation.connection shouldBe hcdConnection

    // Form the external command going to the Assembly
    val setup1 = ACTUATOR.toActuator(assemblyPrefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).asSetup

    val setup2 = CFG_CUR_LOOP.toCfgActCurLoop(assemblyPrefix, ALL_ACTUATORS, SNUB).withCtrlParams(Array(1.2, 3.4, 5.6)).asSetup

    val cs = CommandServiceFactory.make(assemblyLocation)

    val result1 = cs.submitAndWait(setup1)
    val result2 = cs.submitAndWait(setup2)

    result1.foreach { r =>
      r shouldBe a[Completed]
      println(">>>>>>>>>>RESULT 1 Completed<<<<<<<<<<<")
    }

    result2.foreach { r =>
      r shouldBe a[Completed]
      println("********** RESULT 2 Completed **********")
    }

    TestProbe[SubmitResponse]().expectNoMessage(3000.milli)

  }

}
