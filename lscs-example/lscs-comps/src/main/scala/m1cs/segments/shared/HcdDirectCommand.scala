package m1cs.segments.shared

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.ObsId
import csw.prefix.models.Prefix
import SegmentCommands.{CommandMap, segmentIdKey, segmentRangeKey}

case object HcdDirectCommand {

  val lscsDirectCommand = CommandName("lscsDirectCommand")
  // This key is used to store the command to be executed
  val lscsCommandKey: Key[String]     = KeyType.StringKey.make(name = "lscsCommand")
  val lscsCommandNameKey: Key[String] = KeyType.StringKey.make(name = "lscsCommandName")

  def toHcdDirectCommand(assemblyPrefix: Prefix, assemblySetup: Setup, obsId: Option[ObsId] = None): Setup = {
    val segmentIdExists    = assemblySetup.exists(segmentIdKey)
    val segmentRangeExists = assemblySetup.exists(segmentRangeKey)
    // Can't go on without one of these set
    require(segmentIdExists || segmentRangeExists, "Bad segment info in the Assembly Setup: ${setup.commandName}")

    // Convert setup to a String command - note that we know this will work because we validated
    val commandAsString = CommandMap(assemblySetup.commandName)(assemblySetup)

    // Grab the command name from the first part of
    require(commandAsString.nonEmpty, "The command to the HCD must not be empty, fool!")

    Setup(assemblyPrefix, lscsDirectCommand, obsId).madd(
      lscsCommandKey.set(commandAsString),
      lscsCommandNameKey.set(assemblySetup.commandName.name),
      assemblySetup(segmentIdKey)
    )
  }
}

case object HcdShutdown {
  val shutdownCommand: CommandName = CommandName("ShutdownAll")

  def toHcdShutdown(assemblyPrefix: Prefix, obsId: Option[ObsId] = None): Setup = Setup(assemblyPrefix, shutdownCommand, obsId)
}
