package buildgraph

import com.jetbrains.teamcity.plugins.unrealengine.common.UnrealCommandType
import com.jetbrains.teamcity.plugins.unrealengine.common.UnrealEngineRunner
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapApplyTo
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapApplyToParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapMode
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapModeParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapStepRefsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphModeParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.parameters.UnrealCommandTypeParameter
import com.jetbrains.teamcity.plugins.unrealengine.server.buildgraph.BuildGraphBootstrapStepSelector
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import jetbrains.buildServer.serverSide.BuildPromotion
import jetbrains.buildServer.serverSide.ReadOnlyBuildSettings
import jetbrains.buildServer.serverSide.RunType
import jetbrains.buildServer.serverSide.SBuildRunnerDescriptor
import kotlin.test.Test

class BuildGraphBootstrapStepSelectorTests {
    @Test
    fun `selects single BuildGraph runner when bootstrap support is disabled`() {
        val buildGraphRunner = buildGraphRunner()

        val selection = BuildGraphBootstrapStepSelector.select(build(buildGraphRunner))

        selection?.buildGraphRunner shouldBe buildGraphRunner
        selection?.bootstrapRunners shouldBe emptyList()
    }

    @Test
    fun `rejects extra runners when bootstrap support is disabled`() {
        val selection = BuildGraphBootstrapStepSelector.select(build(bootstrapRunner("p4", "P4 Login"), buildGraphRunner()))

        selection shouldBe null
    }

    @Test
    fun `selects all runners before BuildGraph`() {
        val p4Login = bootstrapRunner("p4", "P4 Login")
        val clientSetup = bootstrapRunner("client", "Client Setup")
        val buildGraphRunner =
            buildGraphRunner(
                BuildGraphBootstrapModeParameter.name to BuildGraphBootstrapMode.AllBeforeBuildGraph.name,
            )

        val selection = BuildGraphBootstrapStepSelector.select(build(p4Login, clientSetup, buildGraphRunner))

        selection?.buildGraphRunner shouldBe buildGraphRunner
        selection?.bootstrapRunners shouldContainExactly listOf(p4Login, clientSetup)
    }

    @Test
    fun `rejects runners after BuildGraph`() {
        val selection =
            BuildGraphBootstrapStepSelector.select(
                build(
                    bootstrapRunner("p4", "P4 Login"),
                    buildGraphRunner(BuildGraphBootstrapModeParameter.name to BuildGraphBootstrapMode.AllBeforeBuildGraph.name),
                    bootstrapRunner("after", "After BuildGraph"),
                ),
            )

        selection shouldBe null
    }

    @Test
    fun `selects configured bootstrap runners by id or name`() {
        val p4Login = bootstrapRunner("p4", "P4 Login")
        val clientSetup = bootstrapRunner("client", "Client Setup")
        val buildGraphRunner =
            buildGraphRunner(
                BuildGraphBootstrapModeParameter.name to BuildGraphBootstrapMode.Selected.name,
                BuildGraphBootstrapStepRefsParameter.name to "p4\nClient Setup",
                BuildGraphBootstrapApplyToParameter.name to BuildGraphBootstrapApplyTo.NodesOnly.name,
            )

        val selection = BuildGraphBootstrapStepSelector.select(build(p4Login, clientSetup, buildGraphRunner))

        selection?.setupBootstrapRunners shouldBe emptyList()
        selection?.nodeBootstrapRunners shouldContainExactly listOf(p4Login, clientSetup)
    }

    @Test
    fun `rejects missing selected bootstrap runners`() {
        val selection =
            BuildGraphBootstrapStepSelector.select(
                build(
                    bootstrapRunner("p4", "P4 Login"),
                    buildGraphRunner(
                        BuildGraphBootstrapModeParameter.name to BuildGraphBootstrapMode.Selected.name,
                        BuildGraphBootstrapStepRefsParameter.name to "Missing Step",
                    ),
                ),
            )

        selection shouldBe null
    }

    private fun build(vararg runners: SBuildRunnerDescriptor): BuildPromotion =
        mockk {
            every { buildSettings } returns
                mockk<ReadOnlyBuildSettings> {
                    every { buildRunners } returns runners.toList()
                }
        }

    private fun buildGraphRunner(vararg extraParameters: Pair<String, String>): SBuildRunnerDescriptor =
        runner(
            id = "buildGraph",
            name = "BuildGraph",
            type = UnrealEngineRunner.RUN_TYPE,
            parameters =
                mapOf(
                    UnrealCommandTypeParameter.name to UnrealCommandType.BuildGraph.value,
                    BuildGraphModeParameter.name to BuildGraphModeParameter.distributed.name,
                ) + extraParameters,
        )

    private fun bootstrapRunner(
        id: String,
        name: String,
    ): SBuildRunnerDescriptor =
        runner(
            id = id,
            name = name,
            type = "simpleRunner",
            parameters = emptyMap(),
        )

    private fun runner(
        id: String,
        name: String,
        type: String,
        parameters: Map<String, String>,
    ): SBuildRunnerDescriptor {
        val runType = mockk<RunType>()
        every { runType.type } returns type

        return mockk {
            every { this@mockk.id } returns id
            every { this@mockk.name } returns name
            every { this@mockk.type } returns type
            every { this@mockk.parameters } returns parameters
            every { this@mockk.runType } returns runType
        }
    }
}
