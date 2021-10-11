lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  `command-example`,

)

lazy val `osw-examples` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)


lazy val `command-example` = project
  .settings(
    libraryDependencies ++= Dependencies.CommandExample,
  )
