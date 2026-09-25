import play.sbt.routes.RoutesKeys
import sbt.Def
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

lazy val root = Project("claim-a-trust-frontend", file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) // Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(inConfig(Test)(testSettings))
  .settings(majorVersion := 0)
  .settings(CodeCoverageSettings())
  .settings(
    scalaVersion := "3.3.7",
    RoutesKeys.routesImport += "models._",
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
    scalacOptions ++= Seq(
      "-feature",
      "-Wconf:src=routes/.*:s",
      "-Wconf:msg=unused import&src=html/.*:s",
      "-Wconf:msg=Flag.*repeatedly:s"
    ),
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true
  )

lazy val testSettings: Seq[Def.Setting[?]] = Seq(
  fork := true
)

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt")
