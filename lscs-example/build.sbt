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
  .aggregate(`m1cs-comshcd`, `m1cs-comsdeploy`, `docs`).settings(
    ghpagesBranch := "gh-pages", // DO NOT DELETE
    commands += openSite.value,
    Settings.makeSiteMappings(docs)
)


// assembly module
lazy val `m1cs-comsassembly` = project
  .settings(
    libraryDependencies ++= Dependencies.Comsassembly
  )

// hcd module
lazy val `m1cs-comshcd` = project
  .settings(
    libraryDependencies ++= Dependencies.Comshcd
  )

// deploy module
lazy val `m1cs-comsdeploy` = project
  .dependsOn(
    `m1cs-comsassembly`,
    `m1cs-comshcd`
  )
  .enablePlugins(CswBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.ComsDeploy
  )





