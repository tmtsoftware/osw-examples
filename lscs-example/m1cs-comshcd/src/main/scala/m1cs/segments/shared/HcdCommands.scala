package m1cs.segments.shared

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.ObsId
import csw.prefix.models.Prefix
import SegmentCommands.{ALL_SEGMENTS, AllCommands, segmentIdKey}

case object HcdCommands {

  val lscsCommandKey: Key[String] = KeyType.StringKey.make("lscsCommand")

  /**
   * Creates a Setup with a formed LSCS command for processing in HCD
   * @param prefix sending prefix
   * @param segmentCommand command formatted to follow LSCS spec
   * @return full formed Setup
   */
  def toOneSegment(prefix: Prefix, segmentId: SegmentId, commandAsString: String, obsId: Option[ObsId] = None): Setup = {
    // Grab the command name from the first part of
    require(commandAsString.nonEmpty, "The command to the HCD must not be empty, fool!")
    println(s"SegmentId: $segmentId")

    // Grab the comnand Name
    val commandName = commandAsString.substring(0, commandAsString.indexOf(" "))
    // Make sure it is allowed
    require(AllCommands.contains(commandName), "You can only send supported Segment Commands. See \"SegmentCommands\"")

    Setup(prefix, CommandName(commandName), obsId).madd(lscsCommandKey.set(commandAsString), segmentIdKey.set(segmentId.toString))
  }

  def toAllSegments(prefix: Prefix, commandAsString: String, obsId: Option[ObsId] = None): Setup = {
    // Grab the command name from the first part of
    require(commandAsString.nonEmpty, "The command to the HCD must not be empty, fool!")

    // Grab the comnand Name
    val commandName = commandAsString.substring(0, commandAsString.indexOf(" "))
    // Make sure it is allowed
    require(AllCommands.contains(commandName), "You can only send supported Segment Commands. See \"SegmentCommands\"")

    Setup(prefix, CommandName(commandName), obsId).madd(lscsCommandKey.set(commandAsString), segmentIdKey.set(ALL_SEGMENTS))
  }

}
