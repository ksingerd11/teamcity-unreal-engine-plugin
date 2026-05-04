package com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph

import arrow.core.raise.Raise
import com.jetbrains.teamcity.plugins.framework.common.ensure
import com.jetbrains.teamcity.plugins.framework.common.raise
import com.jetbrains.teamcity.plugins.unrealengine.common.PropertyValidationError
import com.jetbrains.teamcity.plugins.unrealengine.common.parameters.CheckboxParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.parameters.RunnerParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.parameters.SelectOption
import com.jetbrains.teamcity.plugins.unrealengine.common.parameters.SelectParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.parameters.TextInputParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.ugs.UgsMetadataServerUrl
import java.net.URI
import java.time.Duration

object BuildGraphScriptPathParameter : TextInputParameter {
    override val name = "build-graph-script-path"
    override val displayName = "Script path"
    override val defaultValue = ""
    override val description = "The path to the script describing the build graph."
    override val required = true
    override val supportsVcsNavigation = true
    override val expandable = false
    override val advanced = false

    context(_: Raise<PropertyValidationError>)
    fun parseScriptPath(runnerParameters: Map<String, String>): BuildGraphScriptPath {
        val scriptPath = runnerParameters[name]
        if (scriptPath.isNullOrEmpty()) {
            raise(PropertyValidationError(name, "The path to the script is not set."))
        }

        return BuildGraphScriptPath(scriptPath)
    }
}

object BuildGraphTargetNodeParameter : TextInputParameter {
    override val name = "build-graph-target-node"
    override val displayName = "Target"
    override val defaultValue = ""
    override val description = "The name of the node or output tag to be built."
    override val required = true
    override val supportsVcsNavigation = false
    override val expandable = false
    override val advanced = false

    context(_: Raise<PropertyValidationError>)
    fun parseTargetNode(runnerParameters: Map<String, String>): BuildGraphTargetNode {
        val targetNode = runnerParameters[name]
        if (targetNode.isNullOrEmpty()) {
            raise(PropertyValidationError(name, "The target node name is not set."))
        }

        return BuildGraphTargetNode(targetNode)
    }
}

object BuildGraphOptionsParameter : RunnerParameter {
    override val name = "build-graph-options"
    override val displayName = "Options"
    override val defaultValue = ""
    val description =
        """
        The newline-delimited list of custom command-line options in the 'OPTION_NAME=OPTION_VALUE' format that
        should be passed to your BuildGraph script.
        """.trimIndent()

    context(_: Raise<PropertyValidationError>)
    fun parseOptions(runnerParameters: Map<String, String>): List<BuildGraphOption> {
        val optionsString = runnerParameters[name]
        if (optionsString.isNullOrEmpty()) {
            return emptyList()
        }

        return optionsString
            .split("\r\n", "\r", "\n")
            .filter { it.isNotEmpty() }
            .map { optionString ->
                val indexOfEqual = optionString.indexOf('=')
                if (indexOfEqual == -1) {
                    raise(PropertyValidationError(name, "Make sure all options are in the required 'OPTION_NAME=OPTION_VALUE' format."))
                }

                val optionName = optionString.substring(0, indexOfEqual)
                val optionValue = optionString.substring(indexOfEqual + 1)

                if (optionName.isEmpty()) {
                    raise(PropertyValidationError(BuildGraphOptionsParameter.name, "All options must have a name."))
                }

                BuildGraphOption(optionName, optionValue)
            }
    }
}

object BuildGraphModeParameter : SelectParameter() {
    val distributed = SelectOption("Distributed")
    private val singleMachine = SelectOption("SingleMachine")

    override val name = "build-graph-mode"
    override val displayName = "Mode"
    override val description =
        """
        ${singleMachine.name} - Executes all nodes sequentially on a single build agent.
        ${distributed.name} - Distributes the process across multiple agents.
        """.trimIndent()
    override val defaultValue = singleMachine.name
    override val options = listOf(singleMachine, distributed)

