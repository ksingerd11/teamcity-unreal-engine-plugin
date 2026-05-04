package com.jetbrains.teamcity.plugins.unrealengine.agent.build.log

import com.jetbrains.teamcity.plugins.framework.common.TeamCityLoggers
import com.jetbrains.teamcity.plugins.unrealengine.agent.RetrySignal
import com.jetbrains.teamcity.plugins.unrealengine.agent.UnrealBuildContext
import com.jetbrains.teamcity.plugins.unrealengine.agent.buildcookrun.BuildCookRunWorkflowCreator
import com.jetbrains.teamcity.plugins.unrealengine.common.UnrealPluginLoggers
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLoggingSettings
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.agent.runner.ProcessListenerAdapter
import java.time.Instant

class UnrealEngineProcessListenerFactory(
    private val logEventParser: UnrealLogEventParser,
    private val logSinkFactory: UnrealLogSinkFactory,
) {
    context(context: UnrealBuildContext)
    fun create(vararg handlers: LogEventHandler) = create(UnrealProcessListenerSettings(), *handlers)

    context(context: UnrealBuildContext)
    fun create(
        settings: UnrealProcessListenerSettings,
        vararg handlers: LogEventHandler,
    ) = UnrealEngineProcessListener(
        context.build.buildLogger,
        logEventParser,
        handlers.asList(),
        settings.reportBuildProblems,
        logSinkFactory.create(settings.loggingSettings, settings.executionName, settings.attempt, settings.maxAttempts),
        settings.retryFailurePatterns.map { Regex(it, RegexOption.IGNORE_CASE) },
        settings.retrySignal,
        settings.onFailedProcessDiagnostics,
        settings.flushBuildProblemsOnFailedAttemptWithoutRetrySignal,
    )
}

data class UnrealProcessListenerSettings(
    val loggingSettings: BuildGraphLoggingSettings = BuildGraphLoggingSettings(),
    val reportBuildProblems: Boolean = true,
    val executionName: String? = null,
    val attempt: Int = 1,
    val maxAttempts: Int = 1,
    val retryFailurePatterns: List<String> = emptyList(),
    val retrySignal: RetrySignal = RetrySignal(),
    val onFailedProcessDiagnostics: (Int, List<String>) -> Unit = { _, _ -> },
    val flushBuildProblemsOnFailedAttemptWithoutRetrySignal: Boolean = false,
)

class UnrealEngineProcessListener(
    private val buildLogger: BuildProgressLogger,
    private val logEventParser: UnrealLogEventParser,
    private val handlers: Collection<LogEventHandler>,
    private val reportBuildProblems: Boolean,
    private val logSink: UnrealLogSink,
    private val retryFailurePatterns: List<Regex>,
    private val retrySignal: RetrySignal,
    private val onFailedProcessDiagnostics: (Int, List<String>) -> Unit,
    private val flushBuildProblemsOnFailedAttemptWithoutRetrySignal: Boolean,
) : ProcessListenerAdapter() {
    companion object {
        private val agentLogger = UnrealPluginLoggers.get<BuildCookRunWorkflowCreator>()
        private val buildStdOutLogger = TeamCityLoggers.buildStdOut()
    }

    private val pendingBuildProblems = mutableListOf<BuildProblemData>()
    private val recentMessages = ArrayDeque<String>(200)

    override fun onStandardOutput(text: String) {
        val event = logEventParser.parse(text)

        if (event.message.isEmpty()) {
            return
        }

        logSink.write(event)
        remember(event.message)
        updateRetrySignal(event.message)

        val handler = handlers.firstOrNull { it.tryHandleEvent(event) }
        if (handler != null) {
            agentLogger.debug(
                """Log event was handled by "${handler::class.simpleName}":
                |${event.toLogString()}
                """.trimMargin(),
            )
            return
        }

        when (event.level) {
            LogLevel.Error, LogLevel.Critical -> {
                buildStdOutLogger.error(event.message)
                if (reportBuildProblems) {
                    buildLogger.logBuildProblem(event.asBuildProblem())
                } else {
                    event.asBuildProblem()?.let { pendingBuildProblems.add(it) }
                    buildLogger.warning(event.message)
                }
            }
            LogLevel.Warning -> {
                buildStdOutLogger.warn(event.message)
                buildLogger.warning(event.message)
            }
            else -> {
                buildStdOutLogger.info(event.message)
                buildLogger.message(event.message)
            }
        }
    }

    override fun onErrorOutput(text: String) {
        logSink.write(UnrealLogEvent(Instant.now(), LogLevel.Warning, text, "stderr"))
        remember(text)
        updateRetrySignal(text)
        buildLogger.warning(text)
        buildStdOutLogger.warn(text)
    }

    override fun processFinished(exitCode: Int) {
        if (exitCode != 0) {
            onFailedProcessDiagnostics(exitCode, recentMessages.toList())
        }

        val shouldFlushSuppressedProblems =
            !reportBuildProblems &&
                (exitCode == 0 || (flushBuildProblemsOnFailedAttemptWithoutRetrySignal && !retrySignal.matched))

        if (shouldFlushSuppressedProblems) {
            pendingBuildProblems.forEach { buildLogger.logBuildProblem(it) }
        }

        logSink.close()
    }

    private fun remember(message: String) {
        if (recentMessages.size == 200) {
            recentMessages.removeFirst()
        }
        recentMessages.addLast(message)
    }

    private fun updateRetrySignal(message: String) {
        if (retryFailurePatterns.any { it.containsMatchIn(message) }) {
            retrySignal.markMatched()
        }
    }

    private fun UnrealLogEvent.asBuildProblem(): BuildProblemData? {
        val problemType =
            channel?.let { "unreal-engine-error:$it" }
                ?: "unreal-engine-error"

        val problemId = "$problemType:$message".hashCode().toString()

        return BuildProblemData.createBuildProblem(
            problemId,
            problemType,
            message,
        )
    }
}
