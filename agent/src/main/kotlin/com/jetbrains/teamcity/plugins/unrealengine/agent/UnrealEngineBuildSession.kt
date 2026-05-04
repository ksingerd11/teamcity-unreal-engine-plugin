package com.jetbrains.teamcity.plugins.unrealengine.agent

import arrow.core.Either
import arrow.core.raise.either
import com.jetbrains.teamcity.plugins.unrealengine.common.UnrealPluginLoggers
import jetbrains.buildServer.RunBuildException
import jetbrains.buildServer.agent.BuildFinishedStatus
import jetbrains.buildServer.agent.runner.CommandExecution
import jetbrains.buildServer.agent.runner.MultiCommandBuildSession
import kotlinx.coroutines.runBlocking

class UnrealEngineBuildSession(
    private val workflowCreator: WorkflowCreator,
    private val unrealBuildContext: UnrealBuildContext,
) : MultiCommandBuildSession {
    companion object {
        private val logger = UnrealPluginLoggers.get<UnrealEngineBuildSession>()
    }

    private lateinit var workflow: Workflow

    private val executingCommands = ArrayDeque<UnrealEngineCommandExecution>(1)
    private val completedCommands = mutableListOf<CompletedCommand>()

    override fun sessionStarted() =
        runBlocking {
            with(unrealBuildContext) {
                workflow =
                    when (val result = either { workflowCreator.create() }) {
                        is Either.Left -> {
                            logger.error("There was an error during workflow construction: ${result.value.message}")
                            throw RunBuildException("Workflow cannot be created. Error: ${result.value.message}")
                        }
                        is Either.Right -> result.value
                    }
            }
        }

    override fun getNextCommand(): CommandExecution? {
        processPreviousCommandCompletion()

        val previousCommand = completedCommands.lastOrNull()
        val nextCommand = workflow.next(completedCommands)
        logRetrySummary(previousCommand, nextCommand)

        if (nextCommand != null) {
            executingCommands.addLast(nextCommand)
        }

        return nextCommand
    }

    override fun sessionFinished(): BuildFinishedStatus {
        processPreviousCommandCompletion()

        return workflow.onCompletion(unrealBuildContext, completedCommands)
    }

    private fun processPreviousCommandCompletion() {
        executingCommands.removeLastOrNull()?.let {
            when (val state = it.state) {
                is UnrealEngineCommandState.Finished -> completedCommands.add(CompletedCommand(it, state.exitCode))
                else -> {
                    logger.warn(
                        "Next session command has been requested, but the previous one hasn't been completed yet." +
                            " Previous command state: $state",
                    )
                }
            }
        }
    }

    private fun logRetrySummary(
        previousCommand: CompletedCommand?,
        nextCommand: UnrealEngineCommandExecution?,
    ) {
        val command = previousCommand?.command ?: return
        if (!command.logRetrySummaries || command.retryGroupId == null) {
            return
        }

        val sameRetryGroup = nextCommand?.retryGroupId == command.retryGroupId
        when {
            previousCommand.exitCode == 0 && command.attempt < command.maxAttempts ->
                unrealBuildContext.build.buildLogger.message(
                    "BuildGraph \"${command.retryGroupId}\" succeeded on attempt ${command.attempt}/${command.maxAttempts}; skipping remaining attempts.",
                )
            previousCommand.exitCode != 0 && sameRetryGroup ->
                unrealBuildContext.build.buildLogger.warning(
                    "BuildGraph \"${command.retryGroupId}\" failed on attempt ${command.attempt}/${command.maxAttempts}; retrying.",
                )
            previousCommand.exitCode != 0 && command.attempt < command.maxAttempts ->
                unrealBuildContext.build.buildLogger.warning(
                    "BuildGraph \"${command.retryGroupId}\" failed on attempt ${command.attempt}/${command.maxAttempts}; retry condition was not met.",
                )
        }
    }
}
