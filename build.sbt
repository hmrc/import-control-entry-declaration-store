import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
val appName = "import-control-entry-declaration-store"

lazy val coverageSettings: Seq[Setting[_]] = {
  import scoverage.ScoverageKeys

  val excludedPackages = Seq(
    "<empty>",
    ".*Reverse.*",
    ".*standardError*.*",
    ".*govuk_wrapper*.*",
    ".*main_template*.*",
    "uk.gov.hmrc.BuildInfo",
    "api.*",
    "app.*",
    "prod.*",
    "config.*",
    "testOnly.*",
    "testOnlyDoNotUseInAppConf.*",
    "views.html.*",
    ".*feedback*.*"
  )

  Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 80,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin, ScalafmtCorePlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    scalaVersion := "2.13.8",
    majorVersion := 0,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    resolvers += Resolver.jcenterRepo,
    PlayKeys.playDefaultPort := 9818
  )
  .settings(coverageSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(IntegrationTest / resourceDirectory := baseDirectory.value / "test" / "resources")
  .settings(
    scalacOptions += "-Xlint:_,-missing-interpolator",
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions += "-Wconf:cat=unused-imports&src=html/.*:s"
  )