    context(_: Raise<PropertyValidationError>)
    fun parse(runnerParameters: Map<String, String>): BuildGraphMode {
        val modeRaw = runnerParameters[name] ?: return BuildGraphMode.SingleMachine

        return when (modeRaw) {
            singleMachine.name -> BuildGraphMode.SingleMachine
            distributed.name -> {
                val postBadges = runnerParameters[PostBadgesFromGraphParameter.name].toBoolean()
                val metadataServerUrl = runnerParameters[UgsMetadataServerUrlParameter.name]
                if (postBadges) {
                    ensure(!metadataServerUrl.isNullOrBlank()) {
                        PropertyValidationError(
                            UgsMetadataServerUrlParameter.name,
                            "Metadata server URL should not be empty",
                        )
                    }
                }
                BuildGraphMode.Distributed(if (postBadges) UgsMetadataServerUrl(metadataServerUrl!!) else null)
            }
            else -> raise(PropertyValidationError(name, "Unknown BuildGraph mode value $modeRaw"))
        }
    }
}

object PostBadgesFromGraphParameter : CheckboxParameter {
    override val description = "Enables posting of badges defined in the build graph"
    override val advanced = false
    override val name = "build-graph-post-badges"
    override val displayName = "Post badges"
    override val defaultValue = false.toString()
}

object UgsMetadataServerUrlParameter : TextInputParameter {
    override val description =
        """
        Specify the metadata server address where badges will be posted.
        Example: http://localhost:1111/ugs-metadata-server
        """.trimIndent()
    override val supportsVcsNavigation = false
    override val expandable = false
    override val required = true
    override val advanced = false
    override val name = "ugs-metadata-server"
    override val displayName = "Metadata server"
    override val defaultValue = ""
}

object BuildGraphRetriesEnabledParameter : CheckboxParameter {
    override val description = "Enables retry controls for the distributed BuildGraph setup step and generated node steps."
    override val advanced = true
    override val name = "build-graph-retries-enabled"
    override val displayName = "Enable retries"
    override val defaultValue = false.toString()
}

object BuildGraphSetupMaxAttemptsParameter : TextInputParameter {
    override val name = "build-graph-setup-max-attempts"
    override val displayName = "Setup max attempts"
    override val defaultValue = "1"
    override val description = "The maximum number of attempts for the distributed BuildGraph setup step."
    override val required = false
    override val supportsVcsNavigation = false
    override val expandable = false
    override val advanced = true
}

object BuildGraphRetryDelaySecondsParameter : TextInputParameter {
    override val name = "build-graph-retry-delay-seconds"
    override val displayName = "Retry delay seconds"
    override val defaultValue = "0"
    override val description = "The delay between retry attempts."
    override val required = false
    override val supportsVcsNavigation = false
    override val expandable = false
    override val advanced = true
}

object BuildGraphNodeRetryRulesParameter : RunnerParameter {
    override val name = "build-graph-node-retry-rules"
    override val displayName = "Node retry rules"
    override val defaultValue = ""
    val description =
        """
        Newline-delimited retry rules in the NODE_PATTERN=MAX_ATTEMPTS format.
        Wildcards are supported, for example Compile*=3 or Cook*=3. The first matching rule wins.
        """.trimIndent()
}

object BuildGraphRetryConditionParameter : SelectParameter() {
    val anyFailure = SelectOption(BuildGraphRetryCondition.AnyFailure.name, "Any failure")
    val matchingLog = SelectOption(BuildGraphRetryCondition.MatchingLog.name, "Only when log matches")

    override val name = "build-graph-retry-condition"
    override val displayName = "Retry condition"
    override val description =
        """
        Controls whether failed attempts are retried for any non-zero exit code or only when the log matches
        one of the retry failure patterns.
        """.trimIndent()
    override val defaultValue = anyFailure.name
    override val options = listOf(anyFailure, matchingLog)
}

