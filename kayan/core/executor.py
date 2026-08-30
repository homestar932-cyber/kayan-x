class ExecutionBlocked(RuntimeError):
    pass


class Executor:
    def __init__(self, registry, policy, confirm_callback):
        self.registry = registry
        self.policy = policy
        self.confirm = confirm_callback

    def execute(self, action):
        tool = self.registry.get(action["tool"])
        params = action.get("params") or {}

        # Determine likely overwrite target for policy decisions.
        existing = False
        for key in ("file_path", "destination", "path"):
            value = params.get(key)
            if value and key in ("file_path", "destination"):
                try:
                    # Tool itself remains the final path authority.
                    from pathlib import Path
                    existing = Path(value).expanduser().exists()
                except Exception:
                    pass

        if self.policy.needs_confirmation(tool, params, existing):
            approved = self.confirm(
                tool.name,
                params,
                tool.risk_level.value
            )
            if not approved:
                raise ExecutionBlocked("Operation rejected by user")

        return self.registry.execute(tool.name, params)
