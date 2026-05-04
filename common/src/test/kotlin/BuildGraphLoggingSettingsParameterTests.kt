import arrow.core.raise.either
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogEndpointParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogExtraFieldsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogSourceParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphGraylogTimeoutSecondsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLogDirectoryParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLogSink
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLogSinkParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLoggingSettings
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphLoggingSettingsParameter
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphPublishLogArtifactsParameter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class BuildGraphLoggingSettingsParameterTests {
    @Test
    fun `returns TeamCity only logging settings by default`() {
        val settings = either { BuildGraphLoggingSettingsParameter.parse(emptyMap()) }.getOrNull()

        settings shouldBe BuildGraphLoggingSettings()
    }

    @Test
    fun `requires endpoint for Graylog sink`() {
        val error =
            either {
                BuildGraphLoggingSettingsParameter.parse(
                    mapOf(BuildGraphLogSinkParameter.name to BuildGraphLogSink.Graylog.name),
                )
            }.leftOrNull()

        error?.propertyName shouldBe BuildGraphGraylogEndpointParameter.name
    }

    @Test
    fun `parses local file and Graylog settings`() {
        val settings =
            either {
                BuildGraphLoggingSettingsParameter.parse(
                    mapOf(
                        BuildGraphLogSinkParameter.name to BuildGraphLogSink.LocalFileAndGraylog.name,
                        BuildGraphLogDirectoryParameter.name to "Saved/TeamCityLogs",
                        BuildGraphPublishLogArtifactsParameter.name to true.toString(),
                        BuildGraphGraylogEndpointParameter.name to "http://graylog.local/gelf",
                        BuildGraphGraylogSourceParameter.name to "build-agent",
                        BuildGraphGraylogTimeoutSecondsParameter.name to "10",
                        BuildGraphGraylogExtraFieldsParameter.name to "project=Fetch\n_stream=main",
                    ),
                )
            }.getOrNull()

        settings shouldNotBe null
        settings!!.sink shouldBe BuildGraphLogSink.LocalFileAndGraylog
        settings.localDirectory shouldBe "Saved/TeamCityLogs"
        settings.publishLocalLogArtifacts shouldBe true
        settings.graylogEndpoint shouldBe "http://graylog.local/gelf"
        settings.graylogSource shouldBe "build-agent"
        settings.graylogTimeoutSeconds shouldBe 10
        settings.graylogExtraFields shouldBe mapOf("project" to "Fetch", "_stream" to "main")
    }
}
