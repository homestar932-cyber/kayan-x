package com.kayanx.android.agent.planner

import com.kayanx.android.agent.state.AgentState
import com.kayanx.android.agent.state.ToolCall
import com.kayanx.android.fs.model.LogicalRoot

/**
 * Deterministic planner used for:
 * - Unit tests
 * - First end-to-end validation of the Agent Loop without a loaded model
 *
 * It understands a small set of scripted goals so we can prove the loop,
 * policy, confirmation and verification work before wiring the real LLM.
 */
class MockPlanner : Planner {

    override suspend fun nextAction(state: AgentState): PlannerDecision {
        val goal = state.goal.lowercase()
        val step = state.iteration

        // Example multi-step goal: "أنشئ مجلد KayanTest في Downloads واكتب فيه hello.txt"
        if (goal.contains("kayan") || goal.contains("test") || goal.contains("مجلد")) {
            return when (step) {
                0 -> PlannerDecision.CallTool(
                    thought = "First I need the root of Downloads",
                    call = ToolCall("list_files", mapOf("id" to "root:${LogicalRoot.DOWNLOADS.name}"))
                )
                1 -> PlannerDecision.CallTool(
                    thought = "Create the directory KayanTest",
                    call = ToolCall(
                        "create_directory",
                        mapOf(
                            "parent_id" to "root:${LogicalRoot.DOWNLOADS.name}",
                            "name" to "KayanTest"
                        )
                    )
                )
                2 -> PlannerDecision.CallTool(
                    thought = "Write hello.txt inside KayanTest",
                    call = ToolCall(
                        "write_file",
                        mapOf(
                            "id" to "root:${LogicalRoot.DOWNLOADS.name}/KayanTest/hello.txt",
                            "content" to "مرحبا كيان"
                        )
                    )
                )
                3 -> PlannerDecision.CallTool(
                    thought = "Verify the file exists",
                    call = ToolCall(
                        "get_file_info",
                        mapOf("id" to "root:${LogicalRoot.DOWNLOADS.name}/KayanTest/hello.txt")
                    )
                )
                else -> PlannerDecision.FinalAnswer(
                    thought = "All steps completed and verified",
                    answer = "تم إنشاء المجلد KayanTest والملف hello.txt بنجاح."
                )
            }
        }

        // Fallback: just list Downloads
        return if (step == 0) {
            PlannerDecision.CallTool(
                thought = "Listing Downloads",
                call = ToolCall("list_files", mapOf("id" to "root:${LogicalRoot.DOWNLOADS.name}"))
            )
        } else {
            PlannerDecision.FinalAnswer(
                thought = "Listed",
                answer = state.history.lastOrNull()?.observation ?: "done"
            )
        }
    }
}
