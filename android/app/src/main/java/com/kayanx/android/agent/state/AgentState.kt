package com.kayanx.android.agent.state

import com.kayanx.android.fs.model.DocumentId
import com.kayanx.android.fs.model.FileResult

/**
 * Immutable snapshot of the agent at any point in the loop.
 * The LLM never mutates this; the Orchestrator does.
 */
data class AgentState(
    val goal: String,
    val history: List<StepRecord> = emptyList(),
    val pendingConfirmation: PendingConfirmation? = null,
    val isComplete: Boolean = false,
    val finalAnswer: String? = null,
    val iteration: Int = 0,
    val maxIterations: Int = 24
) {
    fun withStep(record: StepRecord) = copy(
        history = history + record,
        iteration = iteration + 1
    )

    fun withConfirmation(pending: PendingConfirmation?) = copy(pendingConfirmation = pending)

    fun completed(answer: String) = copy(isComplete = true, finalAnswer = answer)
}

data class StepRecord(
    val thought: String,
    val toolName: String?,
    val toolArgs: Map<String, String>,
    val observation: String,
    val verification: VerificationResult?,
    val timestampMs: Long = System.currentTimeMillis()
)

data class PendingConfirmation(
    val operation: String,
    val targetId: DocumentId,
    val details: String,
    val originalToolCall: ToolCall
)

data class ToolCall(
    val name: String,
    val args: Map<String, String>
)

sealed class VerificationResult {
    data class Passed(val evidence: String) : VerificationResult()
    data class Failed(val reason: String) : VerificationResult()
    object NotApplicable : VerificationResult()
}
