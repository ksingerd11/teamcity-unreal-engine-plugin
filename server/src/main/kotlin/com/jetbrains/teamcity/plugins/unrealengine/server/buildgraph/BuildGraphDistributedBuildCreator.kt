package com.jetbrains.teamcity.plugins.unrealengine.server.buildgraph

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.jetbrains.teamcity.plugins.unrealengine.common.Error
import com.jetbrains.teamcity.plugins.unrealengine.common.UnrealPluginLoggers
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphDiagnosticsSettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphExecutionSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLoggingSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLoggingSettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetrySettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetrySettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRunnerInternalSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphTimeoutSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphTimeoutSettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.toMap
import com.jetbrains.teamcity.plugins.unrealengine.common.parameters.AdditionalArgumentsParameter
import com.jetbrains.teamcity.plugins.unrealengine.server.build.DistributedBuild
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.addBuildRunnerCopyFrom
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.addDependencies
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.addUnrealRunner
import jetbrains.buildServer.requirements.Requirement
import jetbrains.buildServer.requirements.RequirementType
import jetbrains.buildServer.serverSide.BuildPromotionEx
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.SimpleParameter

class BuildGraphDistributedBuildCreator(
    private val virtualBuildCreator: BuildGraphVirtualBuildCreator,
) {
    companion object {
        private val logger = UnrealPluginLoggers.get<BuildGraphDistributedBuildCreator>()
    }

    context(_: Raise<Error>)
    fun create(
        originalBuild: SBuild,
        buildGraph: BuildGraph<BuildGraphNodeGroup>,
    ): DistributedBuild =
        with(virtualBuildCreator.inContextOf(originalBuild.buildPromotion)) {
            createDistributedBuild(buildGraph)
        }

    context(context: BuildGraphVirtualBuildCreator.VirtualBuildCreationContext)
    private fun createDistributedBuild(buildGraph: BuildGraph<BuildGraphNodeGroup>): DistributedBuild {
        val groupDependencies = mutableMapOf<BuildGraphNodeGroup, MutableList<BuildPromotionEx>>()

        val buildsToAdd =
            buildGraph
                .topologicalSort()
                .map {
                    val build = createBuildForGroupOfNodes(it)

                    for (successor in buildGraph.adjacencyList[it]!!) {
                        groupDependencies.computeIfAbsent(successor) { mutableListOf() }
                        groupDependencies[successor]!!.add(build)
                    }
                    build.addDependencies(groupDependencies[it].orEmpty())
                    build
                }

        return DistributedBuild(buildsToAdd)
    }

    context(context: BuildGraphVirtualBuildCreator.VirtualBuildCreationContext)
    private fun createBuildForGroupOfNodes(group: BuildGraphNodeGroup): BuildPromotionEx {
        val bootstrapSelection = BuildGraphBootstrapStepSelector.select(context.originalBuild)!!
        val originalRunnerParameters = bootstrapSelection.buildGraphRunner.parameters
        val originalBuildId = context.originalBuild.id.toString()
        val retrySettings = createRetrySettings(originalRunnerParameters)
        val timeoutSettings = createTimeoutSettings(originalRunnerParameters)
        val diagnosticsSettings = BuildGraphDiagnosticsSettingsParameter.parse(originalRunnerParameters)
        val loggingSettings =
            either { BuildGraphLoggingSettingsParameter.parse(originalRunnerParameters) }.getOrElse {
                logger.warn("Unable to parse BuildGraph logging settings for generated node builds. Falling back to default behavior")
                BuildGraphLoggingSettings()
            }

        return virtualBuildCreator.create(group.name) {
            bootstrapSelection.nodeBootstrapRunners.forEach { runner ->
                addBuildRunnerCopyFrom(runner)
            }

            for (node in group.nodes) {
                val parameters =
                    buildMap {
                        putAll(originalRunnerParameters)
                        executeSingleNode(node.name)
                        addTraceabilityParameters(originalBuildId, group.name, node.name, diagnosticsSettings.traceGeneratedBuilds)
                        addInternalGraphSettings(
                            originalBuildId,
                            retrySettings.nodeExecutionSettings(
                                node.name,
                                loggingSettings,
                                timeoutSettings.timeoutForNode(node.name),
                            ),
                        )
                    }

                addUnrealRunner(node.name, parameters)
            }

            // This cast is safe because we're operating on a finishing build (Setup),
            // which already has its build number resolved.
            buildNumberPattern = (context.originalBuild.associatedBuild as SRunningBuild).buildNumber

            if (diagnosticsSettings.traceGeneratedBuilds) {
                addConfigParameter(SimpleParameter("unreal-engine.build-graph.original-build-id", originalBuildId))
                addConfigParameter(SimpleParameter("unreal-engine.build-graph.generated-role", "node-group"))
                addConfigParameter(SimpleParameter("unreal-engine.build-graph.group", group.name))
                addConfigParameter(SimpleParameter("unreal-engine.build-graph.nodes", group.nodes.joinToString(";") { it.name }))
            }

            if (group.agents.any()) {
                addRequirement(
                    Requirement(
                        "unreal-engine.build-graph.agent.type",
                        ".*(;|^)(${group.agents.joinToString(separator = "|")})(;|$).*",
                        RequirementType.MATCHES,
                    ),
                )
            }
        }
    }

    private fun MutableMap<String, String>.executeSingleNode(name: String) =
        put(AdditionalArgumentsParameter.name, get(AdditionalArgumentsParameter.name) + " \"-SingleNode=$name\"")

    private fun MutableMap<String, String>.addTraceabilityParameters(
        originalBuildId: String,
        groupName: String,
        nodeName: String,
        enabled: Boolean,
    ) {
        if (!enabled) {
            return
        }

        put("unreal-engine.build-graph.original-build-id", originalBuildId)
        put("unreal-engine.build-graph.group", groupName)
        put("unreal-engine.build-graph.node", nodeName)
    }

    private fun MutableMap<String, String>.addInternalGraphSettings(
        originalBuildId: String,
        executionSettings: BuildGraphExecutionSettings,
    ) = putAll(BuildGraphRunnerInternalSettings.RegularBuildSettings(originalBuildId, executionSettings).toMap())

    private fun createRetrySettings(originalRunnerParameters: Map<String, String>): BuildGraphRetrySettings =
        either { BuildGraphRetrySettingsParameter.parse(originalRunnerParameters) }.getOrElse {
            logger.warn("Unable to parse BuildGraph retry settings for generated node builds. Falling back to default behavior")
            BuildGraphRetrySettings()
        }

    private fun createTimeoutSettings(originalRunnerParameters: Map<String, String>): BuildGraphTimeoutSettings =
        either { BuildGraphTimeoutSettingsParameter.parse(originalRunnerParameters) }.getOrElse {
            logger.warn("Unable to parse BuildGraph timeout settings for generated node builds. Falling back to default behavior")
            BuildGraphTimeoutSettings()
        }
}
