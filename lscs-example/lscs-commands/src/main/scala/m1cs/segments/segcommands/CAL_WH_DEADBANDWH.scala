package m1cs.segments.segcommands

import csw.params.commands.{CommandName, Setup}
import csw.prefix.models.Prefix

/**
 * The docs for this command seem unusual and this code may need repair.
 */
object CAL_WH_DEADBANDWH {
  import Common.*

  val COMMAND_NAME: CommandName = CommandName("CAL_WH_DEADBANDWH")

  private[CAL_WH_DEADBANDWH] case class toCalibrateWarpingHarnessDeadband(prefix: Prefix,
                                               warpingHarness: String) extends BaseCommand[toCalibrateWarpingHarnessDeadband](prefix, COMMAND_NAME) {
    setup = setup.add(warpingHarnessIdKey.set(warpingHarness))

    // Make a copy -- do any checks here
    override def asSetup: Setup = {
      // Should require a segment set
      Setup(setup.source, setup.commandName, setup.maybeObsId, setup.paramSet)
    }
  }

  object toCalibrateWarpingHarnessDeadband {
    def apply(prefix: Prefix, warpingHarnessId: Int): toCalibrateWarpingHarnessDeadband = {
      val s = checkWarpingHarnessId(warpingHarnessId)
      toCalibrateWarpingHarnessDeadband(prefix, s)
    }

    def apply(prefix: Prefix): toCalibrateWarpingHarnessDeadband = {
      toCalibrateWarpingHarnessDeadband(prefix, ALL_HARNESS)
    }
  }

  /**
   * Returns a formatted CAL_WH_DEADBANDWH command from a Setup
   *
   * @param setup Setup created with toCalibrateWarpingHarnessDeadband
   * @return String command ready to send
   */
  def toCommand(setup: Setup): String = {
    require(setup.commandName == COMMAND_NAME, s"The provided Setup is not a: $COMMAND_NAME")

    val sb = new StringBuilder(s"${setup.commandName.name} CAL_WH_DEADBANDWH_ID=${setup(warpingHarnessIdKey).head}")
    sb.result()
  }
}
