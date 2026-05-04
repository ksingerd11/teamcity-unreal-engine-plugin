package com.jetbrains.teamcity.plugins.unrealengine.server.runner.ui

import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapApplyToParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapModeParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphBootstrapStepRefsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogEndpointParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogExtraFieldsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogSourceParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogTimeoutSecondsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLogDirectoryParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLogRetrySummariesParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLogSinkParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphModeParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphNodeRetryRulesParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphNodeTimeoutRulesParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphOptionsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphPublishLogArtifactsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphPublishSetupDiagnosticsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetriesEnabledParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetryConditionParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetryDelaySecondsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetryFailurePatternsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphScriptPathParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphSetupMaxAttemptsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphSetupTimeoutParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphTargetNodeParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphTraceGeneratedBuildsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphWarnUnmatchedRetryRulesParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.PostBadgesFromGraphParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.UgsMetadataServerUrlParameter

class BuildGraphComponent {
    val script = BuildGraphScriptPathParameter
    val target = BuildGraphTargetNodeParameter
    val options = BuildGraphOptionsParameter
    val mode = BuildGraphModeParameter
    val postBadges = PostBadgesFromGraphParameter
    val ugsMetadataServer = UgsMetadataServerUrlParameter
    val retriesEnabled = BuildGraphRetriesEnabledParameter
    val setupMaxAttempts = BuildGraphSetupMaxAttemptsParameter
    val retryDelaySeconds = BuildGraphRetryDelaySecondsParameter
    val nodeRetryRules = BuildGraphNodeRetryRulesParameter
    val retryCondition = BuildGraphRetryConditionParameter
    val retryFailurePatterns = BuildGraphRetryFailurePatternsParameter
    val logRetrySummaries = BuildGraphLogRetrySummariesParameter
    val warnUnmatchedRetryRules = BuildGraphWarnUnmatchedRetryRulesParameter
    val setupTimeout = BuildGraphSetupTimeoutParameter
    val nodeTimeoutRules = BuildGraphNodeTimeoutRulesParameter
    val publishSetupDiagnostics = BuildGraphPublishSetupDiagnosticsParameter
    val traceGeneratedBuilds = BuildGraphTraceGeneratedBuildsParameter
    val bootstrapMode = BuildGraphBootstrapModeParameter
    val bootstrapStepRefs = BuildGraphBootstrapStepRefsParameter
    val bootstrapApplyTo = BuildGraphBootstrapApplyToParameter
    val logSink = BuildGraphLogSinkParameter
    val logDirectory = BuildGraphLogDirectoryParameter
    val publishLogArtifacts = BuildGraphPublishLogArtifactsParameter
    val graylogEndpoint = BuildGraphGraylogEndpointParameter
    val graylogSource = BuildGraphGraylogSourceParameter
    val graylogTimeoutSeconds = BuildGraphGraylogTimeoutSecondsParameter
    val graylogExtraFields = BuildGraphGraylogExtraFieldsParameter
}
