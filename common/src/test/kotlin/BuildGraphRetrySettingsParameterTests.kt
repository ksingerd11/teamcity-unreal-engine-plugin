import arrow.core.raise.either
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphNodeRetryRulesParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetriesEnabledParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetryCondition
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetryConditionParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetryDelaySecondsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetryFailurePatternsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetrySettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetrySettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphSetupMaxAttemptsParameter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class BuildGraphRetrySettingsParameterTests {
    @Test
    fun `returns disabled settings by default`() {
        val settings = either { BuildGraphRetrySettingsParameter.parse(emptyMap()) }.getOrNull()

        settings shouldBe BuildGraphRetrySettings()
    }

    @Test
    fun `ignores retry values when retries are disabled`() {
        val settings =
            either {
                BuildGraphRetrySettingsParameter.parse(
                    mapOf(
                        BuildGraphRetriesEnabledParameter.name to false.toString(),
                        BuildGraphSetupMaxAttemptsParameter.name to "0",
                        BuildGraphNodeRetryRulesParameter.name to "Compile*=0",
                    ),
                )
            }.getOrNull()

        settings shouldBe BuildGraphRetrySettings()
    }

    @Test
    fun `parses setup and node retry settings`() {
        val settings =
            either {
                BuildGraphRetrySettingsParameter.parse(
                    mapOf(
                        BuildGraphRetriesEnabledParameter.name to true.toString(),
                        BuildGraphSetupMaxAttemptsParameter.name to "2",
                        BuildGraphRetryDelaySecondsParameter.name to "5",
                        BuildGraphNodeRetryRulesParameter.name to "Compile*=3\nCook*=4",
                    ),
                )
            }.getOrNull()

        settings shouldNotBe null
        settings!!.setupMaxAttempts shouldBe 2
        settings.retryDelaySeconds shouldBe 5
        settings.maxAttemptsForNode("Compile Win64") shouldBe 3
        settings.maxAttemptsForNode("cook client") shouldBe 4
        settings.maxAttemptsForNode("Archive") shouldBe 1
    }

    @Test
    fun `parses log matching retry condition`() {
        val settings =
            either {
                BuildGraphRetrySettingsParameter.parse(
                    mapOf(
                        BuildGraphRetriesEnabledParameter.name to true.toString(),
                        BuildGraphRetryConditionParameter.name to BuildGraphRetryCondition.MatchingLog.name,
                        BuildGraphRetryFailurePatternsParameter.name to "AutomationTool exiting with ExitCode=6\nlost connection",
                    ),
                )
            }.getOrNull()

        settings shouldNotBe null
        settings!!.condition shouldBe BuildGraphRetryCondition.MatchingLog
        settings.failurePatterns shouldBe listOf("AutomationTool exiting with ExitCode=6", "lost connection")
    }

    @Test
    fun `requires failure patterns when retry condition matches logs`() {
        val error =
            either {
                BuildGraphRetrySettingsParameter.parse(
                    mapOf(
                        BuildGraphRetriesEnabledParameter.name to true.toString(),
                        BuildGraphRetryConditionParameter.name to BuildGraphRetryCondition.MatchingLog.name,
                    ),
                )
            }.leftOrNull()

        error?.propertyName shouldBe BuildGraphRetryFailurePatternsParameter.name
    }

    @Test
    fun `returns validation error for invalid enabled retry rules`() {
        val error =
            either {
                BuildGraphRetrySettingsParameter.parse(
                    mapOf(
                        BuildGraphRetriesEnabledParameter.name to true.toString(),
                        BuildGraphNodeRetryRulesParameter.name to "Compile*",
                    ),
                )
            }.leftOrNull()

        error?.propertyName shouldBe BuildGraphNodeRetryRulesParameter.name
    }
}
