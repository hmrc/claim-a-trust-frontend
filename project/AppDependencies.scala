import sbt._

object AppDependencies {
  import play.core.PlayVersion

  val bootstrapFrontendPlay28 = "7.20.0"

  private val compile: Seq[ModuleID] = Seq(
    play.sbt.PlayImport.ws,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"             % "1.3.0",
    "uk.gov.hmrc"       %% "play-frontend-hmrc"             % "7.16.0-play-28",
    "uk.gov.hmrc"       %% "play-conditional-form-mapping"  % "1.13.0-play-28",
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-28"     % bootstrapFrontendPlay28,
    "org.typelevel"     %% "cats-core"                      % "2.9.0"
  )

  private val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-28"   % bootstrapFrontendPlay28,
    "org.scalatest"               %% "scalatest"                % "3.2.16",
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-test-play-28"  % "1.3.0",
    "org.scalatestplus.play"      %% "scalatestplus-play"       % "5.1.0",
    "org.scalatestplus"           %% "scalatestplus-scalacheck" % "3.1.0.0-RC2",
    "org.pegdown"                 %  "pegdown"                  % "1.6.0",
    "org.jsoup"                   %  "jsoup"                    % "1.16.1",
    "com.typesafe.play"           %% "play-test"                % PlayVersion.current,
    "org.mockito"                 %% "mockito-scala-scalatest"  % "1.17.14",
    "org.scalacheck"              %% "scalacheck"               % "1.17.0",
    "com.github.tomakehurst"      % "wiremock-standalone"       % "2.27.2",
    "com.vladsch.flexmark"        %  "flexmark-all"             % "0.64.8"
  ).map(_ % Test)

  val akkaVersion = "2.6.7"
  val akkaHttpVersion = "10.1.12"

  val overrides: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-stream_2.12"    % akkaVersion,
    "com.typesafe.akka" %% "akka-protobuf_2.12"  % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j_2.12"     % akkaVersion,
    "com.typesafe.akka" %% "akka-actor_2.12"     % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core_2.12" % akkaHttpVersion,
    "commons-codec"     % "commons-codec"        % "1.12"
  )

  def apply(): Seq[ModuleID] = compile ++ test
}
