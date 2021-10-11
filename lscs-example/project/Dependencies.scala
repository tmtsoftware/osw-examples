import sbt._

object Dependencies {

  val Comsassembly = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test,
    Libs.`junit-4-13` % Test,
    Akka.`akka-actor-testkit-typed` % Test
  )

  val Comshcd = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test,
    Libs.`junit-4-13` % Test,
    Akka.`akka-actor-testkit-typed` % Test
  )

  val ComsDeploy = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test
  )

  val SegSim = Seq(
    CSW.`csw-framework`,
    Akka.`akka-stream-typed`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test,
    Akka.`akka-stream-testkit` % Test,
    Akka.`akka-actor-testkit-typed` % Test
  )

  val SegSimClient = Seq(
    CSW.`csw-framework`,
    Akka.`akka-stream-typed`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test,
    Akka.`akka-stream-testkit` % Test,
    Akka.`akka-actor-testkit-typed` % Test
  )
}
