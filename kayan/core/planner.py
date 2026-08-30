import json
from .context import select_tools, schemas_text
from ..llm.structured import extract_json


SYSTEM = """أنت عقل Kayan X، وكيل محلي لإدارة الملفات.
لا تنفذ أي شيء بنفسك. اختر خطوة واحدة فقط في كل مرة اعتمادًا على حالة التنفيذ الحالية.
يجب أن تختار tool من الأدوات المعروضة فقط.
أعد JSON فقط بهذا الشكل:
{"action":"tool","tool":"اسم_الأداة","params":{...},"goal_complete":false,"final_message":""}
إذا كانت المهمة مكتملة أعد:
{"action":"finish","tool":"","params":{},"goal_complete":true,"final_message":"..."}
لا تخترع نتائج أو مسارات. استخدم aliases مثل downloads:/ و workspace:/ عندما يكون ذلك واضحًا.
"""


class Planner:
    def __init__(self, llm, registry, config):
        self.llm = llm
        self.registry = registry
        self.config = config

    def next_action(self, state):
        schemas = select_tools(self.registry, state.task)
        roots = {k: str(v) for k, v in self.config.roots.items()}
        prompt = {
            "task": state.task,
            "step_count": state.step_count,
            "allowed_roots": roots,
            "previous_steps": state.history[-8:],
            "last_result": state.last_result,
            "tools": schemas,
        }
        messages = [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": json.dumps(prompt, ensure_ascii=False)},
        ]
        raw = self.llm.chat(messages)
        action = extract_json(raw)
        self._validate(action)
        return action

    def _validate(self, action):
        if not isinstance(action, dict):
            raise ValueError("Planner output must be an object")
        if action.get("action") not in {"tool", "finish"}:
            raise ValueError("Invalid action")
        if action["action"] == "tool":
            if action.get("tool") not in self.registry.tools:
                raise ValueError("Unknown tool selected")
            if not isinstance(action.get("params", {}), dict):
                raise ValueError("params must be an object")
