from pathlib import Path
import yaml
from ..tools.base import RiskLevel


class PolicyEngine:
    def __init__(self, policy_file: Path):
        self.data = {}
        try:
            self.data = yaml.safe_load(policy_file.read_text(encoding="utf-8")) or {}
        except FileNotFoundError:
            self.data = {}

    def policy_for(self, tool_name):
        return (self.data.get("tools") or {}).get(tool_name, {})

    def needs_confirmation(self, tool, params, existing_target=False):
        p = self.policy_for(tool.name)
        if p.get("requires_confirmation"):
            return True
        if tool.risk_level in (RiskLevel.HIGH, RiskLevel.CRITICAL):
            if p.get("confirm_if_overwrite") and existing_target:
                return True
            return bool(self.data.get("defaults", {}).get("require_confirmation_for_high_risk", True))
        return False
