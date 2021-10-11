
lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
//  `m1cs-comsassembly`,
  `m1cs-comshcd`,
  `m1cs-comsdeploy`
)

lazy val `lscs-example` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

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
