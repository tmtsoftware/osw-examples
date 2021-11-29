import Dependencies._

ThisBuild / organization := "org.tmt"
ThisBuild / organizationName := "TMT International Observatory"
ThisBuild / organizationHomepage := Some(url("http://www.tmt.org"))
ThisBuild / scalaVersion := "2.13.7"
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / resolvers += "jitpack" at "https://jitpack.io"

// This makes tests run serially, not in parallel, which is the default
ThisBuild / Test / parallelExecution := false
// This forks all run and tests, should be done usually: https://www.scala-sbt.org/1.x/docs/Combined+Pages.html#Forking
ThisBuild / fork := true

/** PROJECTS */

lazy val `osw-examples` = project
  .in(file("."))
  .aggregate(
    `command-example`,
    `lscs-example`,
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
lazy val `lscs-example` = project.in(file("lscs-example"))

// Docs for all projects
lazy val docs = project
  .in(file("docs"))
  .enablePlugins(
    ParadoxPlugin,
    ParadoxSitePlugin,
    GhpagesPlugin
  )
  .settings(
    name := "paradox docs",
    version := version.value.takeWhile(_ != '-'), // strip off the -SNAPSHOT for docs
    paradoxTheme := Some(builtinParadoxTheme("generic")),
    ghpagesNoJekyll := true,
    publish / skip     := true,
    git.remoteRepo  := "git@github.com:tmtsoftware/osw-examples.git",
    paradoxProperties ++= Map(
      "org" -> organization.value,
      "version" -> version.value,
      "image.base_url" -> "./images",
      "lscs.base" -> "../../../../lscs-example/",
    )
  ).dependsOn(`lscs-example`)


// Shared compile scalac options
ThisBuild / Compile / scalacOptions ++= Seq(
  "-deprecation",                     // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",               // Specify character encoding used by source files.
  "-explaintypes",                    // Explain type errors in more detail.
  "-feature",                         // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",           // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros",    // Allow macro definition (besides implementation and application)
  "-language:higherKinds",            // Allow higher-kinded types
  "-language:implicitConversions",    // Allow definition of implicit functions called views
  "-Xcheckinit",                      // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings",                 // Fail the compilation if there are any warnings.
  "-Xsource:3",                       // Treat compiler input as Scala source for the specified version
  "-Xlint:adapted-args",              // Warn if an argument list is modified to match the receiver.
  "-Xlint:delayedinit-select",        // Selecting member of DelayedInit.
  "-Xlint:doc-detached",              // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",              // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                 // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",      // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-unit",              // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",           // Option.apply used implicit view.
  "-Xlint:package-object-classes",    // Class or object defined in package object.
  "-Xlint:private-shadow",            // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",               // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",     // A local type parameter shadows a type already in scope.
  "-Wdead-code",                      // Warn when dead code is identified.
  "-Wextra-implicit",                 // Warn when more than one implicit parameter section is defined.
  "-Wnumeric-widen",                  // Warn when numerics are widened.
  //"-Wunused",                         // Enable -Wunused:imports,privates,locals,implicits.
  "-Wvalue-discard"                   // Warn when non-Unit expression results are unused.
)
