import Dependencies._

/* =============================== */

lazy val `lscs-example` = project
  .in(file("."))
  .aggregate(
    lscsCommands,
    lscsComps,
    lscsDeploy
  )

// All LSCS JVM components
lazy val lscsComps = project
  .in(file("lscsComps"))
  .dependsOn(lscsCommands)
  .settings(
    libraryDependencies ++= Seq(
      `csw-framework`,
      `csw-testkit` % Test,
      `akka-testkit` % Test,
      `scalatest` % Test
    )
  )

// Command Support
lazy val lscsCommands = project
  .in(file("lscsCommands"))
  .settings(
    libraryDependencies ++= Seq(
      `csw-framework`,
      `csw-testkit` % Test,
      `scalatest` % Test
    )
  )

// LSCS deploy module
lazy val lscsDeploy = project
  .in(file("lscsDeploy"))
  .dependsOn(lscsComps, lscsCommands)
  .settings(
    Compile / packageBin / mainClass := Some("m1cs.segments.deploy.SegmentsContainerApp"),
    libraryDependencies ++= Seq(
      `csw-framework`,
      `csw-testkit` % Test
    )
  )
