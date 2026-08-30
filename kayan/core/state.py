from dataclasses import dataclass, field
from typing import Any


@dataclass
class StepRecord:
    step_id: str
    tool: str
    params: dict
    success: bool
    output: Any = None
    error: str | None = None


@dataclass
class AgentState:
    task: str
    step_count: int = 0
    status: str = "received"
    history: list[dict] = field(default_factory=list)
    variables: dict[str, Any] = field(default_factory=dict)
    last_result: dict | None = None

    def record(self, tool, params, result):
        self.step_count += 1
        rec = {
            "step_id": f"step_{self.step_count}",
            "tool": tool,
            "params": params,
            "success": result.success,
            "output": result.output,
            "error": result.error,
        }
        self.history.append(rec)
        self.last_result = rec
        self.variables[f"step_{self.step_count}"] = rec
        return rec
