package com.jetbrains.teamcity.plugins.unrealengine.server.runner

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.jetbrains.teamcity.plugins.unrealengine.common.JsonEncoder
import com.jetbrains.teamcity.plugins.unrealengine.common.PropertyValidationError
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogEndpointParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogExtraFieldsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogSourceParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogTimeoutSecondsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLogSink
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLogSinkParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLoggingSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLoggingSettingsParameter
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.web.openapi.WebControllerManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.web.servlet.ModelAndView
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class BuildGraphGraylogTestController(
    server: SBuildServer,
    webControllerManager: WebControllerManager,
) : BaseController(server) {
    companion object {
        private const val PATH = "/unrealEngine/buildGraph/testGraylog.html"
        private val json = JsonEncoder.instance
        private val httpClient = HttpClient.newHttpClient()
    }

    init {
        webControllerManager.registerController(PATH, this)
    }

    override fun doHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ModelAndView? {
        val settings =
            either { parseLoggingSettings(request) }.getOrElse {
                response.writeJson(false, it.message)
                return null
            }

        val result =
            runCatching {
                sendTestMessage(settings)
            }.getOrElse {
                response.writeJson(false, "Unable to send Graylog test message: ${it.message ?: it.javaClass.simpleName}")
                return null
            }

        response.writeJson(result.success, result.message)
        return null
    }

    context(_: Raise<PropertyValidationError>)
    private fun parseLoggingSettings(request: HttpServletRequest): BuildGraphLoggingSettings =
        BuildGraphLoggingSettingsParameter.parse(
            mapOf(
                BuildGraphLogSinkParameter.name to BuildGraphLogSink.Graylog.name,
                BuildGraphGraylogEndpointParameter.name to request.getParameter("endpoint").orEmpty(),
                BuildGraphGraylogSourceParameter.name to request.getParameter("source").orEmpty(),
                BuildGraphGraylogTimeoutSecondsParameter.name to request.getParameter("timeoutSeconds").orEmpty(),
                BuildGraphGraylogExtraFieldsParameter.name to request.getParameter("extraFields").orEmpty(),
            ),
        )

    private fun sendTestMessage(settings: BuildGraphLoggingSettings): GraylogTestResult {
        val endpoint = settings.graylogEndpoint ?: return GraylogTestResult(false, "Graylog endpoint should not be empty.")
        val payload = json.encodeToString(createPayload(settings))
        val request =
            HttpRequest
                .newBuilder(URI(endpoint))
                .timeout(Duration.ofSeconds(settings.graylogTimeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build()
        val graylogResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))

        if (graylogResponse.statusCode() in 200..299) {
            return GraylogTestResult(true, "Graylog test message sent.")
        }

        val body = graylogResponse.body().orEmpty().take(300)
        val detail = if (body.isBlank()) "" else ": $body"
        return GraylogTestResult(false, "Graylog responded with HTTP ${graylogResponse.statusCode()}$detail")
    }

    private fun createPayload(settings: BuildGraphLoggingSettings): JsonObject =
        buildJsonObject {
            put("version", "1.1")
            put("host", settings.graylogSource ?: hostName())
            put("short_message", "TeamCity Unreal Engine plugin Graylog test")
            put("full_message", "TeamCity Unreal Engine plugin Graylog test from BuildGraph runner settings.")
            put("timestamp", JsonPrimitive(Instant.now().epochSecond))
            put("level", 6)
            put("_teamcity_plugin", "unreal-engine")
            put("_unreal_execution", "graylog-test")
            addExtraFields(settings.graylogExtraFields)
        }

    private fun JsonObjectBuilder.addExtraFields(fields: Map<String, String>) {
        fields.forEach { (key, value) ->
            put(if (key.startsWith("_")) key else "_$key", value)
        }
    }

    private fun hostName() = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("teamcity-server")

    private fun HttpServletResponse.writeJson(
        success: Boolean,
        message: String,
    ) {
        status = if (success) HttpServletResponse.SC_OK else HttpServletResponse.SC_BAD_REQUEST
        contentType = "application/json"
        characterEncoding = Charsets.UTF_8.name()
        writer.write(
            json.encodeToString(
                buildJsonObject {
                    put("success", success)
                    put("message", message)
                },
            ),
        )
    }

    private data class GraylogTestResult(
        val success: Boolean,
        val message: String,
    )
}
