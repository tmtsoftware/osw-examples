import sbt._

object CSW {
  private val Org     = "com.github.tmtsoftware.csw"
  private val Version = "4.0.0"
  //  private val Version = "0.1.0-SNAPSHOT"

  val `csw-logging-client`  = Org %% "csw-logging-client"  % Version
  val `csw-commons`         = Org %% "csw-commons"         % Version
  val `csw-location-client` = Org %% "csw-location-client" % Version
  val `csw-location-api`    = Org %% "csw-location-api"    % Version
  val `csw-config-client`   = Org %% "csw-config-client"   % Version
  val `csw-config-cli`      = Org %% "csw-config-cli"      % Version
  val `csw-aas-installed`   = Org %% "csw-aas-installed"   % Version
  val `csw-framework`       = Org %% "csw-framework"       % Version
  val `csw-event-client`    = Org %% "csw-event-client"    % Version
  val `csw-database`        = Org %% "csw-database"        % Version
  val `csw-prefix`          = Org %% "csw-prefix"          % Version
  val `csw-testkit`         = Org %% "csw-testkit"         % Version

}

object Libs {
  val ScalaVersion = "2.13.6"

  val `scalatest`       = "org.scalatest"          %% "scalatest"       % "3.2.9"  //Apache License 2.0
  val `junit`           = "junit"                  %  "junit"           % "4.12"   //Eclipse Public License 1.0
  val `junit-interface` = "com.novocode"           %  "junit-interface" % "0.11"   //BSD 2-clause "Simplified" License
  val `mockito-scala`   = "org.mockito"            %% "mockito-scala"   % "1.16.37"
  val `slf4j-api`       = "org.slf4j"              % "slf4j-api"        % "1.7.25"
}


object Akka {
  val Version                    = "2.6.15" //all akka is Apache License 2.0
  val `akka-actor`               = "com.typesafe.akka" %% "akka-actor" % Version
  val `akka-stream`              = "com.typesafe.akka" %% "akka-stream" % Version
  val `akka-stream-typed`        = "com.typesafe.akka" %% "akka-stream-typed" % Version
  val `akka-stream-testkit`      = "com.typesafe.akka" %% "akka-stream-testkit" % Version
  val `akka-testkit`             = "com.typesafe.akka" %% "akka-testkit" % Version
  val `akka-actor-testkit-typed` = "com.typesafe.akka" %% "akka-actor-testkit-typed" % Version
}

object AkkaHttp {
  val Version                = "10.2.4"
  val `akka-http-spray-json` = "com.typesafe.akka" %% "akka-http-spray-json" % Version
}



