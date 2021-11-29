import sbt._

object Dependencies {

  private val Org           = "com.github.tmtsoftware.csw"
  lazy val cswVersion       = "4.0.0"
  lazy val akkaVersion      = "2.6.15" //all akka is Apache License 2.0
  lazy val scalaTestVersion = "3.2.9"  // Apache License 2.0

  val `csw-framework`       = Org %% "csw-framework" % cswVersion
  val `csw-testkit`         = Org %% "csw-testkit"   % cswVersion


  val `scalatest`       = "org.scalatest"          %% "scalatest"       % "3.2.9"  //Apache License 2.0

  val `akka-testkit`    = "com.typesafe.akka"      %% "akka-actor-testkit-typed" % akkaVersion



  val CommandExample = Seq(

//    Libs.`junit` % Test,
//    Libs.`junit-interface` % Test
  )
}
