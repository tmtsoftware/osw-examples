package m1cs.segments.deploy

import csw.framework.deploy.containercmd.ContainerCmd
import csw.prefix.models.Subsystem

object SegmentsContainerCmdApp {
  def main(args: Array[String]): Unit = {
    ContainerCmd.start("coms_container_cmd_app", Subsystem.withNameInsensitive("m1cs"), args)
  }
}
