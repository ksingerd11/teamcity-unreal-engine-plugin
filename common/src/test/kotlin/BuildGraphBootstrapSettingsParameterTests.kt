import arrow.core.raise.either
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapApplyTo
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapApplyToParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapMode
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapModeParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapSettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapStepRefsParameter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class BuildGraphBootstrapSettingsParameterTests {
    @Test
    fun `returns disabled bootstrap settings by default`() {
        val settings = either { BuildGraphBootstrapSettingsParameter.parse(emptyMap()) }.getOrNull()

        settings shouldBe BuildGraphBootstrapSettings()
    }

    @Test
    fun `ignores selected steps when bootstrap mode is disabled`() {
        val settings =
            either {
                BuildGraphBootstrapSettingsParameter.parse(
                    mapOf(
                        BuildGraphBootstrapModeParameter.name to BuildGraphBootstrapMode.Disabled.name,
                        BuildGraphBootstrapStepRefsParameter.name to "P4 Login",
                    ),
                )
            }.getOrNull()

        settings shouldBe BuildGraphBootstrapSettings()
    }

    @Test
    fun `parses selected bootstrap settings`() {
        val settings =
            either {
                BuildGraphBootstrapSettingsParameter.parse(
                    mapOf(
                        BuildGraphBootstrapModeParameter.name to BuildGraphBootstrapMode.Selected.name,
                        BuildGraphBootstrapStepRefsParameter.name to "RUNNER_1\nP4 Login\nRUNNER_1",
                        BuildGraphBootstrapApplyToParameter.name to BuildGraphBootstrapApplyTo.NodesOnly.name,
                    ),
                )
            }.getOrNull()

        settings shouldNotBe null
        settings!!.mode shouldBe BuildGraphBootstrapMode.Selected
        settings.stepRefs shouldBe listOf("RUNNER_1", "P4 Login")
        settings.applyTo shouldBe BuildGraphBootstrapApplyTo.NodesOnly
    }

    @Test
    fun `requires step refs for selected bootstrap mode`() {
        val error =
            either {
                BuildGraphBootstrapSettingsParameter.parse(
                    mapOf(BuildGraphBootstrapModeParameter.name to BuildGraphBootstrapMode.Selected.name),
                )
            }.leftOrNull()

        error?.propertyName shouldBe BuildGraphBootstrapStepRefsParameter.name
    }
}