object BuildGraphRetryFailurePatternsParameter : RunnerParameter {
    override val name = "build-graph-retry-failure-patterns"
    override val displayName = "Retry failure patterns"
    override val defaultValue = ""
    val description =
        """
        Newline-delimited regular expressions matched against Unreal log messages when retry condition is
        "Only when log matches".
        """.trimIndent()
}

object BuildGraphLogRetrySummariesParameter : CheckboxParameter {
    override val description = "Logs retry decisions and attempt summaries to the TeamCity build log."
    override val advanced = true
    override val name = "build-graph-log-retry-summaries"
    override val displayName = "Log retry summaries"
    override val defaultValue = false.toString()
}

object BuildGraphWarnUnmatchedRetryRulesParameter : CheckboxParameter {
    override val description = "Logs a setup warning when retry rules do not match any exported BuildGraph nodes."
    override val advanced = true
    override val name = "build-graph-warn-unmatched-retry-rules"
    override val displayName = "Warn about unmatched retry rules"
    override val defaultValue = false.toString()
}

object BuildGraphSetupTimeoutParameter : TextInputParameter {
    override val name = "build-graph-setup-timeout"
    override val displayName = "Setup timeout"
    override val defaultValue = "0"
    override val description =
        "Optional timeout for the distributed BuildGraph setup step. Use 0 to disable, or values like 3600, 90m, 45s, 2h, or PT1H."
    override val required = false
    override val supportsVcsNavigation = false
    override val expandable = false
    override val advanced = true
}

object BuildGraphNodeTimeoutRulesParameter : RunnerParameter {
    override val name = "build-graph-node-timeout-rules"
    override val displayName = "Node timeout rules"
    override val defaultValue = ""
    val description =
        """
        Newline-delimited timeout rules in the NODE_PATTERN=TIMEOUT format. Wildcards are supported.
        Use values like Cook*=90m, Compile*=45m, or Exact Node Name=3600. A timeout stops the stuck TeamCity build.
        """.trimIndent()
}

object BuildGraphPublishSetupDiagnosticsParameter : CheckboxParameter {
    override val description = "Publishes setup diagnostics when the distributed BuildGraph setup step fails."
    override val advanced = true
    override val name = "build-graph-publish-setup-diagnostics"
    override val displayName = "Publish setup diagnostics"
    override val defaultValue = false.toString()
}

object BuildGraphTraceGeneratedBuildsParameter : CheckboxParameter {
    override val description = "Adds BuildGraph traceability parameters to generated setup and node builds."
    override val advanced = true
    override val name = "build-graph-trace-generated-builds"
    override val displayName = "Trace generated builds"
    override val defaultValue = false.toString()
}

object BuildGraphBootstrapModeParameter : SelectParameter() {
    val disabled = SelectOption(BuildGraphBootstrapMode.Disabled.name, "Disabled")
    val allBeforeBuildGraph = SelectOption(BuildGraphBootstrapMode.AllBeforeBuildGraph.name, "All steps before BuildGraph")
    val selected = SelectOption(BuildGraphBootstrapMode.Selected.name, "Selected steps")

    override val name = "build-graph-bootstrap-mode"
    override val displayName = "Bootstrap steps"
    override val description =
        """
        Controls whether generated distributed setup and node builds should include bootstrap build steps from this build configuration.
        Use this for agent-local setup such as Perforce login or environment preparation.
        """.trimIndent()
    override val defaultValue = disabled.name
    override val options = listOf(disabled, allBeforeBuildGraph, selected)
}

object BuildGraphBootstrapStepRefsParameter : RunnerParameter {
    override val name = "build-graph-bootstrap-step-refs"
    override val displayName = "Selected bootstrap steps"
    override val defaultValue = ""
    val description =
        """
        Newline-delimited build step IDs or names to copy into generated distributed builds.
        Selected steps must be present before the BuildGraph step.
        """.trimIndent()
}

