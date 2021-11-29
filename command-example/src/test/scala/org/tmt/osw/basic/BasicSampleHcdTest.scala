package org.tmt.osw.basic

import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.params.commands.{CommandResponse, Setup}
import csw.params.core.generics.KeyType
import csw.params.core.models.ObsId
import csw.params.events.{Event, EventKey, EventName, SystemEvent}
import csw.prefix.models.{Prefix, Subsystem}
import csw.testkit.FrameworkTestKit
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.tmt.osw.basic.shared.SampleInfo.{hcdSleep, setSleepTime}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.*

//noinspection ScalaStyle
class BasicSampleHcdTest extends AnyFunSuite with BeforeAndAfterAll with Matchers {
  // Shared HCD connection val in all tests
  private val hcdConnection = AkkaConnection(ComponentId(Prefix(Subsystem.CSW, "samplehcd"), ComponentType.HCD))

  private val frameworkTestKit = FrameworkTestKit()

  //ScalaTestFrameworkTestKit(AlarmServer, EventServer) wit
  import frameworkTestKit.*

  override def beforeAll(): Unit = {
    frameworkTestKit.start(AlarmServer, EventServer)
    // Create one for all tests.  Could create new one in each test
    val _ = frameworkTestKit.spawnStandalone(com.typesafe.config.ConfigFactory.load("BasicSampleHcdStandalone.conf"))
  }

  // stops all services started by this testkit
  override protected def afterAll(): Unit = frameworkTestKit.shutdown()

  test("HCD should be locatable using Location Service") {
    val akkaLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe hcdConnection
  }

  test("should be able to subscribe to HCD events") {
    val counterEventKey = EventKey(Prefix("CSW.samplehcd"), EventName("HcdCounter"))
    val hcdCounterKey   = KeyType.IntKey.make("counter")

    //val eventService = eventServiceFactory.make(locationService)(actorSystem)
    val subscriber   = eventService.defaultSubscriber

    // wait for a bit to ensure HCD has started and published an event
    Thread.sleep(2000)

    val subscriptionEventList = mutable.ListBuffer[Event]()
    subscriber.subscribeCallback(Set(counterEventKey), { ev => subscriptionEventList.append(ev) })

    // Sleep for 5 seconds, to allow HCD to publish events
    Thread.sleep(5000)

    // Event publishing period is 2 seconds.
    // Expecting 3 events: first event on subscription
    // and two more events 2 and 4 seconds later.
    subscriptionEventList.toList.size shouldBe 3

    // extract counter values to a List for comparison
    val counterList = subscriptionEventList.toList.map {
      case sysEv: SystemEvent if sysEv.contains(hcdCounterKey) => sysEv(hcdCounterKey).head
      case _                                                   => -1
    }

    // we don't know exactly how long HCD has been running when this test runs,
    // so we don't know what the first value will be,
    // but we know we should get three consecutive numbers
    val expectedCounterList = (0 to 2).toList.map(_ + counterList.head)

    counterList shouldBe expectedCounterList
  }

  test("basic: should be able to send sleep command to HCD") {
    import org.tmt.osw.basic.shared.SampleInfo.*

    implicit val sleepCommandTimeout: Timeout = Timeout(10000.millis)

    // Construct Setup command
    val testPrefix: Prefix = Prefix("CSW.test")

    // Helper to get units set
    val setupCommand = setSleepTime(Setup(testPrefix, hcdSleep, Some(ObsId("2020A-001-123"))), 5000)

    val akkaLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    val hcd = CommandServiceFactory.make(akkaLocation)(actorSystem)
    // submit command and handle response
    val responseF = hcd.submitAndWait(setupCommand)

    Await.result(responseF, 10000.millis) shouldBe a[CommandResponse.Completed]
  }

  test("should get timeout exception if submit timeout is too small") {
    implicit val sleepCommandTimeout: Timeout = Timeout(500.millis)

    // Construct Setup command
    val testPrefix: Prefix = Prefix("CSW.test")

    val setupCommand = setSleepTime(Setup(testPrefix, hcdSleep, Some(ObsId("2020A-001-123"))), 5000)

    val akkaLocation = Await.result(locationService.resolve(hcdConnection, 10.seconds), 10.seconds).get

    val hcd = CommandServiceFactory.make(akkaLocation)(actorSystem)

    // submit command and handle response
    intercept[java.util.concurrent.TimeoutException] {
      val responseF = hcd.submitAndWait(setupCommand)(sleepCommandTimeout)
      Await.result(responseF, 10000.millis) shouldBe a[CommandResponse.Completed]
    }
  }
}
