import org.tmt.sbt.docs.DocKeys._
import org.tmt.sbt.docs.Settings

/* ================= Paradox Docs ============== */

lazy val githubRepoUrl = "https://github.com/tmtsoftware/osw-examples/lscs-example"

ThisBuild / docsRepo := githubRepoUrl
ThisBuild / docsParentDir := ""
ThisBuild / gitCurrentRepo := githubRepoUrl

version := {
  sys.props.get("prod.publish") match {
    case Some("true") => version.value
    case _            => "0.1.0-SNAPSHOT"
  }
}

lazy val openSite =
  Def.setting {
    Command.command("openSite") { state =>
      val uri = s"file://${Project.extract(state).get(siteDirectory)}/${docsParentDir.value}/${version.value}/index.html"
      state.log.info(s"Opening browser at $uri ...")
      java.awt.Desktop.getDesktop.browse(new java.net.URI(uri))
      state
    }
  }

lazy val `docs` = (project in file("docs")).
  enablePlugins(ParadoxMaterialSitePlugin).settings(
  paradoxRoots := List(
    "index.html"
  )
)

/* =============================== */

lazy val `lscs-example` = project
  .in(file("."))
  .aggregate(`lscs-comps`, `lscs-compsdeploy`, `docs`).settings(
    ghpagesBranch := "gh-pages", // DO NOT DELETE
    commands += openSite.value,
    Settings.makeSiteMappings(docs)
)


// All LSCS JVM components
lazy val `lscs-comps` = project
  .dependsOn(
    `lscs-support`
  )
  .settings(
    libraryDependencies ++= Dependencies.LscsCompsDeps
  )

// Command Support
lazy val `lscs-support` = project
  .settings(
    libraryDependencies ++= Dependencies.LscsSupportDeps
  )

// LSCS deploy module
lazy val `lscs-compsdeploy` = project
  .dependsOn(
    `lscs-comps`
  )
  .enablePlugins(CswBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.LscsComsDeployDeps
  )





