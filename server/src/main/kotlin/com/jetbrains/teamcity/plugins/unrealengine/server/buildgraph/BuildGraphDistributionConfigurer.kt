package com.jetbrains.teamcity.plugins.unrealengine.server.buildgraph

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.jetbrains.teamcity.plugins.unrealengine.common.GenericError
import com.jetbrains.teamcity.plugins.unrealengine.common.UnrealPluginLoggers
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphDiagnosticsSettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphExecutionSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLoggingSettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetrySettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRunnerInternalSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphTimeoutSettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.toMap
import com.jetbrains.teamcity.plugins.unrealengine.common.parameters.AdditionalArgumentsParameter
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.addBuildRunnerCopyFrom
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.addUnrealRunner
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.asBuildPromotionEx
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.asTriggeredBy
import com.jetbrains.teamcity.plugins.unrealengine.server.extensions.default
import jetbrains.buildServer.agent.AgentRuntimeProperties
import jetbrains.buildServer.serverSide.BuildAttributes
import jetbrains.buildServer.serverSide.BuildPromotion
import jetbrains.buildServer.serverSide.BuildPromotionEx
import jetbrains.buildServer.serverSide.BuildQueueEx
import jetbrains.buildServer.serverSide.SimpleParameter
import jetbrains.buildServer.serverSide.impl.LogUtil
import jetbrains.buildServer.util.DependencyOptionSupportImpl
import jetbrains.buildServer.virtualConfiguration.processor.ProcessVirtualConfigurations

