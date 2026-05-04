package com.jetbrains.teamcity.plugins.unrealengine.agent.build.log

import com.jetbrains.teamcity.plugins.unrealengine.agent.UnrealBuildContext
import com.jetbrains.teamcity.plugins.unrealengine.common.JsonEncoder
import com.jetbrains.teamcity.plugins.unrealengine.common.UnrealPluginLoggers
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLoggingSettings
import jetbrains.buildServer.agent.impl.artifacts.ArtifactsWatcherEx
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedWriter
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

interface UnrealLogSink : AutoCloseable {
    fun write(event: UnrealLogEvent)

    override fun close() = Unit
}

class UnrealLogSinkFactory(
    private val artifactsWatcher: ArtifactsWatcherEx,
) {
    companion object {
        private val logger = UnrealPluginLoggers.get<UnrealLogSinkFactory>()
    }

    context(context: UnrealBuildContext)
    fun create(
        settings: BuildGraphLoggingSettings,
        executionName: String?,
        attempt: Int,
        maxAttempts: Int,
    ): UnrealLogSink {
        val sinks =
            buildList {
                if (settings.localFileEnabled) {
                    add(createLocalFileSink(settings, executionName, attempt))
                }
                if (settings.graylogEnabled && !settings.graylogEndpoint.isNullOrBlank()) {
                    add(GraylogUnrealLogSink(settings, executionName, attempt, maxAttempts, context))
                }
            }

        return when (sinks.size) {
            0 -> NoOpUnrealLogSink
            1 -> sinks.single()
            else -> CompositeUnrealLogSink(sinks)
        }
    }

    context(context: UnrealBuildContext)
    private fun createLocalFileSink(
        settings: BuildGraphLoggingSettings,
        executionName: String?,
        attempt: Int,
    ): UnrealLogSink {
        val directory =
            settings.localDirectory
                ?.let { context.resolveUserPath(it) }
                ?: context.resolvePath(context.agentTempDirectory, "unreal-engine-logs")

        context.createDirectory(directory)

        val fileName =
            listOfNotNull(
                context.runnerId,
                executionName,
                "attempt-$attempt",
            ).joinToString(separator = "-") { it.sanitizeForFileName() } + ".log"

        return LocalFileUnrealLogSink(
            Path.of(directory, fileName),
            settings.publishLocalLogArtifacts,
            artifactsWatcher,
        )
    }

    private fun String.sanitizeForFileName() = replace("[^A-Za-z0-9_.-]".toRegex(), "_").ifBlank { "unreal" }
}

private object NoOpUnrealLogSink : UnrealLogSink {
    override fun write(event: UnrealLogEvent) = Unit
}

private class CompositeUnrealLogSink(
    private val sinks: Collection<UnrealLogSink>,
) : UnrealLogSink {
    override fun write(event: UnrealLogEvent) = sinks.forEach { it.write(event) }

    override fun close() = sinks.forEach { it.close() }
}

private class LocalFileUnrealLogSink(
    private val file: Path,
    private val publishArtifact: Boolean,
    private val artifactsWatcher: ArtifactsWatcherEx,
) : UnrealLogSink {
    companion object {
        private val logger = UnrealPluginLoggers.get<LocalFileUnrealLogSink>()
    }

    private val writer: BufferedWriter = Files.newBufferedWriter(file, StandardCharsets.UTF_8)

    override fun write(event: UnrealLogEvent) {
        runCatching {
            writer.appendLine("${event.time}\t${event.level}\t${event.channel ?: ""}\t${event.message}")
            writer.flush()
        }.onFailure {
            logger.warn("Unable to write Unreal log event to local file $file", it)
        }
    }

    override fun close() {
        runCatching {
            writer.close()
            if (publishArtifact) {
                artifactsWatcher.addNewArtifactsPath("${file.toAbsolutePath()} => unreal-logs")
            }
        }.onFailure {
            logger.warn("Unable to close Unreal local log sink $file", it)
        }
    }
}

private class GraylogUnrealLogSink(
    private val settings: BuildGraphLoggingSettings,
    private val executionName: String?,
    private val attempt: Int,
    private val maxAttempts: Int,
    private val context: UnrealBuildContext,
) : UnrealLogSink {
    companion object {
        private val logger = UnrealPluginLoggers.get<GraylogUnrealLogSink>()
        private val json = JsonEncoder.instance
        private val httpClient = HttpClient.newHttpClient()
    }

    override fun write(event: UnrealLogEvent) {
        val endpoint = settings.graylogEndpoint ?: return
        val payload = json.encodeToString(createPayload(event))
        val request =
            HttpRequest
                .newBuilder(URI(endpoint))
                .timeout(Duration.ofSeconds(settings.graylogTimeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build()

        runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding())
        }.onFailure {
            logger.debug("Unable to send Unreal log event to Graylog endpoint $endpoint", it)
        }
    }

    private fun createPayload(event: UnrealLogEvent): JsonObject =
        buildJsonObject {
            put("version", "1.1")
            put("host", settings.graylogSource ?: getHostName())
            put("short_message", event.message.take(250))
            put("full_message", event.message)
            put("timestamp", JsonPrimitive(event.time.epochSecond + event.time.nano / 1_000_000_000.0))
            put("level", event.level.toSyslogLevel())
            put("_teamcity_runner_id", context.runnerId)
            put("_teamcity_runner_name", context.runnerName)
            executionName?.let { put("_unreal_execution", it) }
            event.channel?.let { put("_unreal_channel", it) }
            put("_unreal_log_level", event.level.name)
            put("_unreal_attempt", attempt)
            put("_unreal_max_attempts", maxAttempts)
            addExtraFields(settings.graylogExtraFields)
        }

    private fun JsonObjectBuilder.addExtraFields(fields: Map<String, String>) {
        fields.forEach { (key, value) ->
            put(if (key.startsWith("_")) key else "_$key", value)
        }
    }

    private fun LogLevel.toSyslogLevel() =
        when (this) {
            LogLevel.Critical -> 2
            LogLevel.Error -> 3
            LogLevel.Warning -> 4
            LogLevel.Information -> 6
            LogLevel.Trace, LogLevel.Debug, LogLevel.None -> 7
        }

    private fun getHostName() =
        runCatching {
            InetAddress.getLocalHost().hostName
        }.getOrDefault("teamcity-agent")
}
