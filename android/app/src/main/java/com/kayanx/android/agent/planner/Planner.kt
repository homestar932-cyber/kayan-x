package com.kayanx.android.agent.planner

import com.kayanx.android.agent.state.AgentState
import com.kayanx.android.agent.state.ToolCall

/**
 * Planner decides the *next single action* only.
 * Never produces a fixed multi-step plan.
 * Concrete implementations: LocalLlamaPlanner (native), MockPlanner (tests).
 */
interface Planner {
    /**
     * @return either a ToolCall or a final answer string when the goal is complete.
     */
    suspend fun nextAction(state: AgentState): PlannerDecision
}

sealed class PlannerDecision {
    data class CallTool(val thought: String, val call: ToolCall) : PlannerDecision()
    data class FinalAnswer(val thought: String, val answer: String) : PlannerDecision()
    data class RequestUserInput(val thought: String, val question: String) : PlannerDecision()
}
