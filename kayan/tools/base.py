from dataclasses import dataclass
from enum import Enum
from typing import Any, Dict


class RiskLevel(str, Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


@dataclass
class ToolResult:
    success: bool
    output: Any = None
    error: str | None = None
    metadata: Dict[str, Any] | None = None


class Tool:
    name = ""
    description = ""
    parameters: Dict[str, Any] = {}
    risk_level = RiskLevel.LOW
    requires_confirmation = False

    def schema(self):
        return {
            "name": self.name,
            "description": self.description,
            "parameters": self.parameters,
            "risk_level": self.risk_level.value,
            "requires_confirmation": self.requires_confirmation,
        }

    def execute(self, **kwargs) -> ToolResult:
        raise NotImplementedError