object BuildGraphBootstrapApplyToParameter : SelectParameter() {
    val setupAndNodes = SelectOption(BuildGraphBootstrapApplyTo.SetupAndNodes.name, "Setup and node builds")
    val setupOnly = SelectOption(BuildGraphBootstrapApplyTo.SetupOnly.name, "Setup build only")
    val nodesOnly = SelectOption(BuildGraphBootstrapApplyTo.NodesOnly.name, "Node builds only")

    override val name = "build-graph-bootstrap-apply-to"
    override val displayName = "Apply bootstrap to"
    override val description = "Controls which generated distributed builds receive copied bootstrap steps."
    override val defaultValue = setupAndNodes.name
    override val options = listOf(setupAndNodes, setupOnly, nodesOnly)
}

object BuildGraphLogSinkParameter : SelectParameter() {
    val teamCityOnly = SelectOption(BuildGraphLogSink.TeamCityOnly.name, "TeamCity only")
    val localFile = SelectOption(BuildGraphLogSink.LocalFile.name, "Local file")
    val graylog = SelectOption(BuildGraphLogSink.Graylog.name, "Graylog")
    val localFileAndGraylog = SelectOption(BuildGraphLogSink.LocalFileAndGraylog.name, "Local file + Graylog")

    override val name = "build-graph-log-sink"
    override val displayName = "Unreal log sink"
    override val description = "Controls optional agent-side Unreal log fan-out."
    override val defaultValue = teamCityOnly.name
    override val options = listOf(teamCityOnly, localFile, graylog, localFileAndGraylog)
}

object BuildGraphLogDirectoryParameter : TextInputParameter {
    override val name = "build-graph-log-directory"
    override val displayName = "Log directory"
    override val defaultValue = ""
    override val description =
        "The directory for local Unreal log files. Relative paths are resolved from the checkout directory."
    override val required = false
    override val supportsVcsNavigation = false
    override val expandable = true
    override val advanced = true
}

object BuildGraphPublishLogArtifactsParameter : CheckboxParameter {
    override val description = "Publishes local Unreal log files as build artifacts."
    override val advanced = true
    override val name = "build-graph-publish-log-artifacts"
    override val displayName = "Publish log artifacts"
    override val defaultValue = false.toString()
}

object BuildGraphGraylogEndpointParameter : TextInputParameter {
    override val name = "build-graph-graylog-endpoint"
    override val displayName = "Graylog endpoint"
    override val defaultValue = ""
    override val description = "The HTTP GELF endpoint used for optional agent-side Graylog forwarding."
    override val required = false
    override val supportsVcsNavigation = false
    override val expandable = true
    override val advanced = true
}

object BuildGraphGraylogSourceParameter : TextInputParameter {
    override val name = "build-graph-graylog-source"
    override val displayName = "Graylog source"
    override val defaultValue = ""
    override val description = "Optional Graylog source value. When empty, the agent host name is used."
    override val required = false
    override val supportsVcsNavigation = false
    override val expandable = true
    override val advanced = true
}

object BuildGraphGraylogTimeoutSecondsParameter : TextInputParameter {
    override val name = "build-graph-graylog-timeout-seconds"
    override val displayName = "Graylog timeout seconds"
    override val defaultValue = "5"
    override val description = "The timeout for each Graylog HTTP request."
    override val required = false
    override val supportsVcsNavigation = false
    override val expandable = false
    override val advanced = true
}

object BuildGraphGraylogExtraFieldsParameter : RunnerParameter {
    override val name = "build-graph-graylog-extra-fields"
    override val displayName = "Graylog extra fields"
    override val defaultValue = ""
    val description =
        """
        Newline-delimited extra Graylog fields in the FIELD_NAME=FIELD_VALUE format.
        Field names are sent with the GELF underscore prefix when it is not already present.
        """.trimIndent()
}

