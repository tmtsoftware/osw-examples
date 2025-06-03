import com.lightbend.paradox.sbt.ParadoxPlugin.autoImport._
import com.typesafe.sbt.site.paradox.ParadoxSitePlugin
import com.typesafe.sbt.site.SitePlugin.autoImport.siteDirectory
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import org.tmt.sbt.docs.DocKeys.{docsParentDir, docsRepo, gitCurrentRepo}
import sbt.Keys.*
import sbt.*

object Common {
  private val enableFatalWarnings: Boolean = sys.props.get("enableFatalWarnings").contains("true")
  private val enableCoverage: Boolean      = sys.props.get("enableCoverage").contains("true")
  val storyReport: Boolean                 = sys.props.get("generateStoryReport").contains("true")

  lazy val CommonSettings: Seq[Setting[_]] = Seq(
    scalaVersion     := "3.6.4",
    version          := "0.1.0-SNAPSHOT",
    docsRepo         := "https://github.com/tmtsoftware/tmtsoftware.github.io.git",
    docsParentDir    := "osw-examples",
    gitCurrentRepo   := "https://github.com/tmtsoftware/osw-examples",
    organization     := "com.github.tmtsoftware.csw",
    organizationName := "Thirty Meter Telescope International Observatory",
    homepage         := Some(url("https://github.com/tmtsoftware/osw-examples")),
    resolvers += "Apache Pekko Staging".at("https://repository.apache.org/content/groups/staging"),
    resolvers += "jitpack" at "https://jitpack.io",
    scmInfo := Some(
      ScmInfo(url("https://github.com/tmtsoftware/osw-examples"), "git@github.com:tmtsoftware/osw-examples.git")
    ),
    licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))),
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation"
    ),
    javacOptions ++= Seq(
      "-Xlint:unchecked"
    ),
    javaOptions += "-Xmx2G",
    Compile / doc / javacOptions ++= Seq("-Xdoclint:none"),
    doc / javacOptions ++= Seq("--ignore-source-errors"),
    Test / packageBin / publishArtifact := true,
    // jitpack provides the env variable VERSION=<version being built> # A tag or commit. We have aliased VERSION to JITPACK_VERSION
    // we make use of it so that the version in class metadata (e.g. classOf[LocationService].getPackage.getSpecificationVersion)
    // and the maven repo match
    version := sys.env.getOrElse("JITPACK_VERSION", "0.1.0-SNAPSHOT"),
    fork    := true,
    parallelExecution := false,
    Test / javaOptions ++= Seq("-Dpekko.actor.serialize-messages=on", s"-DRTM_PATH=${file("./target/RTM").getAbsolutePath}"),
    autoCompilerPlugins := true,
    Global / cancelable := true, // allow ongoing test(or any task) to cancel with ctrl + c and still remain inside sbt
    scalafmtOnCompile   := false,
    commands += Command.command("openSite") { state =>
      val uri = s"file://${Project.extract(state).get(siteDirectory)}/${docsParentDir.value}/${version.value}/index.html"
      state.log.info(s"Opening browser at $uri ...")
      java.awt.Desktop.getDesktop.browse(new java.net.URI(uri))
      state
    },
    Global / excludeLintKeys := Set(
      SettingKey[Boolean]("ide-skip-project"),
      aggregate // verify if this needs to be here or our configuration is wrong
    ),
  )
}
