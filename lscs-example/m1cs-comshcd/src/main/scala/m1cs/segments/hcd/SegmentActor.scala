package m1cs.segments.hcd

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import csw.logging.api.scaladsl.Logger
import m1cs.segments.shared.SegmentId

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.util.Random

object SegmentActor {
  private type SeqNo      = Int // JPL Sequence Number
  private type WaiterList = SizedList[(SeqNo, ActorRef[SegmentActor.Response])]

  sealed trait Command //extends akka.actor.NoSerializationVerificationNeeded
  case class Send(commandName: String, args: String, replyTo: ActorRef[SegmentActor.Response])                               extends Command
  case class SendWithTime(commandName: String, args: String, time: FiniteDuration, replyTo: ActorRef[SegmentActor.Response]) extends Command
  case class CommandFinished(commandName: String, commandId: Int)                                                            extends Command
  case object Unknown                                                                                                        extends Command

  sealed trait Response {
    val seqNo: Int
    val segmentId: SegmentId
  }

  case class Started(commandName: String, seqNo: Int, segmentId: SegmentId)                extends Response
  case class Processing(commandName: String, seqNo: Int, segmentId: SegmentId)             extends Response
  case class Completed(commandName: String, seqNo: Int, segmentId: SegmentId)              extends Response
  case class Error(commandName: String, seqNo: Int, segmentId: SegmentId, message: String) extends Response

  // The following is temp hack for testing error
  val ERROR_SEG_ID: Int          = 6
  val ERROR_COMMAND_NAME: String = "ERROR"

  def apply(segmentId: SegmentId, log: Logger, waiterSize: Int = 10): Behavior[Command] = {

    def handle(waiterList: WaiterList, nextCommandId: Int): Behavior[Command] = {
      Behaviors.withTimers { timers =>
        Behaviors.receiveMessage {
          case Send(commandName, _, replyTo) =>
            val delay = getRandomDelay
            log.debug(s"Starting: $segmentId:$commandName:$nextCommandId with delay: $delay.")
            timers.startSingleTimer(CommandFinished(commandName, nextCommandId), delay)
            if (delay.toMillis > 1000) {
              replyTo ! SegmentActor.Started(commandName, nextCommandId, segmentId)
            }
            handle(waiterList.append((nextCommandId, replyTo)), nextCommandId + 1)
          case SendWithTime(commandName, _, delay, replyTo) =>
            log.debug(s"Starting: $segmentId:$commandName with delay:$delay")
            timers.startSingleTimer(CommandFinished(commandName, nextCommandId), delay)
            if (delay.toMillis > 1000) {
              replyTo ! SegmentActor.Started(commandName, nextCommandId, segmentId)
            }
            handle(waiterList.append((nextCommandId, replyTo)), nextCommandId + 1)
          case CommandFinished(commandName, commandId) =>
            log.debug(s"Finished: $segmentId:$commandName:$commandId")
            waiterList.query(_._1 == commandId) match {
              case Some(entry) =>
                // Special check to make error
                if (segmentId.number == ERROR_SEG_ID && commandName == ERROR_COMMAND_NAME) {
                  val errorMessage = "Fake Error Message"
                  log.error(s"Command: $commandName on segment: $segmentId produced ERROR with message: $errorMessage")
                  entry._2 ! SegmentActor.Error(commandName, commandId, segmentId, errorMessage)
                }
                else {
                  entry._2 ! SegmentActor.Completed(commandName, commandId, segmentId)
                }
                handle(waiterList.remove(_._1 == commandId), nextCommandId)
              case None =>
                log.error(s"Did not find an entry for: $commandName and commandId: $commandId")
                Behaviors.same
            }
          case SegmentActor.Unknown =>
            Behaviors.unhandled
        }
      }
    }

    handle(new WaiterList(waiterSize), nextCommandId = 1)
  }

  private def getRandomDelay: FiniteDuration = FiniteDuration(Random.between(4, 1200), MILLISECONDS)

  /**
   * This is a specialized list that will only keep a maximum number of elements
   * @param max size of list to retain
   * @param initList the list can be initialized with some values
   * @tparam T the type of elements in the list
   */
  private class SizedList[T](max: Int, initList: List[T] = List.empty) {
    val list: ListBuffer[T] = ListBuffer() ++= initList

    def append(sr: T): SizedList[T] = {
      // If the list is at the maximum, remove 1 and add the new one
      if (list.size == max) {
        list.dropInPlace(1)
      }
      list.append(sr)
      this
    }

    def remove(f: T => Boolean): SizedList[T] = {
      val newList = list.filterNot(f)
      new SizedList(max, newList.toList)
    }

    // Runs the predicate argument against each member of the list and returns first
    def query(f: T => Boolean): Option[T] = list.find(f)

    // Runs the function for every member of the list
    def foreach[U](f: T => U): Unit = list.foreach(f)

    override def toString: String = s"SizedList(${list.toString()})"
  }

}
