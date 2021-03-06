package m1cs.segments.shared

import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.ObsId
import csw.prefix.models.Prefix
import m1cs.segments.segcommands.Common.{CommandMap, segmentIdKey, segmentRangeKey}

//#hcd-direct
/**
 * This object contains the parameter keys for the command sent to the HCD to process a single
 * segment command. This is used by the segment Assembly and test code to produce an HCD Setup from
 * an Assembly Setup.
 */
case object HcdDirectCommand {

  val lscsDirectCommand: CommandName = CommandName("lscsDirectCommand")
  // This key is used to store the command to be executed
  val lscsCommandKey: Key[String]     = KeyType.StringKey.make(name = "lscsCommand")
  val lscsCommandNameKey: Key[String] = KeyType.StringKey.make(name = "lscsCommandName")

  /**
   * This helper function returns a direct command Setup for the
   * @param assemblyPrefix prefix of the Assembly as source
   * @param assemblySetup the Setup received by the Assembly -contains segmentIdKey and command name
   * @param obsId optional ObsId, defaults to None
   * @return Setup ready for sending to HCD
   */
  def toHcdDirectCommand(assemblyPrefix: Prefix, assemblySetup: Setup, obsId: Option[ObsId] = None): Setup = {
    val segmentIdExists    = assemblySetup.exists(segmentIdKey)
    val segmentRangeExists = assemblySetup.exists(segmentRangeKey)
    // Can't go on without one of these set
    require(segmentIdExists || segmentRangeExists, s"Bad segment info in the Assembly Setup: ${assemblySetup.commandName}")

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
//#hcd-direct

/**
 * This command when sent to the HCD causes shutdown of all the connections to Segments
 */
case object HcdShutdown {
  val shutdownCommand: CommandName = CommandName("ShutdownAll")

  def toHcdShutdown(assemblyPrefix: Prefix, obsId: Option[ObsId] = None): Setup = Setup(assemblyPrefix, shutdownCommand, obsId)
}
