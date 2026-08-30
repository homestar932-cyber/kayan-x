package com.kayanx.android.agent.planner

import com.kayanx.android.agent.state.AgentState
import com.kayanx.android.agent.state.ToolCall
import com.kayanx.android.native.LlamaEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Real planner that uses the local llama.cpp engine.
 * Produces exactly ONE next action (tool call or final answer).
 * Never produces a multi-step static plan.
 */
class LocalLlamaPlanner(
    private val engine: LlamaEngine
) : Planner {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val systemPrompt = """
أنت وكيل ملفات محلي اسمه Kayan X. تعمل على جهاز Android بدون إنترنت.
يمكنك استخدام الأدوات التالية فقط (واحدة في كل مرة):

- list_files(id)
- read_file(id)
- write_file(id, content)
- create_directory(parent_id, name)
- delete(id, recursive=false)
- copy(source_id, dest_parent_id, new_name?)
- move(source_id, dest_parent_id, new_name?)
- get_file_info(id)
- search(root_id, query)

الجذور المتاحة:
- root:DOWNLOADS
- root:WORKSPACE

قواعد صارمة:
1. اختر خطوة واحدة فقط في كل مرة.
2. لا تخترع مسارات. استخدم DocumentId فقط.
3. بعد كل أداة ستصلك نتيجة (Observation). قرر الخطوة التالية بناءً عليها.
4. عندما يكتمل الهدف أعد final_answer.

صيغة الرد الإلزامية (JSON فقط):
{"thought":"...","action":"tool_name","args":{...}}
أو
{"thought":"...","action":"final_answer","answer":"..."}
""".trimIndent()

    override suspend fun nextAction(state: AgentState): PlannerDecision {
        if (!engine.isLoaded()) {
            // Fallback to safe behaviour if model not loaded
            return PlannerDecision.FinalAnswer(
                thought = "Model not loaded",
                answer = "النموذج غير محمّل. اختر ملف GGUF أولاً."
            )
        }

        val historyText = state.history.takeLast(8).joinToString("\n") { step ->
            "Thought: ${step.thought}\nTool: ${step.toolName} ${step.toolArgs}\nObservation: ${step.observation}\nVerification: ${step.verification}"
        }

        val prompt = buildString {
            append(systemPrompt)
            append("\n\nالهدف: ${state.goal}\n")
            if (historyText.isNotBlank()) {
                append("\nالسجل السابق:\n$historyText\n")
            }
            append("\nالخطوة التالية (JSON فقط):")
        }

        val raw = engine.complete(prompt, maxTokens = 384, temperature = 0.2f)
        return parseDecision(raw)
    }

    private fun parseDecision(raw: String): PlannerDecision {
        // Extract JSON object even if model added extra text
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) {
            return PlannerDecision.FinalAnswer("parse_failed", "تعذر فهم رد النموذج: $raw")
        }
        val jsonStr = raw.substring(start, end + 1)
        return try {
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            val thought = obj["thought"]?.jsonPrimitive?.content ?: ""
            val action = obj["action"]?.jsonPrimitive?.content ?: "final_answer"

            if (action == "final_answer") {
                val answer = obj["answer"]?.jsonPrimitive?.content ?: thought
                PlannerDecision.FinalAnswer(thought, answer)
            } else {
                val argsObj = obj["args"]?.jsonObject
                val args = mutableMapOf<String, String>()
                argsObj?.forEach { (k, v) ->
                    args[k] = v.jsonPrimitive.content
                }
                PlannerDecision.CallTool(thought, ToolCall(action, args))
            }
        } catch (e: Exception) {
            PlannerDecision.FinalAnswer("parse_error", "خطأ في تحليل JSON: ${e.message}\n$raw")
        }
    }
}
