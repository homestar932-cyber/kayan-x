from .state import AgentState
from .executor import ExecutionBlocked


class Orchestrator:
    def __init__(self, planner, executor, verifier, config):
        self.planner = planner
        self.executor = executor
        self.verifier = verifier
        self.config = config

    def run(self, task):
        state = AgentState(task=task)
        state.status = "planning"

        for _ in range(self.config.max_steps):
            try:
                action = self.planner.next_action(state)
            except Exception as e:
                state.status = "failed"
                return f"تعذر توليد الخطوة التالية: {e}"

            if action["action"] == "finish":
                state.status = "success"
                return action.get("final_message") or "تم إنجاز المهمة."

            state.status = "policy_check"
            try:
                result = self.executor.execute(action)
            except ExecutionBlocked as e:
                state.status = "blocked"
                return f"تم إيقاف العملية: {e}"
            except Exception as e:
                state.status = "failed"
                state.history.append({
                    "step_id": f"step_{state.step_count + 1}",
                    "tool": action.get("tool"),
                    "params": action.get("params", {}),
                    "success": False,
                    "error": str(e),
                })
                state.step_count += 1
                state.last_result = state.history[-1]
                continue

            rec = state.record(action["tool"], action.get("params", {}), result)
            verification = self.verifier.verify(action, result)
            rec["verification"] = verification

            if not verification["success"]:
                state.status = "replanning"
                continue

            state.status = "observing"

        state.status = "max_steps"
        return f"توقفت المهمة بعد بلوغ الحد الأقصى ({self.config.max_steps}) من الخطوات."
