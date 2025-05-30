import sbt._

object Dependencies {

  private val Org = "com.github.tmtsoftware.csw"
  lazy val cswVersion = "0.1.0-SNAPSHOT" //4.0.0"
  lazy val pekkoVersion = "1.1.3" //all akka is Apache License 2.0
  lazy val scalaTestVersion = "3.2.19" // Apache License 2.0

  val `csw-framework` = Org %% "csw-framework" % cswVersion
  val `csw-testkit` = Org %% "csw-testkit" % cswVersion


  val `scalatest` = "org.scalatest" %% "scalatest" % scalaTestVersion //Apache License 2.0

  val `pekko-testkit` = "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion

  /*

  val CommandExample = Seq(

//    Libs.`junit` % Test,
//    Libs.`junit-interface` % Test
  )
}

 */

}
