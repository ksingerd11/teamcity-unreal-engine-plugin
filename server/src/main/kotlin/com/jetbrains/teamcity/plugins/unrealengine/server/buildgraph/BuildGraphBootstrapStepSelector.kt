package com.jetbrains.teamcity.plugins.unrealengine.server.buildgraph

import arrow.core.getOrElse
import arrow.core.raise.either
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapMode
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapSettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.activeRunners
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.distributedBuildGraphRunnerOrNull
import jetbrains.buildServer.serverSide.BuildPromotion
import jetbrains.buildServer.serverSide.SBuildRunnerDescriptor

data class BuildGraphBootstrapStepSelection(
    val buildGraphRunner: SBuildRunnerDescriptor,
    val bootstrapRunners: List<SBuildRunnerDescriptor>,
    val settings: BuildGraphBootstrapSettings,
) {
    val setupBootstrapRunners get() = if (settings.appliesToSetup) bootstrapRunners else emptyList()

    val nodeBootstrapRunners get() = if (settings.appliesToNodes) bootstrapRunners else emptyList()
}

object BuildGraphBootstrapStepSelector {
    fun select(build: BuildPromotion): BuildGraphBootstrapStepSelection? {
        val runners = build.activeRunners().toList()
        val buildGraphRunner = build.distributedBuildGraphRunnerOrNull() ?: return null
        val settings =
            either { BuildGraphBootstrapSettingsParameter.parse(buildGraphRunner.parameters) }.getOrElse {
                return null
            }

        if (!settings.enabled) {
            return if (runners.size == 1) {
                BuildGraphBootstrapStepSelection(buildGraphRunner, emptyList(), settings)
            } else {
                null
            }
        }

        val buildGraphIndex = runners.indexOf(buildGraphRunner)
        val bootstrapRunners =
            when (settings.mode) {
                BuildGraphBootstrapMode.Disabled -> emptyList()
                BuildGraphBootstrapMode.AllBeforeBuildGraph -> runners.take(buildGraphIndex)
                BuildGraphBootstrapMode.Selected -> selectByRefs(runners, buildGraphRunner, settings.stepRefs) ?: return null
            }

        if (bootstrapRunners.any { runners.indexOf(it) > buildGraphIndex }) {
            return null
        }

        val nonBuildGraphRunners = runners.filterNot { it == buildGraphRunner }
        if (nonBuildGraphRunners.any { it !in bootstrapRunners }) {
            return null
        }

        return BuildGraphBootstrapStepSelection(buildGraphRunner, bootstrapRunners, settings)
    }

    private fun selectByRefs(
        runners: List<SBuildRunnerDescriptor>,
        buildGraphRunner: SBuildRunnerDescriptor,
        refs: List<String>,
    ): List<SBuildRunnerDescriptor>? {
        val candidates = runners.filterNot { it == buildGraphRunner }

        if (refs.any { ref -> candidates.none { it.matches(ref) } }) {
            return null
        }

        return candidates.filter { candidate -> refs.any { candidate.matches(it) } }
    }

    private fun SBuildRunnerDescriptor.matches(ref: String) = id.equals(ref, ignoreCase = true) || name.equals(ref, ignoreCase = true)
}
