from pathlib import Path


class Verifier:
    """Deterministic postconditions for high-value filesystem actions."""

    def verify(self, action, result):
        if not result.success:
            return {"success": False, "reason": result.error or "tool failed"}

        tool = action.get("tool")
        p = action.get("params", {})

        try:
            if tool == "create_directory":
                path = self._path_from_metadata(result, "path")
                return {"success": bool(path and path.is_dir()), "reason": "directory existence checked"}

            if tool == "write_file":
                path = self._path_from_metadata(result, "path")
                return {"success": bool(path and path.is_file()), "reason": "file existence checked"}

            if tool == "delete_file":
                path = self._path_from_metadata(result, "path")
                return {"success": bool(path and not path.exists()), "reason": "absence checked"}

            if tool == "move_file":
                d = result.metadata.get("destination") if result.metadata else None
                return {"success": bool(d and Path(d).exists()), "reason": "destination existence checked"}

            if tool == "copy_file":
                d = result.metadata.get("destination") if result.metadata else None
                return {"success": bool(d and Path(d).exists()), "reason": "destination existence checked"}

            return {"success": True, "reason": "tool returned success"}
        except Exception as e:
            return {"success": False, "reason": f"verification error: {e}"}

    @staticmethod
    def _path_from_metadata(result, key):
        value = (result.metadata or {}).get(key)
        return Path(value) if value else None
