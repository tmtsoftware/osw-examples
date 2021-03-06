package org.tmt.osw.full

import akka.actor.typed.ActorSystem
import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.params.commands.CommandResponse.{Cancelled, Completed, Started}
import csw.params.commands.{CommandResponse, Setup}
import csw.params.core.generics.{KeyType, Parameter}
import csw.params.core.models.{ObsId, Units}
import csw.params.events.{Event, EventKey, EventName, SystemEvent}
import csw.prefix.models.{Prefix, Subsystem}
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer, LocationServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.tmt.osw.full.shared.SampleInfo.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.collection.mutable
import scala.concurrent.Await

//noinspection ScalaStyle
//#setup
class FullSampleHcdTest
    extends ScalaTestFrameworkTestKit(AlarmServer, EventServer, LocationServer)
    with AnyFunSuiteLike
    with BeforeAndAfterEach {
  import frameworkTestKit.*

  override def beforeAll(): Unit = {
    super.beforeAll()
    val _ = spawnStandalone(com.typesafe.config.ConfigFactory.load("FullSampleHcdStandalone.conf"))
  }

  import scala.concurrent.duration.*
  test("HCD should be locatable using Location Service") {
    val connection   = AkkaConnection(ComponentId(Prefix(Subsystem.CSW, "samplehcd"), ComponentType.HCD))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe connection
  }
  //#setup

  //#subscribe
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
  //#subscribe

  //#submit
  implicit val typedActorSystem: ActorSystem[?] = actorSystem
  test("full: should be able to send sleep command to HCD") {
    import scala.concurrent.duration.*
    implicit val sleepCommandTimeout: Timeout = Timeout(10000.millis)

    // Construct Setup command
    val sleepTimeParam: Parameter[Long] = sleepTimeKey.set(4000).withUnits(Units.millisecond)
    val setupCommand                    = Setup(testPrefix, hcdSleep, Some(ObsId("2020A-001-123"))).add(sleepTimeParam)

    val connection = AkkaConnection(ComponentId(Prefix(Subsystem.CSW, "samplehcd"), ComponentType.HCD))

    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val hcd = CommandServiceFactory.make(akkaLocation)
    // submit command and handle response
    val responseF = hcd.submitAndWait(setupCommand)

    Await.result(responseF, 10000.millis) shouldBe a[CommandResponse.Completed]
  }
  //#submit

  test("should handle long command and cancel") {
    implicit val timeout: Timeout = 10.seconds
    val connection                = AkkaConnection(ComponentId(Prefix(Subsystem.CSW, "samplehcd"), ComponentType.HCD))
    val akkaLocation              = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val hcdCS = CommandServiceFactory.make(akkaLocation)

    // Start a long command
    val longResponse = Await.result(hcdCS.submit(Setup(testPrefix, hcdLong, None)), 10.seconds)
    longResponse shouldBe a[Started]

    // Wait 2 seconds, then cancel
    Thread.sleep(2000)
    val cancelSetup    = Setup(testPrefix, hcdCancelLong, None).add(cancelKey.set(longResponse.runId.id))
    val cancelResponse = Await.result(hcdCS.submitAndWait(cancelSetup), 10.seconds)
    cancelResponse shouldBe a[Completed]

    val finalResponse = Await.result(hcdCS.queryFinal(longResponse.runId), 10.seconds)
    finalResponse shouldBe a[Cancelled]
  }

  //#exception
  test("should get timeout exception if submit timeout is too small") {
    import scala.concurrent.duration.*
    implicit val sleepCommandTimeout: Timeout = Timeout(1000.millis)

    // Construct Setup command
    val sleepTimeParam: Parameter[Long] = sleepTimeKey.set(4000).withUnits(Units.millisecond)
    val setupCommand                    = Setup(testPrefix, hcdSleep, Some(ObsId("2020A-001-123"))).add(sleepTimeParam)

    val connection = AkkaConnection(ComponentId(Prefix(Subsystem.CSW, "samplehcd"), ComponentType.HCD))

    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val hcd = CommandServiceFactory.make(akkaLocation)

    // submit command and handle response
    intercept[java.util.concurrent.TimeoutException] {
      val responseF = hcd.submitAndWait(setupCommand)
      Await.result(responseF, 10000.millis) shouldBe a[CommandResponse.Completed]
    }
  }
  //#exception
}
