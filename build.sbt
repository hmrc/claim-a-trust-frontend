import uk.gov.hmrc.DefaultBuildSettings.targetJvm

ThisBuild / scalaVersion := "3.9.0"
ThisBuild / majorVersion := 1
ThisBuild / targetJvm := "jvm-21"

lazy val microservice = Project("claim-a-trust-frontend", file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) // Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    CodeCoverageSettings(),
    scalacOptions ++= Seq(
      "-Wconf:src=routes/.*:s",
      "-Wconf:msg=unused import&src=html/.*:s",
      "-feature"
    ),
    routesImport += "models._",
    TwirlKeys.templateImports ++= Seq(
      "play.twirl.api.HtmlFormat",
      "play.twirl.api.HtmlFormat._",
      "uk.gov.hmrc.govukfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.helpers._",
      "views.ViewUtils._",
      "models.Mode"
    ),
    PlayKeys.playDefaultPort := 9785,
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true,
    Test / fork := true
  )

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt")
