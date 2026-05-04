package com.jetbrains.teamcity.plugins.unrealengine.agent

import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetryCondition
import jetbrains.buildServer.agent.runner.CommandExecution
import jetbrains.buildServer.agent.runner.ProcessListener
import jetbrains.buildServer.agent.runner.ProgramCommandLine
import jetbrains.buildServer.agent.runner.TerminationAction
import java.io.File

sealed interface UnrealEngineCommandState {
    data object NotStarted : UnrealEngineCommandState

    data object Running : UnrealEngineCommandState

    data class Finished(
        val exitCode: Int,
    ) : UnrealEngineCommandState
}

class UnrealEngineCommandExecution(
    private val commandLine: ProgramCommandLine,
    private val listener: ProcessListener,
    val retryGroupId: String? = null,
    val attempt: Int = 1,
    val maxAttempts: Int = 1,
    private val delayBeforeStartSeconds: Long = 0,
    private val retryCondition: BuildGraphRetryCondition = BuildGraphRetryCondition.AnyFailure,
    private val retrySignal: RetrySignal = RetrySignal(),
    val logRetrySummaries: Boolean = false,
    val timeoutSeconds: Long = 0,
    private val onTimeout: (String) -> Unit = {},
) : CommandExecution {
    @Volatile
    var state: UnrealEngineCommandState = UnrealEngineCommandState.NotStarted
        private set

    override fun onStandardOutput(text: String) = listener.onStandardOutput(text)

    override fun onErrorOutput(text: String) = listener.onErrorOutput(text)

    override fun processStarted(
        programCommandLine: String,
        workingDirectory: File,
    ) {
        state = UnrealEngineCommandState.Running
        scheduleTimeout()
        listener.processStarted(programCommandLine, workingDirectory)
    }

    override fun processFinished(exitCode: Int) {
        state = UnrealEngineCommandState.Finished(exitCode)
        listener.processFinished(exitCode)
    }

    override fun makeProgramCommandLine() = commandLine

    override fun beforeProcessStarted() {
        if (delayBeforeStartSeconds > 0) {
            Thread.sleep(delayBeforeStartSeconds * 1000)
        }
    }

    override fun interruptRequested() = TerminationAction.KILL_PROCESS_TREE

    override fun isCommandLineLoggingEnabled() = true

    fun shouldRetry(exitCode: Int): Boolean =
        retryGroupId != null &&
            attempt < maxAttempts &&
            exitCode != 0 &&
            when (retryCondition) {
                BuildGraphRetryCondition.AnyFailure -> true
                BuildGraphRetryCondition.MatchingLog -> retrySignal.matched
            }

    private fun scheduleTimeout() {
        if (timeoutSeconds <= 0) {
            return
        }

        Thread(
            {
                Thread.sleep(timeoutSeconds.coerceAtMost(Long.MAX_VALUE / 1000) * 1000)
                if (state is UnrealEngineCommandState.Running) {
                    onTimeout("Unreal command timed out after ${timeoutSeconds}s")
                }
            },
            "unreal-command-timeout-$retryGroupId-$attempt",
        ).apply {
            isDaemon = true
            start()
        }
    }
}

class RetrySignal {
    @Volatile
    var matched = false
        private set

    fun markMatched() {
        matched = true
    }
}
