import arrow.core.raise.either
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphDiagnosticsSettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphNodeTimeoutRulesParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphPublishSetupDiagnosticsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphSetupTimeoutParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphTimeoutSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphTimeoutSettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphTraceGeneratedBuildsParameter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class BuildGraphTimeoutSettingsParameterTests {
    @Test
    fun `returns disabled timeout settings by default`() {
        val settings = either { BuildGraphTimeoutSettingsParameter.parse(emptyMap()) }.getOrNull()

        settings shouldBe BuildGraphTimeoutSettings()
    }

    @Test
    fun `parses setup timeout and node timeout rules`() {
        val settings =
            either {
                BuildGraphTimeoutSettingsParameter.parse(
                    mapOf(
                        BuildGraphSetupTimeoutParameter.name to "2h",
                        BuildGraphNodeTimeoutRulesParameter.name to "Compile*=45m\nCook*=PT1H",
                    ),
                )
            }.getOrNull()

        settings shouldNotBe null
        settings!!.setupTimeoutSeconds shouldBe 7200
        settings.timeoutForNode("Compile Win64") shouldBe 2700
        settings.timeoutForNode("Cook Client") shouldBe 3600
        settings.timeoutForNode("Archive") shouldBe 0
    }

    @Test
    fun `returns validation error for invalid timeout rule`() {
        val error =
            either {
                BuildGraphTimeoutSettingsParameter.parse(
                    mapOf(BuildGraphNodeTimeoutRulesParameter.name to "Cook*=forever"),
                )
            }.leftOrNull()

        error?.propertyName shouldBe BuildGraphNodeTimeoutRulesParameter.name
    }

    @Test
    fun `parses diagnostics settings`() {
        val settings =
            BuildGraphDiagnosticsSettingsParameter.parse(
                mapOf(
                    BuildGraphPublishSetupDiagnosticsParameter.name to true.toString(),
                    BuildGraphTraceGeneratedBuildsParameter.name to true.toString(),
                ),
            )

        settings.publishSetupDiagnostics shouldBe true
        settings.traceGeneratedBuilds shouldBe true
    }
}
