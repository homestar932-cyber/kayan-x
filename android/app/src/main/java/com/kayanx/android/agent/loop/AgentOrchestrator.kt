package com.kayanx.android.agent.loop

import com.kayanx.android.agent.executor.ToolExecutor
import com.kayanx.android.agent.planner.Planner
import com.kayanx.android.agent.planner.PlannerDecision
import com.kayanx.android.agent.state.*
import com.kayanx.android.agent.verifier.DeterministicVerifier
import com.kayanx.android.fs.model.FileResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real Agent Loop (requirement 13 + 14):
 *
 * User → Planner → Tool Selection → Permission/Policy → Execution
 *       → Observation → Verification → Re-plan → Final Answer
 *
 * No static plan. Next step is decided only after observing the previous result.
 */
class AgentOrchestrator(
    private val planner: Planner,
    private val executor: ToolExecutor,
    private val verifier: DeterministicVerifier
) {
    private val _state = MutableStateFlow<AgentState?>(null)
    val state: StateFlow<AgentState?> = _state.asStateFlow()

    private val _events = MutableStateFlow<AgentEvent>(AgentEvent.Idle)
    val events: StateFlow<AgentEvent> = _events.asStateFlow()

    suspend fun start(goal: String) {
        val initial = AgentState(goal = goal)
        _state.value = initial
        _events.value = AgentEvent.Started(goal)
        runLoop(initial)
    }

    suspend fun confirmPending(confirmed: Boolean) {
        val current = _state.value ?: return
        val pending = current.pendingConfirmation ?: return
        if (!confirmed) {
            val record = StepRecord(
                thought = "User rejected confirmation",
                toolName = pending.originalToolCall.name,
                toolArgs = pending.originalToolCall.args,
                observation = "USER_REJECTED",
                verification = VerificationResult.Failed("user rejected")
            )
            val next = current.withStep(record).withConfirmation(null)
            _state.value = next
            runLoop(next)
            return
        }
        // Re-execute with confirmed=true
        _events.value = AgentEvent.Executing(pending.originalToolCall.name)
        val result = executor.execute(pending.originalToolCall, confirmed = true)
        val observation = result.toObservation()
        val verification = verifier.verify(pending.originalToolCall, result)
        val record = StepRecord(
            thought = "Confirmed by user",
            toolName = pending.originalToolCall.name,
            toolArgs = pending.originalToolCall.args,
            observation = observation,
            verification = verification
        )
        val next = current.withStep(record).withConfirmation(null)
        _state.value = next
        _events.value = AgentEvent.Verified(verification)
        runLoop(next)
    }

    private suspend fun runLoop(startState: AgentState) {
        var current = startState
        while (!current.isComplete && current.iteration < current.maxIterations) {
            _events.value = AgentEvent.Planning
            val decision = planner.nextAction(current)

            when (decision) {
                is PlannerDecision.FinalAnswer -> {
                    current = current.completed(decision.answer)
                    _state.value = current
                    _events.value = AgentEvent.Finished(decision.answer)
                    return
                }
                is PlannerDecision.RequestUserInput -> {
                    _events.value = AgentEvent.NeedsUserInput(decision.question)
                    return
                }
                is PlannerDecision.CallTool -> {
                    _events.value = AgentEvent.Executing(decision.call.name)
                    val result = executor.execute(decision.call, confirmed = false)

                    if (result is FileResult.NeedsConfirmation) {
                        val pending = PendingConfirmation(
                            operation = result.operation.name,
                            targetId = result.target,
                            details = result.details,
                            originalToolCall = decision.call
                        )
                        current = current.withConfirmation(pending)
                        _state.value = current
                        _events.value = AgentEvent.NeedsConfirmation(pending)
                        return
                    }

                    val observation = result.toObservation()
                    val verification = verifier.verify(decision.call, result)
                    val record = StepRecord(
                        thought = decision.thought,
                        toolName = decision.call.name,
                        toolArgs = decision.call.args,
                        observation = observation,
                        verification = verification
                    )
                    current = current.withStep(record)
                    _state.value = current
                    _events.value = AgentEvent.Verified(verification)

                    // If verification failed hard, we still re-plan; the planner sees the failure.
                }
            }
        }
        if (!current.isComplete) {
            current = current.completed("Reached max iterations without completing the goal.")
            _state.value = current
            _events.value = AgentEvent.Finished(current.finalAnswer ?: "")
        }
    }

    private fun FileResult.toObservation(): String = when (this) {
        is FileResult.Success -> "SUCCESS: ${message}${content?.let { "\n$content" } ?: ""}"
        is FileResult.Failure -> "FAILURE [${code}]: $message"
        is FileResult.NeedsConfirmation -> "NEEDS_CONFIRMATION: $details"
    }
}

sealed class AgentEvent {
    object Idle : AgentEvent()
    data class Started(val goal: String) : AgentEvent()
    object Planning : AgentEvent()
    data class Executing(val tool: String) : AgentEvent()
    data class Verified(val result: VerificationResult) : AgentEvent()
    data class NeedsConfirmation(val pending: PendingConfirmation) : AgentEvent()
    data class NeedsUserInput(val question: String) : AgentEvent()
    data class Finished(val answer: String) : AgentEvent()
}
