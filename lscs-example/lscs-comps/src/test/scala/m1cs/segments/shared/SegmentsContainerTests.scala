package m1cs.segments.shared

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.prefix.models.Prefix
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration._

class SegmentsContainerTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike {
  import frameworkTestKit._

  // Load the config to fetch prefix
  val config = ConfigFactory.load("SegmentsContainer.conf")

  // Hard-coding HCD and Assembly prefixes because they are not easily available
  private val hcdPrefix          = Prefix("M1CS.segmentsHCD")
  private val hcdConnection      = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))
  private val assemblyPrefix     = Prefix("M1CS.segmentsAssembly")
  private val assemblyConnection = AkkaConnection(ComponentId(assemblyPrefix, ComponentType.Assembly))

  // Used for waiting for submits
  private implicit val timeout: Timeout = 10.seconds

  LoggingSystemFactory.forTestingOnly()
  val log = GenericLoggerFactory.getLogger

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one HCD run for all tests
    spawnContainer(config) // I guess this works, beforeAll runs after constructor, which is logical
  }

  override def afterAll(): Unit = {
    frameworkTestKit.shutdown()
  }

  test("HCD should be locatable using Location Service") {

    val akkaLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe hcdConnection
  }

  /*
  test("Try sending one command") {
    val hcdLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    val commandService = CommandServiceFactory.make(hcdLocation)
    // This simulates sending to Assembly
    val setup = toActuator(prefix, Set(1,3)).withMode(TRACK).withTarget(target=22.34).toSegment(SegmentId(A,5)).asSetup
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
   */
}
