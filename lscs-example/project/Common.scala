import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import org.tmt.sbt.docs.DocKeys.{docsParentDir, docsRepo, gitCurrentRepo}
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbt.{Compile, url, _}

object Common extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  //override def requires: Plugins = JvmPluginSegmentsAssemblyTests

  override lazy val projectSettings: Seq[Setting[_]] = Seq(

    Compile / doc / javacOptions ++= Seq("-Xdoclint:none"),
    Test / testOptions ++= Seq(
      // show full stack traces and test case durations
      Tests.Argument("-oDF"),
      // -v Log "test run started" / "test started" / "test run finished" events on log level "info" instead of "debug".
      // -a Show stack traces and exception class name for AssertionErrors.
      //Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
    ),
    resolvers += "jitpack" at "https://jitpack.io",


    autoCompilerPlugins := true,
    if (formatOnCompile) scalafmtOnCompile := true else scalafmtOnCompile := false,
    Global / excludeLintKeys := Set(
      docsParentDir, docsRepo, gitCurrentRepo
    )


  )

  private def formatOnCompile = sys.props.get("format.on.compile") match {
    case Some("false") ⇒ false
    case _             ⇒ true
  }
}