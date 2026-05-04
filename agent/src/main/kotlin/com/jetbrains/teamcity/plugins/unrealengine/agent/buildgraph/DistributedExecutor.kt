package com.jetbrains.teamcity.plugins.unrealengine.agent.buildgraph

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.jetbrains.teamcity.plugins.framework.common.Environment
import com.jetbrains.teamcity.plugins.unrealengine.agent.RetrySignal
import com.jetbrains.teamcity.plugins.unrealengine.agent.UnrealBuildContext
import com.jetbrains.teamcity.plugins.unrealengine.agent.UnrealEngineCommandExecution
import com.jetbrains.teamcity.plugins.unrealengine.agent.UnrealEngineProgramCommandLine
import com.jetbrains.teamcity.plugins.unrealengine.agent.UnrealToolRegistry
import com.jetbrains.teamcity.plugins.unrealengine.agent.build.log.LogEventHandler
import com.jetbrains.teamcity.plugins.unrealengine.agent.build.log.UnrealEngineProcessListenerFactory
import com.jetbrains.teamcity.plugins.unrealengine.agent.build.log.UnrealProcessListenerSettings
import com.jetbrains.teamcity.plugins.unrealengine.agent.reporting.AutomationTestLogEventHandler
import com.jetbrains.teamcity.plugins.unrealengine.common.GenericError
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphCommand
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphExecutionSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetryCondition
import com.jetbrains.teamcity.plugins.unrealengine.common.raise
import jetbrains.buildServer.agent.impl.artifacts.ArtifactsWatcherEx
import jetbrains.buildServer.agent.runner.ProcessListener
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class DistributedExecutor(
    private val toolRegistry: UnrealToolRegistry,
    private val environment: Environment,
    private val artifactsWatcher: ArtifactsWatcherEx,
    private val settingsCreator: DistributedBuildSettingsCreator,
    private val processListenerFactory: UnrealEngineProcessListenerFactory,
) {
    context(_: Raise<GenericError>, context: UnrealBuildContext)
    suspend fun execute(command: BuildGraphCommand): List<UnrealEngineCommandExecution> {
        val settings =
            either {
                settingsCreator.from(context.runnerParameters)
            }.getOrElse {
                raise("Unable to get distributed BuildGraph build settings. Error: ${it.message}")
            }

        return when (settings) {
            is DistributedBuildSettings.SetupBuildSettings -> setupCommands(settings, command)
            is DistributedBuildSettings.RegularBuildSettings -> executeCommands(settings, command)
        }
    }

    context(_: Raise<GenericError>, context: UnrealBuildContext)
    private suspend fun setupCommands(
        settings: DistributedBuildSettings.SetupBuildSettings,
        command: BuildGraphCommand,
    ): List<UnrealEngineCommandExecution> {
        val sharedDir = ensureSharedDirectoryForBuild(settings.networkShare, settings.compositeBuildId)
        val arguments = command.toArguments()

        return createUnrealCommandExecutions(
            arguments,
            settings.executionSettings,
            executionName = "Setup",
            failedDiagnostics = { attempt, exitCode, recentMessages ->
                if (settings.executionSettings.setupDiagnostics) {
                    publishSetupDiagnostics(settings, sharedDir, arguments, attempt, exitCode, recentMessages)
                }
            },
            processListenerDecorator = { listener ->
                object : ProcessListener by listener {
                    override fun processFinished(exitCode: Int) {
                        listener.processFinished(exitCode)
                        if (exitCode == 0) {
                            artifactsWatcher.addNewArtifactsPath(settings.exportedGraphPath)
                            artifactsWatcher.waitForPublishingFinish()
                        }
                    }
                }
            },
        )
    }

    context(_: Raise<GenericError>, context: UnrealBuildContext)
    private suspend fun executeCommands(
        settings: DistributedBuildSettings.RegularBuildSettings,
        command: BuildGraphCommand,
    ): List<UnrealEngineCommandExecution> {
        val sharedDir = ensureSharedDirectoryForBuild(settings.networkShare, settings.compositeBuildId)

        return createUnrealCommandExecutions(
            command.toArguments() +
                listOf(
                    "-SharedStorageDir=$sharedDir",
                    "-WriteToSharedStorage",
                ),
            settings.executionSettings,
            executionName = command.extraArguments.singleNodeName() ?: command.target.value,
            AutomationTestLogEventHandler(context),
        )
    }

    context(_: Raise<GenericError>, context: UnrealBuildContext)
    private suspend fun createUnrealCommandExecutions(
        arguments: List<String>,
        executionSettings: BuildGraphExecutionSettings,
        executionName: String,
        vararg handlers: LogEventHandler,
        failedDiagnostics: (Int, Int, List<String>) -> Unit = { _, _, _ -> },
        processListenerDecorator: (ProcessListener) -> ProcessListener = { it },
    ): List<UnrealEngineCommandExecution> {
        val maxAttempts = executionSettings.maxAttempts.coerceAtLeast(1)

        return (1..maxAttempts).map { attempt ->
            val retrySignal = RetrySignal()
            val listener =
                processListenerFactory.create(
                    UnrealProcessListenerSettings(
                        loggingSettings = executionSettings.loggingSettings,
                        reportBuildProblems = attempt == maxAttempts,
                        executionName = executionName,
                        attempt = attempt,
                        maxAttempts = maxAttempts,
                        retryFailurePatterns = executionSettings.retryFailurePatterns,
                        retrySignal = retrySignal,
                        onFailedProcessDiagnostics = { exitCode, recentMessages ->
                            failedDiagnostics(attempt, exitCode, recentMessages)
                        },
                        flushBuildProblemsOnFailedAttemptWithoutRetrySignal =
                            executionSettings.retryCondition == BuildGraphRetryCondition.MatchingLog,
                    ),
                    *handlers,
                )

            UnrealEngineCommandExecution(
                UnrealEngineProgramCommandLine(
                    environment,
                    context.buildParameters.environmentVariables,
                    context.workingDirectory,
                    toolRegistry.automationTool(context.runnerParameters).executablePath,
                    arguments,
                ),
                processListenerDecorator(listener),
                retryGroupId = if (maxAttempts > 1) executionName else null,
                attempt = attempt,
                maxAttempts = maxAttempts,
                delayBeforeStartSeconds = if (attempt == 1) 0 else executionSettings.retryDelaySeconds,
                retryCondition = executionSettings.retryCondition,
                retrySignal = retrySignal,
                logRetrySummaries = executionSettings.logRetrySummaries,
                timeoutSeconds = executionSettings.timeoutSeconds,
                onTimeout = { message ->
                    context.build.buildLogger.warning("$message. Stopping build; TeamCity does not expose a per-attempt process kill API.")
                    context.build.interruptBuild(message, true)
                },
            )
        }
    }

    context(context: UnrealBuildContext)
    private fun publishSetupDiagnostics(
        settings: DistributedBuildSettings.SetupBuildSettings,
        sharedDir: String,
        arguments: List<String>,
        attempt: Int,
        exitCode: Int,
        recentMessages: List<String>,
    ) {
        val diagnosticsDir = context.createDirectory(context.agentTempDirectory, "unreal-engine-buildgraph-diagnostics")
        val diagnosticsFile = Path.of(diagnosticsDir, "setup-attempt-$attempt.txt")
        val content =
            buildString {
                appendLine("BuildGraph setup diagnostics")
                appendLine("Exit code: $exitCode")
                appendLine("Attempt: $attempt/${settings.executionSettings.maxAttempts.coerceAtLeast(1)}")
                appendLine("Composite build id: ${settings.compositeBuildId}")
                appendLine("Exported graph path: ${settings.exportedGraphPath}")
                appendLine("Shared storage directory: $sharedDir")
                appendLine()
                appendLine("Arguments:")
                arguments.forEach { appendLine(it) }
                appendLine()
                appendLine("Recent Unreal log lines:")
                recentMessages.forEach { appendLine(it) }
            }

        Files.writeString(diagnosticsFile, content, StandardCharsets.UTF_8)
        artifactsWatcher.addNewArtifactsPath("${diagnosticsFile.toAbsolutePath()} => unreal-setup-diagnostics")
    }

    context(context: UnrealBuildContext)
    private fun ensureSharedDirectoryForBuild(
        root: String,
        compositeBuildId: String,
    ) = context.createDirectory(context.resolvePath(root, compositeBuildId))

    private fun List<String>.singleNodeName(): String? =
        firstNotNullOfOrNull { argument ->
            argument
                .removeSurrounding("\"")
                .takeIf { it.startsWith("-SingleNode=") }
                ?.substringAfter("-SingleNode=")
        }
}
