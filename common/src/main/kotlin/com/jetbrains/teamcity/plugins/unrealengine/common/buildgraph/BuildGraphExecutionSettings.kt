package com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class BuildGraphRetrySettings(
    val enabled: Boolean = false,
    val setupMaxAttempts: Int = 1,
    val nodeRules: List<BuildGraphNodeRetryRule> = emptyList(),
    val retryDelaySeconds: Long = 0,
    val condition: BuildGraphRetryCondition = BuildGraphRetryCondition.AnyFailure,
    val failurePatterns: List<String> = emptyList(),
    val logRetrySummaries: Boolean = false,
    val warnOnUnmatchedRules: Boolean = false,
) {
    fun maxAttemptsForNode(nodeName: String): Int =
        if (!enabled) {
            1
        } else {
            nodeRules.firstOrNull { it.matches(nodeName) }?.maxAttempts ?: 1
        }

    fun setupExecutionSettings(
        loggingSettings: BuildGraphLoggingSettings,
        timeoutSeconds: Long = 0,
        diagnosticsSettings: BuildGraphDiagnosticsSettings = BuildGraphDiagnosticsSettings(),
    ) = BuildGraphExecutionSettings(
        maxAttempts = if (enabled) setupMaxAttempts else 1,
        retryDelaySeconds = if (enabled) retryDelaySeconds else 0,
        loggingSettings = loggingSettings,
        timeoutSeconds = timeoutSeconds,
        retryCondition = condition,
        retryFailurePatterns = failurePatterns,
        logRetrySummaries = logRetrySummaries,
        setupDiagnostics = diagnosticsSettings.publishSetupDiagnostics,
    )

    fun nodeExecutionSettings(
        nodeName: String,
        loggingSettings: BuildGraphLoggingSettings,
        timeoutSeconds: Long = 0,
    ) = BuildGraphExecutionSettings(
        maxAttempts = maxAttemptsForNode(nodeName),
        retryDelaySeconds = if (enabled) retryDelaySeconds else 0,
        loggingSettings = loggingSettings,
        timeoutSeconds = timeoutSeconds,
        retryCondition = condition,
        retryFailurePatterns = failurePatterns,
        logRetrySummaries = logRetrySummaries,
    )
}

data class BuildGraphNodeRetryRule(
    val pattern: String,
    val maxAttempts: Int,
) {
    fun matches(nodeName: String): Boolean =
        pattern
            .split('*')
            .joinToString(separator = ".*") { Regex.escape(it) }
            .let { Regex("^$it$", RegexOption.IGNORE_CASE) }
            .matches(nodeName)
}

@Serializable
enum class BuildGraphRetryCondition {
    @SerialName("any-failure")
    AnyFailure,

    @SerialName("matching-log")
    MatchingLog,
}

data class BuildGraphTimeoutSettings(
    val setupTimeoutSeconds: Long = 0,
    val nodeRules: List<BuildGraphNodeTimeoutRule> = emptyList(),
) {
    fun timeoutForNode(nodeName: String): Long = nodeRules.firstOrNull { it.matches(nodeName) }?.timeoutSeconds ?: 0
}

data class BuildGraphNodeTimeoutRule(
    val pattern: String,
    val timeoutSeconds: Long,
) {
    fun matches(nodeName: String): Boolean =
        pattern
            .split('*')
            .joinToString(separator = ".*") { Regex.escape(it) }
            .let { Regex("^$it$", RegexOption.IGNORE_CASE) }
            .matches(nodeName)
}

data class BuildGraphDiagnosticsSettings(
    val publishSetupDiagnostics: Boolean = false,
    val traceGeneratedBuilds: Boolean = false,
)

enum class BuildGraphBootstrapMode {
    Disabled,
    AllBeforeBuildGraph,
    Selected,
}

enum class BuildGraphBootstrapApplyTo {
    SetupAndNodes,
    SetupOnly,
    NodesOnly,
}

data class BuildGraphBootstrapSettings(
    val mode: BuildGraphBootstrapMode = BuildGraphBootstrapMode.Disabled,
    val stepRefs: List<String> = emptyList(),
    val applyTo: BuildGraphBootstrapApplyTo = BuildGraphBootstrapApplyTo.SetupAndNodes,
) {
    val enabled get() = mode != BuildGraphBootstrapMode.Disabled

    val appliesToSetup get() = applyTo == BuildGraphBootstrapApplyTo.SetupAndNodes || applyTo == BuildGraphBootstrapApplyTo.SetupOnly

    val appliesToNodes get() = applyTo == BuildGraphBootstrapApplyTo.SetupAndNodes || applyTo == BuildGraphBootstrapApplyTo.NodesOnly
}

@Serializable
enum class BuildGraphLogSink {
    @SerialName("teamcity")
    TeamCityOnly,

    @SerialName("local-file")
    LocalFile,

    @SerialName("graylog")
    Graylog,

    @SerialName("local-file-and-graylog")
    LocalFileAndGraylog,
}

@Serializable
data class BuildGraphLoggingSettings(
    val sink: BuildGraphLogSink = BuildGraphLogSink.TeamCityOnly,
    @SerialName("local-directory")
    val localDirectory: String? = null,
    @SerialName("publish-local-log-artifacts")
    val publishLocalLogArtifacts: Boolean = false,
    @SerialName("graylog-endpoint")
    val graylogEndpoint: String? = null,
    @SerialName("graylog-source")
    val graylogSource: String? = null,
    @SerialName("graylog-timeout-seconds")
    val graylogTimeoutSeconds: Long = 5,
    @SerialName("graylog-extra-fields")
    val graylogExtraFields: Map<String, String> = emptyMap(),
) {
    val localFileEnabled get() = sink == BuildGraphLogSink.LocalFile || sink == BuildGraphLogSink.LocalFileAndGraylog
    val graylogEnabled get() = sink == BuildGraphLogSink.Graylog || sink == BuildGraphLogSink.LocalFileAndGraylog
}

@Serializable
data class BuildGraphExecutionSettings(
    @SerialName("max-attempts")
    val maxAttempts: Int = 1,
    @SerialName("retry-delay-seconds")
    val retryDelaySeconds: Long = 0,
    @SerialName("logging-settings")
    val loggingSettings: BuildGraphLoggingSettings = BuildGraphLoggingSettings(),
    @SerialName("timeout-seconds")
    val timeoutSeconds: Long = 0,
    @SerialName("retry-condition")
    val retryCondition: BuildGraphRetryCondition = BuildGraphRetryCondition.AnyFailure,
    @SerialName("retry-failure-patterns")
    val retryFailurePatterns: List<String> = emptyList(),
    @SerialName("log-retry-summaries")
    val logRetrySummaries: Boolean = false,
    @SerialName("setup-diagnostics")
    val setupDiagnostics: Boolean = false,
)