class BuildGraphDistributionConfigurer(
    private val buildQueue: BuildQueueEx,
    private val virtualBuildCreator: BuildGraphVirtualBuildCreator,
    private val settings: BuildGraphSettings,
) : ProcessVirtualConfigurations {
    companion object {
        private val logger = UnrealPluginLoggers.get<BuildGraphDistributionConfigurer>()
    }

    override fun addToQueue(build: BuildPromotion): MutableList<BuildPromotion> =
        build
            .asBuildPromotionEx()
            .let {
                if (shouldDistributeBuild(it)) {
                    it.ensureChangeCollectionWhileInQueue()
                }
                mutableListOf()
            }

    private fun BuildPromotionEx.ensureChangeCollectionWhileInQueue() =
        setAttribute(BuildAttributes.FREEZE_REQUIRES_COLLECTED_CHANGES, true.toString())

    override fun freeze(build: BuildPromotion): MutableList<BuildPromotion> =
        runCatching {
            distributeBuild(build.asBuildPromotionEx()).toMutableList()
        }.getOrElse {
            logger.error("An error occurred while trying to set up BuildGraph build distribution", it)
            mutableListOf()
        }

    private fun distributeBuild(build: BuildPromotionEx) =
        sequence<BuildPromotion> {
            if (!shouldDistributeBuild(build)) {
                logger.debug(
                    """
                    Build won't be distributed because it doesn't satisfy some of the distribution criteria.
                    It is either already virtual, doesn't contain a single active Unreal build step,
                    or doesn't have any VCS roots attached
                    """.trimIndent(),
                )
                return@sequence
            }

            logger.info(
                "Build graph build ${build.toLogString()} is " +
                    "eligible for distribution across multiple machines",
            )

            when (val result = setupBuildDistribution(build)) {
                is Either.Left -> {
                    logger.error(
                        "An error occurred while setting up a distribution of the build " +
                            "${build.toLogString()}. Proceeding with the default setup",
                    )
                    return@sequence
                }
                is Either.Right -> {
                    yield(result.value)
                }
            }
        }

    override fun getType() = "UnrealEngine_BuildGraph"

    private fun shouldDistributeBuild(build: BuildPromotionEx): Boolean {
        val isVirtual = build.buildType?.isVirtual ?: false
        val isDistributedBuildGraph = BuildGraphBootstrapStepSelector.select(build) != null

        return build.vcsRootEntries.isNotEmpty() && !isVirtual && isDistributedBuildGraph
    }

    private fun setupBuildDistribution(originalBuild: BuildPromotionEx): Either<GenericError, BuildPromotionEx> =
        either {
            val originalBuildParentProjectId = originalBuild.projectId
            ensure(originalBuildParentProjectId != null) {
                logger.debug("The build ${originalBuild.toLogString()} has no parent project, skipping")
                raise(GenericError("Build is missing its parent project"))
            }

            originalBuild.markAsComposite()
            val setupBuild = createBuildGraphSetupBuild(originalBuild)
            originalBuild.setAttribute(settings.buildGraphGeneratedMarker, true)
            originalBuild.persist()

            buildQueue.addToQueue(mapOf(setupBuild to null), originalBuild.asTriggeredBy())

            logger.info(
                "Build ${originalBuild.toLogString()} has been successfully converted into a " +
                    "composite build with a graph generation setup build",
            )

            setupBuild
        }

    private fun BuildPromotionEx.markAsComposite() = setAttribute(BuildAttributes.COMPOSITE_BUILD, true.toString())

    private fun createBuildGraphSetupBuild(originalBuild: BuildPromotionEx): BuildPromotionEx {
        val bootstrapSelection = BuildGraphBootstrapStepSelector.select(originalBuild)!!
        val originalRunnerParameters = bootstrapSelection.buildGraphRunner.parameters

        val setupBuild =
            with(virtualBuildCreator.inContextOf(originalBuild)) {
                virtualBuildCreator.create("Setup") {
                    val graphExportPath =
                        "%${AgentRuntimeProperties.BUILD_CHECKOUT_DIR}%/${settings.graphArtifactName}"

                    val setupRunnerParameters =
                        originalRunnerParameters +
                            mapOf(
                                AdditionalArgumentsParameter.name to
                                    originalRunnerParameters[AdditionalArgumentsParameter.name] + " \"-Export=$graphExportPath\"",
                            ) +
                            BuildGraphRunnerInternalSettings
                                .SetupBuildSettings(
                                    graphExportPath,
                                    originalBuild.id.toString(),
                                    createSetupExecutionSettings(originalRunnerParameters),
                                ).toMap()

                    bootstrapSelection.setupBootstrapRunners.forEach { runner ->
                        addBuildRunnerCopyFrom(runner)
                    }

                    addUnrealRunner(
                        "Setup",
                        setupRunnerParameters,
                    )

                    if (BuildGraphDiagnosticsSettingsParameter.parse(originalRunnerParameters).traceGeneratedBuilds) {
                        addConfigParameter(SimpleParameter("unreal-engine.build-graph.original-build-id", originalBuild.id.toString()))
                        addConfigParameter(SimpleParameter("unreal-engine.build-graph.generated-role", "setup"))
                        addConfigParameter(SimpleParameter("unreal-engine.build-graph.exported-graph-path", graphExportPath))
                    }

                    originalBuild.requirements.forEach { requirement ->
                        addRequirement(requirement)
                    }
                }
            }.also {
                it.setAttribute(settings.setupBuildMarker, true.toString())
            }

        val dependencyOptions = DependencyOptionSupportImpl().default()
        originalBuild.addDependency(setupBuild, dependencyOptions)

        return setupBuild
    }

    private fun createSetupExecutionSettings(originalRunnerParameters: Map<String, String>): BuildGraphExecutionSettings =
        either {
            val diagnosticsSettings = BuildGraphDiagnosticsSettingsParameter.parse(originalRunnerParameters)
            val timeoutSettings = BuildGraphTimeoutSettingsParameter.parse(originalRunnerParameters)
            BuildGraphRetrySettingsParameter
                .parse(originalRunnerParameters)
                .setupExecutionSettings(
                    BuildGraphLoggingSettingsParameter.parse(originalRunnerParameters),
                    timeoutSettings.setupTimeoutSeconds,
                    diagnosticsSettings,
                )
        }.getOrElse {
            logger.warn("Unable to parse BuildGraph execution settings for setup build. Falling back to default behavior")
            BuildGraphExecutionSettings()
        }

    private fun BuildPromotionEx.toLogString(): String = LogUtil.describe(this)
}