object BuildGraphRetrySettingsParameter {
    context(_: Raise<PropertyValidationError>)
    fun parse(runnerParameters: Map<String, String>): BuildGraphRetrySettings {
        val enabled = runnerParameters[BuildGraphRetriesEnabledParameter.name].toBoolean()
        if (!enabled) {
            return BuildGraphRetrySettings()
        }

        val condition = parseRetryCondition(runnerParameters[BuildGraphRetryConditionParameter.name])
        val failurePatterns = parseRetryFailurePatterns(runnerParameters)
        if (condition == BuildGraphRetryCondition.MatchingLog && failurePatterns.isEmpty()) {
            raise(
                PropertyValidationError(
                    BuildGraphRetryFailurePatternsParameter.name,
                    "At least one retry failure pattern is required when retry condition is \"Only when log matches\".",
                ),
            )
        }

        return BuildGraphRetrySettings(
            enabled = true,
            setupMaxAttempts =
                parsePositiveInt(
                    runnerParameters[BuildGraphSetupMaxAttemptsParameter.name],
                    BuildGraphSetupMaxAttemptsParameter,
                ),
            nodeRules = parseNodeRetryRules(runnerParameters),
            retryDelaySeconds =
                parseNonNegativeLong(
                    runnerParameters[BuildGraphRetryDelaySecondsParameter.name],
                    BuildGraphRetryDelaySecondsParameter,
                ),
            condition = condition,
            failurePatterns = failurePatterns,
            logRetrySummaries = runnerParameters[BuildGraphLogRetrySummariesParameter.name].toBoolean(),
            warnOnUnmatchedRules = runnerParameters[BuildGraphWarnUnmatchedRetryRulesParameter.name].toBoolean(),
        )
    }

