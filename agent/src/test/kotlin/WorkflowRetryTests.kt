import com.jetbrains.teamcity.plugins.unrealengine.agent.CompletedCommand
import com.jetbrains.teamcity.plugins.unrealengine.agent.RetrySignal
import com.jetbrains.teamcity.plugins.unrealengine.agent.UnrealEngineCommandExecution
import com.jetbrains.teamcity.plugins.unrealengine.agent.Workflow
import com.jetbrains.teamcity.plugins.unrealengine.common.buildgraph.BuildGraphRetryCondition
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import jetbrains.buildServer.agent.runner.ProcessListener
import jetbrains.buildServer.agent.runner.ProgramCommandLine
import kotlin.test.Test

class WorkflowRetryTests {
    @Test
    fun `skips remaining retry attempts after success`() {
        val firstAttempt = command(attempt = 1)
        val secondAttempt = command(attempt = 2)
        val thirdAttempt = command(attempt = 3)
        val workflow = Workflow(listOf(firstAttempt, secondAttempt, thirdAttempt))

        workflow.next(emptyList()) shouldBe firstAttempt
        workflow.next(listOf(CompletedCommand(firstAttempt, 1))) shouldBe secondAttempt
        workflow.next(
            listOf(
                CompletedCommand(firstAttempt, 1),
                CompletedCommand(secondAttempt, 0),
            ),
        ) shouldBe null
    }

    @Test
    fun `skips remaining retry attempts when matching log retry condition is not met`() {
        val firstAttempt = command(attempt = 1, retryCondition = BuildGraphRetryCondition.MatchingLog)
        val secondAttempt = command(attempt = 2, retryCondition = BuildGraphRetryCondition.MatchingLog)
        val workflow = Workflow(listOf(firstAttempt, secondAttempt))

        workflow.next(emptyList()) shouldBe firstAttempt
        workflow.next(listOf(CompletedCommand(firstAttempt, 1))) shouldBe null
    }

    @Test
    fun `continues retry attempts when matching log retry condition is met`() {
        val retrySignal = RetrySignal().also { it.markMatched() }
        val firstAttempt =
            command(
                attempt = 1,
                retryCondition = BuildGraphRetryCondition.MatchingLog,
                retrySignal = retrySignal,
            )
        val secondAttempt = command(attempt = 2, retryCondition = BuildGraphRetryCondition.MatchingLog)
        val workflow = Workflow(listOf(firstAttempt, secondAttempt))

        workflow.next(emptyList()) shouldBe firstAttempt
        workflow.next(listOf(CompletedCommand(firstAttempt, 1))) shouldBe secondAttempt
    }

    private fun command(
        attempt: Int,
        retryCondition: BuildGraphRetryCondition = BuildGraphRetryCondition.AnyFailure,
        retrySignal: RetrySignal = RetrySignal(),
    ) = UnrealEngineCommandExecution(
        mockk<ProgramCommandLine>(relaxed = true),
        mockk<ProcessListener>(relaxed = true),
        retryGroupId = "Compile",
        attempt = attempt,
        maxAttempts = 3,
        retryCondition = retryCondition,
        retrySignal = retrySignal,
    )
}
