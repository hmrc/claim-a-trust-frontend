import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  private val settings: Seq[Setting[?]] = Seq(
    coverageExcludedPackages := "<empty>;Reverse.*;..*Routes.*;.*testOnlyDoNotUseInAppConf.*;",
    coverageMinimumStmtTotal := 91,
    coverageFailOnMinimum := true
  )

  def apply(): Seq[Setting[?]] = settings

}
