import sbt._

object Dependencies {

  val LscsCompsDeps = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test,
    Libs.`junit-4-13` % Test,
    Akka.`akka-actor-testkit-typed` % Test
  )

  val LscsCommandsDeps = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test
  )

  val LscsComsDeployDeps = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test
  )

}
