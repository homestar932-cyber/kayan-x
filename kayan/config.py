import os
from pathlib import Path


class Config:
    def __init__(self):
        home = Path.home()
        self.home = home
        self.workspace = Path(
            os.getenv("KAYAN_WORKSPACE", str(home / "kayan-x" / "workspace"))
        ).expanduser().resolve()
        self.downloads = self._detect_downloads(home)
        self.server_url = os.getenv("KAYAN_LLM_URL", "http://127.0.0.1:8080")
        self.model_name = os.getenv("KAYAN_MODEL", "")
        self.request_timeout = float(os.getenv("KAYAN_TIMEOUT", "120"))
        self.max_steps = int(os.getenv("KAYAN_MAX_STEPS", "20"))
        self.max_tool_output = int(os.getenv("KAYAN_MAX_TOOL_OUTPUT", "12000"))
        self.db_path = Path(
            os.getenv("KAYAN_DB", str(home / "kayan-x" / "runtime" / "kayan.sqlite3"))
        ).expanduser()
        self.preferences_path = Path(
            os.getenv("KAYAN_PREFS", str(home / "kayan-x" / "preferences.json"))
        ).expanduser()
        self.policy_path = Path(
            os.getenv("KAYAN_POLICY", str(home / "kayan-x" / "policies.yaml"))
        ).expanduser()

        self.workspace.mkdir(parents=True, exist_ok=True)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)

    @staticmethod
    def _detect_downloads(home: Path) -> Path:
        candidates = [
            home / "storage" / "downloads",
            home / "storage" / "shared" / "Download",
            Path("/storage/emulated/0/Download"),
        ]
        for p in candidates:
            try:
                if p.exists() and p.is_dir():
                    return p.resolve()
            except OSError:
                pass
        # Expected Termux location even before permission is granted.
        return (home / "storage" / "downloads").resolve()

    @property
    def roots(self):
        roots = {"workspace": self.workspace}
        if self.downloads.exists() and self.downloads.is_dir():
            roots["downloads"] = self.downloads
        return roots
