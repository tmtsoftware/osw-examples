package m1cs.segments.assembly

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.client.CommandServiceFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.CommandResponse._
import csw.prefix.models.Prefix
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.shared.SegmentCommands.ACTUATOR.ActuatorModes.TRACK
import m1cs.segments.shared.SegmentCommands.ACTUATOR.toActuator
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._

class SegmentsAssemblyIntTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike with Matchers {

  import frameworkTestKit._

  // Load the config to fetch prefix
  val assemConfig = ConfigFactory.load("SegmentsAssemblyStandalone.conf")
  val hcdConfig   = ConfigFactory.load("SegmentsHcdStandalone.conf")

  // Hard-coding HCD and Assembly prefixes because they are not easily available
  private val hcdPrefix                 = Prefix("M1CS.segmentsHCD")
  private val hcdConnection             = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))
  private val assemblyPrefix            = Prefix("M1CS.segmentsAssembly")
  private val assemblyConnection        = AkkaConnection(ComponentId(assemblyPrefix, ComponentType.Assembly))
  private implicit val timeout: Timeout = 5.seconds

  LoggingSystemFactory.forTestingOnly()
  val log = GenericLoggerFactory.getLogger

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Assembly used for all tests
    spawnStandalone(assemConfig)
    // Create the HCD here for all tests, may change
    spawnStandalone(hcdConfig)

    // For testing here
    //spawnHCD(hcdPrefix, (ctx, cswCtx) => MyHandlers(ctx, cswCtx))
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

  test("Assembly receives a command all the way to HCD") {
    val assemLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val hcdLocation   = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
    assemLocation.connection shouldBe assemblyConnection
    hcdLocation.connection shouldBe hcdConnection

    // Form the external command going to the Assembly
    val to    = toActuator(assemblyPrefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34) //.toSegment(SegmentId("A5"))
    val setup = to.asSetup

    val cs     = CommandServiceFactory.make(assemLocation)
    val result = Await.result(cs.submitAndWait(setup), 10.seconds)

    result shouldBe a[Completed]
  }

}
