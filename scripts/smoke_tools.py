from pathlib import Path
from tempfile import TemporaryDirectory

from kayan.safety.path_guard import PathGuard
from kayan.tools.registry import ToolRegistry

with TemporaryDirectory() as td:
    root = Path(td)
    guard = PathGuard({"workspace": root})
    reg = ToolRegistry()
    reg.load_builtin(guard)

    r = reg.execute("create_directory", {"path": "workspace:/test"})
    assert r.success, r
    r = reg.execute("write_file", {"file_path": "workspace:/test/hello.txt", "content": "مرحبا كيان"})
    assert r.success, r
    r = reg.execute("read_file", {"file_path": "workspace:/test/hello.txt"})
    assert r.success and r.output == "مرحبا كيان", r
    r = reg.execute("move_file", {"source": "workspace:/test/hello.txt", "destination": "workspace:/test/world.txt"})
    assert r.success, r
    r = reg.execute("get_file_info", {"file_path": "workspace:/test/world.txt"})
    assert r.success and r.output["exists"], r
    print("SMOKE TOOLS OK")
