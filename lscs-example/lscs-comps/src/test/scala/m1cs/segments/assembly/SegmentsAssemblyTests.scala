package m1cs.segments.assembly

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.client.CommandServiceFactory
import csw.command.client.models.framework.LocationServiceUsage
import csw.framework.scaladsl.DefaultComponentHandlers
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.CommandResponse.{Completed, SubmitResponse}
import csw.params.commands.{ControlCommand, Setup}
import csw.params.core.models.Id
import csw.prefix.models.Prefix
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import m1cs.segments.shared.{HcdDirectCommand, HcdShutdown}
import m1cs.segments.segcommands.SegmentId
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.*

class SegmentsAssemblyTests extends ScalaTestFrameworkTestKit() with AnyFunSuiteLike with Matchers {
  import frameworkTestKit.*
  import m1cs.segments.segcommands.ACTUATOR
  import m1cs.segments.segcommands.ACTUATOR.ActuatorModes.*

  // Load the config to fetch prefix
  private val config = ConfigFactory.load("SegmentsAssemblyStandalone.conf")

  // Hard-coding HCD and Assembly prefixes because they are not easily available
  private val clientPrefix              = Prefix("ESW.client")
  private val hcdPrefix                 = Prefix("M1CS.segmentsHCD")
  private val hcdConnection             = AkkaConnection(ComponentId(hcdPrefix, ComponentType.HCD))
  private val assemblyPrefix            = Prefix("M1CS.segmentsAssembly")
  private val assemblyConnection        = AkkaConnection(ComponentId(assemblyPrefix, ComponentType.Assembly))
  private implicit val timeout: Timeout = 5.seconds

  private val lastHcdCommands = List.empty[(Id, Setup)]

  LoggingSystemFactory.forTestingOnly()
  private val log = GenericLoggerFactory.getLogger

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one Assembly run for all tests
    spawnStandalone(config)

    // For testing here
//    spawnHCD(hcdPrefix, (ctx, cswCtx) => MyHandlers(ctx, cswCtx))
  }

  override def afterAll(): Unit = {
    val assemLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
    val cs            = CommandServiceFactory.make(assemLocation)
    log.info("Shutting down segments")
    val shutdownSetup = Setup(clientPrefix, HcdShutdown.shutdownCommand)
    Await.ready(cs.submitAndWait(shutdownSetup), 10.seconds)
    super.afterAll()
  }

  test("Assembly should be locatable using Location Service") {
    val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe assemblyConnection
  }

  // XXX HCD was not started!
//  test("Assembly should see HCD") {
//    val akkaLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get
//    akkaLocation.connection shouldBe assemblyConnection
//
//    val hcdLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get
//    hcdLocation.connection shouldBe hcdConnection
//
//    Thread.sleep(2000)
//  }

  trait onSumbiter {
    def onSubmit(runId: Id, command: ControlCommand): SubmitResponse
  }

  test("Assembly receives a command") {
    // Start the test HCD here and wait
    spawnHCD(
      hcdPrefix,
      (ctx, cswCtx) =>
        new DefaultComponentHandlers(ctx, cswCtx) {
          override def onSubmit(id: Id, controlCommand: ControlCommand): SubmitResponse = {
            log.info(s">>>>>>>>>>>>>>>>>>>>>>>onSubmit actually called: $controlCommand")
            controlCommand.commandName shouldBe HcdDirectCommand.lscsDirectCommand
            Completed(id)
          }
        },
      LocationServiceUsage.RegisterAndTrackServices
    )
    Await.ready(locationService.resolve(hcdConnection, 10.seconds), 10.seconds)

    val assLocation = Await.result(locationService.resolve(assemblyConnection, 10.seconds), 10.seconds).get

    // Form the external command going to the Assembly
    val setup =
      ACTUATOR.toActuator(assemblyPrefix, Set(1, 3)).withMode(TRACK).withTarget(target = 22.34).toSegment(SegmentId("A5")).asSetup

    val cs     = CommandServiceFactory.make(assLocation)
    val result = Await.result(cs.submitAndWait(setup), 10.seconds)

    result shouldBe a[Completed]
    log.info(s"Last Command: $lastHcdCommands")

  }

}
