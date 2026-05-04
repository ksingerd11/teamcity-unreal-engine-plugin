package com.jetbrains.teamcity.plugins.unrealengine.agent

import arrow.core.raise.Raise
import com.jetbrains.teamcity.plugins.unrealengine.common.GenericError
import com.jetbrains.teamcity.plugins.unrealengine.common.UnrealEngineRunner
import jetbrains.buildServer.agent.BuildFinishedStatus
import jetbrains.buildServer.agent.problems.ExitCodeProblemBuilder

data class Workflow(
    val commands: Collection<UnrealEngineCommandExecution>,
    val onCompletion: context(UnrealBuildContext)
    (List<CompletedCommand>) -> BuildFinishedStatus = { workflowCompleted(it) },
) {
    private val commandsQueue = ArrayDeque(commands)
    private val completedRetryGroups = mutableSetOf<String>()

    companion object {
        context(context: UnrealBuildContext)
        private fun workflowCompleted(completedCommands: List<CompletedCommand>): BuildFinishedStatus {
            val effectiveExitCodes = completedCommands.effectiveExitCodes()
            return if (effectiveExitCodes.all { it == 0 } || !context.build.failBuildOnExitCode) {
                BuildFinishedStatus.FINISHED_SUCCESS
            } else {
                effectiveExitCodes.filter { it != 0 }.forEach { reportBuildProblem(it) }
                BuildFinishedStatus.FINISHED_WITH_PROBLEMS
            }
        }

        context(context: UnrealBuildContext)
        private fun reportBuildProblem(nonZeroExitCode: Int) {
            context.build.buildLogger.logBuildProblem(
                ExitCodeProblemBuilder()
                    .setExitCode(nonZeroExitCode)
                    .setRunnerId(context.runnerId)
                    .setRunnerName(context.runnerName)
                    .setRunnerType(UnrealEngineRunner.RUN_TYPE)
                    .build(),
            )
        }

        private fun List<CompletedCommand>.effectiveExitCodes(): List<Int> =
            filter { it.command.retryGroupId == null }.map { it.exitCode } +
                filter { it.command.retryGroupId != null }
                    .groupBy { it.command.retryGroupId }
                    .values
                    .map { it.last().exitCode }
    }

    fun next(completedCommands: List<CompletedCommand>): UnrealEngineCommandExecution? {
        val previousCommand = completedCommands.lastOrNull()
        if (previousCommand != null && !previousCommand.command.shouldRetry(previousCommand.exitCode)) {
            previousCommand.command.retryGroupId?.let { completedRetryGroups.add(it) }
        }

        while (commandsQueue.firstOrNull()?.retryGroupId in completedRetryGroups) {
            commandsQueue.removeFirst()
        }

        return commandsQueue.removeFirstOrNull()
    }
}

data class CompletedCommand(
    val command: UnrealEngineCommandExecution,
    val exitCode: Int,
)

interface WorkflowCreator {
    context(_: Raise<GenericError>, context: UnrealBuildContext)
    suspend fun create(): Workflow
}
