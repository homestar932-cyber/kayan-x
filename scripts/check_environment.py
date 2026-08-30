from pathlib import Path
from kayan.config import Config
from kayan.safety.path_guard import PathGuard
from kayan.tools.registry import ToolRegistry

c = Config()
print("Config OK")
print("LLM:", c.server_url)
print("Workspace:", c.workspace)
print("Downloads:", c.downloads, "AVAILABLE" if c.downloads.exists() else "NOT AVAILABLE")

g = PathGuard(c.roots)
r = ToolRegistry()
r.load_builtin(g)
print("Tools:", ", ".join(r.tools))
print("Tool registry OK")
