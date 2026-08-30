from pathlib import Path
import pytest
from kayan.safety.path_guard import PathGuard, PathViolation


def test_workspace_path(tmp_path):
    g = PathGuard({"workspace": tmp_path})
    assert g.resolve("workspace:/a.txt").parent == tmp_path


def test_escape_blocked(tmp_path):
    g = PathGuard({"workspace": tmp_path})
    with pytest.raises(PathViolation):
        g.resolve("workspace:/../../etc/passwd")
