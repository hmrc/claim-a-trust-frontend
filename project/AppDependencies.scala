import sbt.*

object AppDependencies {

  val bootstrapFrontendPlay28 = "7.22.0"

  private val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"             % "1.3.0",
    "uk.gov.hmrc"       %% "play-frontend-hmrc"             % "7.23.0-play-28",
    "uk.gov.hmrc"       %% "play-conditional-form-mapping"  % "1.13.0-play-28",
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-28"     % bootstrapFrontendPlay28,
    "org.typelevel"     %% "cats-core"                      % "2.10.0"
  )

  private val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-28"   % bootstrapFrontendPlay28,
    "org.scalatest"               %% "scalatest"                % "3.2.17",
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-test-play-28"  % "1.3.0",
    "org.scalatestplus"           %% "mockito-4-11"             % "3.2.17.0",
    "org.scalatestplus"           %% "scalacheck-1-17"          % "3.2.17.0",
    "org.jsoup"                   %  "jsoup"                    % "1.16.1",
    "org.scalacheck"              %% "scalacheck"               % "1.17.0",
    "org.mockito"                 %% "mockito-scala-scalatest"  % "1.17.27",
    "org.wiremock"                %  "wiremock-standalone"      % "3.2.0",
    "com.vladsch.flexmark"        %  "flexmark-all"             % "0.64.8"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
