package m1cs.comsdeploy

import csw.framework.deploy.containercmd.ContainerCmd
import csw.prefix.models.Subsystem

object ComsContainerCmdApp extends App {

  ContainerCmd.start("coms_container_cmd_app", Subsystem.withNameInsensitive("m1cs"), args)

}
