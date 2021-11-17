import com.lightbend.paradox.sbt.ParadoxPlugin.autoImport.paradoxProperties
import org.tmt.sbt.docs.DocKeys._
import org.tmt.sbt.docs.Settings
import sbt.Keys.{scalacOptions, _}

lazy val cswVersion  = "4.0.0"
lazy val akkaVersion = "2.6.15" //all akka is Apache License 2.0
lazy val scalaTestVersion = "3.2.9" // Apache License 2.0

ThisBuild / organization := "com.github.tmtsoftware.m1cs"
ThisBuild / organizationName := "TMT"
ThisBuild / organizationHomepage := Some(url("http://www.tmt.org"))
ThisBuild / scalaVersion := "2.13.6"
//ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / version := {
  sys.props.get("prod.publish") match {
    case Some("true") => version.value
    case _            => "0.1.0-SNAPSHOT"
  }
}

// For Paradox

// ======== sbt-docs Settings =========
lazy val githubRepoUrl = "https://github.com/tmtsoftware/osw-examples"
ThisBuild / docsRepo := githubRepoUrl
ThisBuild / docsParentDir := "lscsExample"
ThisBuild / gitCurrentRepo := githubRepoUrl

// This makes tests run serially, not in parallel, which is the default
ThisBuild / Test / parallelExecution := false
// This forks all run and tests, should be done usually: https://www.scala-sbt.org/1.x/docs/Combined+Pages.html#Forking
ThisBuild / fork := true

/* ================= Paradox Docs ============== */

lazy val docs = (project in file("docs"))
  .enablePlugins(ParadoxMaterialSitePlugin)
  .settings(
    //paradoxRoots := List(
//      "index.html"
//    ),
    paradoxProperties ++= Map(
      "image.base_url" -> "./images"
    )
  )

/* =============================== */

lazy val `lscs-example` = project.in(file("."))
  .enablePlugins(GithubPublishPlugin)
  .settings(
    ghpagesBranch := "gh-pages", // Needed for docs
    Settings.makeSiteMappings(docs)
  ).aggregate(
    lscsCommands,
    lscsComps,
    lscsDeploy,
    docs
)

// All LSCS JVM components
lazy val lscsComps = project.in(file("lscsComps"))
  .dependsOn(lscsCommands)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.tmtsoftware.csw" %% "csw-framework"            % cswVersion,
      "com.github.tmtsoftware.csw" %% "csw-testkit"              % cswVersion % Test,
      "com.typesafe.akka"          %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest"              %% "scalatest"                % scalaTestVersion % Test
    )
  )

// Command Support
lazy val lscsCommands = project.in(file("lscsCommands"))
  .settings(
    libraryDependencies ++= Seq(
      "com.github.tmtsoftware.csw" %% "csw-framework" % cswVersion,
      "com.github.tmtsoftware.csw" %% "csw-testkit"   % cswVersion % Test,
      "org.scalatest"              %% "scalatest"     %  scalaTestVersion % Test
    )
  )

// LSCS deploy module
lazy val lscsDeploy = project.in(file("lscsDeploy"))
  .dependsOn(lscsComps, lscsCommands)
  .settings(
    Compile / packageBin / mainClass   := Some("m1cs.segments.deploy.SegmentsContainerApp"),
    libraryDependencies ++= Seq(
      "com.github.tmtsoftware.csw" %% "csw-framework" % cswVersion,
      "com.github.tmtsoftware.csw" %% "csw-testkit"   % cswVersion   % Test
    )
  )

ThisBuild / Compile / scalacOptions  ++= Seq(
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
  //-W Options
  "-Wdead-code",
  //-X Options
  "-Xlint:_,-missing-interpolator",
  "-Xsource:3",
  "-Xcheckinit",
  "-Xasync"
)