    context(_: Raise<PropertyValidationError>)
    private fun parseNodeRetryRules(runnerParameters: Map<String, String>): List<BuildGraphNodeRetryRule> {
        val rulesString = runnerParameters[BuildGraphNodeRetryRulesParameter.name]
        if (rulesString.isNullOrBlank()) {
            return emptyList()
        }

        return rulesString
            .split("\r\n", "\r", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { ruleString ->
                val indexOfEqual = ruleString.indexOf('=')
                if (indexOfEqual == -1) {
                    raise(
                        PropertyValidationError(
                            BuildGraphNodeRetryRulesParameter.name,
                            "Make sure all retry rules are in the required NODE_PATTERN=MAX_ATTEMPTS format.",
                        ),
                    )
                }

                val pattern = ruleString.substring(0, indexOfEqual).trim()
                val maxAttemptsRaw = ruleString.substring(indexOfEqual + 1).trim()

                if (pattern.isEmpty()) {
                    raise(PropertyValidationError(BuildGraphNodeRetryRulesParameter.name, "All retry rules must have a node pattern."))
                }

                BuildGraphNodeRetryRule(
                    pattern,
                    parsePositiveInt(maxAttemptsRaw, BuildGraphNodeRetryRulesParameter),
                )
            }
    }

    context(_: Raise<PropertyValidationError>)
    private fun parsePositiveInt(
        value: String?,
        parameter: RunnerParameter,
    ): Int {
        val parsed = value?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: parameter.defaultValue.toInt()
        if (parsed < 1) {
            raise(PropertyValidationError(parameter.name, "The value must be greater than or equal to 1."))
        }

        return parsed
    }

    context(_: Raise<PropertyValidationError>)
    private fun parseNonNegativeLong(
        value: String?,
        parameter: RunnerParameter,
    ): Long {
        val parsed = value?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: parameter.defaultValue.toLong()
        if (parsed < 0) {
            raise(PropertyValidationError(parameter.name, "The value must be greater than or equal to 0."))
        }

        return parsed
    }

    context(_: Raise<PropertyValidationError>)
    private fun parseRetryCondition(value: String?): BuildGraphRetryCondition =
        when (value ?: BuildGraphRetryConditionParameter.defaultValue) {
            BuildGraphRetryCondition.AnyFailure.name -> BuildGraphRetryCondition.AnyFailure
            BuildGraphRetryCondition.MatchingLog.name -> BuildGraphRetryCondition.MatchingLog
            else -> raise(PropertyValidationError(BuildGraphRetryConditionParameter.name, "Unknown retry condition value $value"))
        }

    context(_: Raise<PropertyValidationError>)
    private fun parseRetryFailurePatterns(runnerParameters: Map<String, String>): List<String> {
        val patternsString = runnerParameters[BuildGraphRetryFailurePatternsParameter.name]
        if (patternsString.isNullOrBlank()) {
            return emptyList()
        }

        return patternsString
            .split("\r\n", "\r", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .onEach { pattern ->
                runCatching { Regex(pattern) }.getOrElse {
                    raise(
                        PropertyValidationError(
                            BuildGraphRetryFailurePatternsParameter.name,
                            "Retry failure pattern \"$pattern\" is not a valid regular expression.",
                        ),
                    )
                }
            }
    }
}

object BuildGraphTimeoutSettingsParameter {
    context(_: Raise<PropertyValidationError>)
    fun parse(runnerParameters: Map<String, String>): BuildGraphTimeoutSettings =
        BuildGraphTimeoutSettings(
            setupTimeoutSeconds =
                parseDurationSeconds(
                    runnerParameters[BuildGraphSetupTimeoutParameter.name],
                    BuildGraphSetupTimeoutParameter,
                ),
            nodeRules = parseNodeTimeoutRules(runnerParameters),
        )

    context(_: Raise<PropertyValidationError>)
    private fun parseNodeTimeoutRules(runnerParameters: Map<String, String>): List<BuildGraphNodeTimeoutRule> {
        val rulesString = runnerParameters[BuildGraphNodeTimeoutRulesParameter.name]
        if (rulesString.isNullOrBlank()) {
            return emptyList()
        }

        return rulesString
            .split("\r\n", "\r", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { ruleString ->
                val indexOfEqual = ruleString.indexOf('=')
                if (indexOfEqual == -1) {
                    raise(
                        PropertyValidationError(
                            BuildGraphNodeTimeoutRulesParameter.name,
                            "Make sure all timeout rules are in the required NODE_PATTERN=TIMEOUT format.",
                        ),
                    )
                }

                val pattern = ruleString.substring(0, indexOfEqual).trim()
                val timeoutRaw = ruleString.substring(indexOfEqual + 1).trim()

                if (pattern.isEmpty()) {
                    raise(PropertyValidationError(BuildGraphNodeTimeoutRulesParameter.name, "All timeout rules must have a node pattern."))
                }

                BuildGraphNodeTimeoutRule(pattern, parseDurationSeconds(timeoutRaw, BuildGraphNodeTimeoutRulesParameter))
            }
    }

    context(_: Raise<PropertyValidationError>)
    private fun parseDurationSeconds(
        value: String?,
        parameter: RunnerParameter,
    ): Long {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: parameter.defaultValue
        val seconds =
            when {
                raw.equals("0", ignoreCase = true) -> 0
                raw.all { it.isDigit() } -> raw.toLongOrNull()
                raw.startsWith("P", ignoreCase = true) ->
                    runCatching {
                        Duration.parse(raw).seconds
                    }.getOrNull()
                raw.endsWith("s", ignoreCase = true) -> raw.dropLast(1).toLongOrNull()
                raw.endsWith("m", ignoreCase = true) -> raw.dropLast(1).toLongOrNull()?.times(60)
                raw.endsWith("h", ignoreCase = true) -> raw.dropLast(1).toLongOrNull()?.times(60 * 60)
                else ->
                    runCatching {
                        Duration.parse(raw).seconds
                    }.getOrNull()
            }

        if (seconds == null || seconds < 0) {
            raise(
                PropertyValidationError(
                    parameter.name,
                    "Timeout values must be 0 or a positive duration such as 3600, 90m, 45s, 2h, or PT1H.",
                ),
            )
        }

        return seconds
    }
}

object BuildGraphDiagnosticsSettingsParameter {
    fun parse(runnerParameters: Map<String, String>) =
        BuildGraphDiagnosticsSettings(
            publishSetupDiagnostics = runnerParameters[BuildGraphPublishSetupDiagnosticsParameter.name].toBoolean(),
            traceGeneratedBuilds = runnerParameters[BuildGraphTraceGeneratedBuildsParameter.name].toBoolean(),
        )
}

object BuildGraphBootstrapSettingsParameter {
    context(_: Raise<PropertyValidationError>)
    fun parse(runnerParameters: Map<String, String>): BuildGraphBootstrapSettings {
        val mode = parseMode(runnerParameters[BuildGraphBootstrapModeParameter.name])
        if (mode == BuildGraphBootstrapMode.Disabled) {
            return BuildGraphBootstrapSettings()
        }

        val stepRefs = parseStepRefs(runnerParameters)
        if (mode == BuildGraphBootstrapMode.Selected && stepRefs.isEmpty()) {
            raise(
                PropertyValidationError(
                    BuildGraphBootstrapStepRefsParameter.name,
                    "At least one bootstrap step ID or name is required when selected bootstrap steps are enabled.",
                ),
            )
        }

        return BuildGraphBootstrapSettings(
            mode = mode,
            stepRefs = stepRefs,
            applyTo = parseApplyTo(runnerParameters[BuildGraphBootstrapApplyToParameter.name]),
        )
    }

    context(_: Raise<PropertyValidationError>)
    private fun parseMode(value: String?): BuildGraphBootstrapMode =
        when (value ?: BuildGraphBootstrapModeParameter.defaultValue) {
            BuildGraphBootstrapMode.Disabled.name -> BuildGraphBootstrapMode.Disabled
            BuildGraphBootstrapMode.AllBeforeBuildGraph.name -> BuildGraphBootstrapMode.AllBeforeBuildGraph
            BuildGraphBootstrapMode.Selected.name -> BuildGraphBootstrapMode.Selected
            else -> raise(PropertyValidationError(BuildGraphBootstrapModeParameter.name, "Unknown bootstrap step mode value $value"))
        }

    private fun parseStepRefs(runnerParameters: Map<String, String>): List<String> =
        runnerParameters[BuildGraphBootstrapStepRefsParameter.name]
            ?.split("\r\n", "\r", "\n")
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    context(_: Raise<PropertyValidationError>)
    private fun parseApplyTo(value: String?): BuildGraphBootstrapApplyTo =
        when (value ?: BuildGraphBootstrapApplyToParameter.defaultValue) {
            BuildGraphBootstrapApplyTo.SetupAndNodes.name -> BuildGraphBootstrapApplyTo.SetupAndNodes
            BuildGraphBootstrapApplyTo.SetupOnly.name -> BuildGraphBootstrapApplyTo.SetupOnly
            BuildGraphBootstrapApplyTo.NodesOnly.name -> BuildGraphBootstrapApplyTo.NodesOnly
            else -> raise(PropertyValidationError(BuildGraphBootstrapApplyToParameter.name, "Unknown bootstrap apply-to value $value"))
        }
}

object BuildGraphLoggingSettingsParameter {
    context(_: Raise<PropertyValidationError>)
    fun parse(runnerParameters: Map<String, String>): BuildGraphLoggingSettings {
        val sink = parseSink(runnerParameters[BuildGraphLogSinkParameter.name])
        val graylogEndpoint = runnerParameters[BuildGraphGraylogEndpointParameter.name]?.trim()?.takeIf { it.isNotEmpty() }

        if ((sink == BuildGraphLogSink.Graylog || sink == BuildGraphLogSink.LocalFileAndGraylog) && graylogEndpoint == null) {
            raise(PropertyValidationError(BuildGraphGraylogEndpointParameter.name, "Graylog endpoint should not be empty."))
        }

        graylogEndpoint?.let { validateHttpEndpoint(it) }

        return BuildGraphLoggingSettings(
            sink = sink,
            localDirectory = runnerParameters[BuildGraphLogDirectoryParameter.name]?.trim()?.takeIf { it.isNotEmpty() },
            publishLocalLogArtifacts = runnerParameters[BuildGraphPublishLogArtifactsParameter.name].toBoolean(),
            graylogEndpoint = graylogEndpoint,
            graylogSource = runnerParameters[BuildGraphGraylogSourceParameter.name]?.trim()?.takeIf { it.isNotEmpty() },
            graylogTimeoutSeconds = parseTimeout(runnerParameters[BuildGraphGraylogTimeoutSecondsParameter.name]),
            graylogExtraFields = parseExtraFields(runnerParameters),
        )
    }

    context(_: Raise<PropertyValidationError>)
    private fun parseSink(value: String?): BuildGraphLogSink =
        when (value ?: BuildGraphLogSinkParameter.defaultValue) {
            BuildGraphLogSink.TeamCityOnly.name -> BuildGraphLogSink.TeamCityOnly
            BuildGraphLogSink.LocalFile.name -> BuildGraphLogSink.LocalFile
            BuildGraphLogSink.Graylog.name -> BuildGraphLogSink.Graylog
            BuildGraphLogSink.LocalFileAndGraylog.name -> BuildGraphLogSink.LocalFileAndGraylog
            else -> raise(PropertyValidationError(BuildGraphLogSinkParameter.name, "Unknown Unreal log sink value $value"))
        }

    context(_: Raise<PropertyValidationError>)
    private fun validateHttpEndpoint(value: String) {
        val uri =
            runCatching {
                URI(value)
            }.getOrNull()

        if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            raise(PropertyValidationError(BuildGraphGraylogEndpointParameter.name, "Graylog endpoint must be an HTTP or HTTPS URL."))
        }
    }

    context(_: Raise<PropertyValidationError>)
    private fun parseTimeout(value: String?): Long {
        val parsed =
            value
                ?.takeIf { it.isNotBlank() }
                ?.toLongOrNull()
                ?: BuildGraphGraylogTimeoutSecondsParameter.defaultValue.toLong()
        if (parsed < 1) {
            raise(PropertyValidationError(BuildGraphGraylogTimeoutSecondsParameter.name, "The value must be greater than or equal to 1."))
        }

        return parsed
    }

    context(_: Raise<PropertyValidationError>)
    private fun parseExtraFields(runnerParameters: Map<String, String>): Map<String, String> {
        val fieldsString = runnerParameters[BuildGraphGraylogExtraFieldsParameter.name]
        if (fieldsString.isNullOrBlank()) {
            return emptyMap()
        }

        return fieldsString
            .split("\r\n", "\r", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associate { fieldString ->
                val indexOfEqual = fieldString.indexOf('=')
                if (indexOfEqual == -1) {
                    raise(
                        PropertyValidationError(
                            BuildGraphGraylogExtraFieldsParameter.name,
                            "Make sure all Graylog extra fields are in the FIELD_NAME=FIELD_VALUE format.",
                        ),
                    )
                }

                val fieldName = fieldString.substring(0, indexOfEqual).trim()
                val fieldValue = fieldString.substring(indexOfEqual + 1).trim()

                if (fieldName.isEmpty()) {
                    raise(PropertyValidationError(BuildGraphGraylogExtraFieldsParameter.name, "All Graylog extra fields must have a name."))
                }

                fieldName to fieldValue
            }
    }
}
