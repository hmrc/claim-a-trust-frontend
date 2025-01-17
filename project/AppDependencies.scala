import sbt.*

object AppDependencies {
  val bootstrapVersion = "9.5.0"
  val mongoVersion = "2.3.0"

  private val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"                    % mongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-30"            % bootstrapVersion,
    "uk.gov.hmrc"       %% "play-frontend-hmrc-play-30"            % "11.7.0",
    "uk.gov.hmrc"       %% "play-conditional-form-mapping-play-30" % "3.2.0",
    "org.typelevel"     %% "cats-core"                             % "2.12.0"
  )

  private val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-30"   % bootstrapVersion,
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-test-play-30"  % mongoVersion,
    "org.scalatestplus"           %% "scalacheck-1-17"          % "3.2.18.0",
    "org.jsoup"                   %  "jsoup"                    % "1.18.3",
    "org.scalacheck"              %% "scalacheck"               % "1.18.1"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
