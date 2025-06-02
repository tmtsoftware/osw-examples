import Dependencies.*
import Common._
import org.tmt.sbt.docs.{Settings => DocSettings}

inThisBuild(
  CommonSettings
)

/** PROJECTS */
lazy val `osw-examples` = project
  .in(file("."))
  .enablePlugins(NoPublish, UnidocSitePlugin, GithubPublishPlugin, GitBranchPrompt, GithubRelease)
  .settings(DocSettings.makeSiteMappings(docs))
  .aggregate(
    `command-example`,
    lscsComps,
    lscsCommands,
    lscsDeploy,
    docs
  )

// Command Example
lazy val `command-example` = project
  .settings(
    libraryDependencies ++= Seq(
      `csw-framework`,
      `csw-testkit` % Test,
      `scalatest` % Test
    )
  )

// LSCS example

// All LSCS JVM components
lazy val lscsComps = project
  .in(file("lscs-example/lscsComps"))
  .dependsOn(lscsCommands)
  .settings(
    libraryDependencies ++= Seq(
      `csw-framework`,
      `csw-testkit` % Test,
      `pekko-testkit` % Test,
      `scalatest` % Test
    )
  )

// Command Support
lazy val lscsCommands = project
  .in(file("lscs-example/lscsCommands"))
  .settings(
    libraryDependencies ++= Seq(
      `csw-framework`,
      `csw-testkit` % Test,
      `scalatest` % Test
    )
  )

// LSCS deploy module

lazy val lscsDeploy = project
  .in(file("lscs-example/lscsDeploy"))
  .dependsOn(lscsComps, lscsCommands)
  .settings(
    Compile / packageBin / mainClass := Some("m1cs.segments.deploy.SegmentsContainerApp"),
    libraryDependencies ++= Seq(
      `csw-framework`,
      `csw-testkit` % Test
    )
  )


// Docs for all projects
lazy val docs = project
  .enablePlugins(NoPublish, ParadoxMaterialSitePlugin)
  .dependsOn(lscsCommands, lscsComps)
  .settings(
    paradoxRoots := List(
      "index.html",
    ),
    paradoxProperties ++= Map(
      "image.base_url" -> ".../images",
      "lscs.base" -> "../../../../lscs-example/",
    ),
  )

