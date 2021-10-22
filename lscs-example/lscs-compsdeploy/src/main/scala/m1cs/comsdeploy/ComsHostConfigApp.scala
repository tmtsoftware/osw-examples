package m1cs.comsdeploy

import csw.framework.deploy.hostconfig.HostConfig
import csw.prefix.models.Subsystem

object ComsHostConfigApp extends App {

  HostConfig.start("coms_host_config_app", Subsystem.withNameInsensitive("m1cs"), args)

}
