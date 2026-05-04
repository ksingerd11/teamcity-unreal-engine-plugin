package com.jetbrains.teamcity.plugins.unrealengine.server.buildgraph

import arrow.core.raise.Raise
import arrow.core.raise.either
import com.jetbrains.teamcity.plugins.unrealengine.common.Error
import com.jetbrains.teamcity.plugins.unrealengine.common.UnrealPluginLoggers
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetrySettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.server.build.state.DistributedBuildStateTracker
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.asBuildPromotionEx
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.asTriggeredBy
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.distributedBuildGraphRunnerOrNull
import jetbrains.buildServer.messages.BuildMessage1
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.serverSide.BuildQueueEx
import jetbrains.buildServer.serverSide.SRunningBuild
import java.util.Date

class BuildGraphDistributedSetupOrchestrator(
    private val validator: BuildGraphSetupBuildValidator,
    private val definitionLoader: BuildGraphDefinitionLoader,
    private val dependencyConnector: BuildGraphDependencyConnector,
    private val buildCreator: BuildGraphDistributedBuildCreator,
    private val buildStateTracker: DistributedBuildStateTracker,
    private val settingsInitializer: BuildGraphSettingsInitializer,
    private val buildQueue: BuildQueueEx,
) {
    companion object {
        private val logger = UnrealPluginLoggers.get<BuildGraphDistributedSetupOrchestrator>()
    }

    context(_: Raise<Error>)
    fun setupDistributedBuild(setupBuild: SRunningBuild) {
        val (validatedSetupBuild, originalBuild) = validator.validate(setupBuild)
        val buildGraph = definitionLoader.loadFrom(validatedSetupBuild)
        warnAboutUnmatchedRetryRules(
            setupBuild,
            originalBuild.buildPromotion
                .distributedBuildGraphRunnerOrNull()
                ?.parameters
                .orEmpty(),
            buildGraph,
        )

        val settings = settingsInitializer.initializeBuildSettings(originalBuild, buildGraph.badges)
        val distributedBuild =
            buildCreator.create(originalBuild, buildGraph).also {
                it.builds.onEach { build -> build.persist() }
            }

        if (settings.badgePosting is BadgePostingConfig.Enabled) {
            buildStateTracker.track(originalBuild, distributedBuild)
        }

        dependencyConnector.connect(validatedSetupBuild, distributedBuild, originalBuild)

        buildQueue.addToQueue(
            distributedBuild.builds.associateWith { null },
            originalBuild.buildPromotion.asBuildPromotionEx().asTriggeredBy(),
        )
    }

    private fun warnAboutUnmatchedRetryRules(
        setupBuild: SRunningBuild,
        runnerParameters: Map<String, String>,
        buildGraph: BuildGraph<BuildGraphNodeGroup>,
    ) {
        val retrySettings =
            either { BuildGraphRetrySettingsParameter.parse(runnerParameters) }.getOrNull()
                ?: return

        if (!retrySettings.enabled || !retrySettings.warnOnUnmatchedRules) {
            return
        }

        val nodes = buildGraph.adjacencyList.keys.flatMap { group -> group.nodes.map { it.name } }
        val unmatchedRules =
            retrySettings.nodeRules
                .filter { rule -> nodes.none { node -> rule.matches(node) } }
                .map { it.pattern }

        if (unmatchedRules.isEmpty()) {
            return
        }

        val message = "BuildGraph retry rules did not match any exported nodes: ${unmatchedRules.joinToString()}"
        logger.warn(message)
        @Suppress("DEPRECATION")
        setupBuild.addBuildMessage(BuildMessage1("", "message", Status.WARNING, Date(), message))
    }
}
