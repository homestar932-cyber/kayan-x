import os
import shutil
from pathlib import Path
from .base import Tool, ToolResult, RiskLevel


class ListFilesTool(Tool):
    name = "list_files"
    description = "List entries in an allowed directory."
    parameters = {"type": "object", "properties": {
        "path": {"type": "string"},
        "recursive": {"type": "boolean"}
    }, "required": ["path"]}

    def __init__(self, guard, max_output=12000):
        self.guard, self.max_output = guard, max_output

    def execute(self, path, recursive=False):
        try:
            p = self.guard.resolve(path)
            if not p.is_dir():
                return ToolResult(False, error=f"Not a directory: {p}")
            if recursive:
                entries = [str(x.relative_to(p)) for x in p.rglob("*")]
            else:
                entries = [x.name for x in p.iterdir()]
            return ToolResult(True, output=entries[:self.max_output],
                              metadata={"count": len(entries), "root": self.guard.root_name(p)})
        except Exception as e:
            return ToolResult(False, error=str(e))


class ReadFileTool(Tool):
    name = "read_file"
    description = "Read a bounded UTF-8 text slice from a file."
    parameters = {"type": "object", "properties": {
        "file_path": {"type": "string"},
        "max_chars": {"type": "integer"},
        "offset": {"type": "integer"}
    }, "required": ["file_path"]}

    def __init__(self, guard):
        self.guard = guard

    def execute(self, file_path, max_chars=12000, offset=0):
        try:
            p = self.guard.resolve(file_path)
            if not p.is_file():
                return ToolResult(False, error=f"File not found: {p}")
            max_chars = max(1, min(int(max_chars), 100000))
            offset = max(0, int(offset))
            with p.open("r", encoding="utf-8", errors="replace") as f:
                f.seek(offset)
                content = f.read(max_chars)
            return ToolResult(True, output=content,
                              metadata={"path": str(p), "offset": offset, "chars": len(content)})
        except Exception as e:
            return ToolResult(False, error=str(e))


class WriteFileTool(Tool):
    name = "write_file"
    description = "Create or replace a UTF-8 text file."
    risk_level = RiskLevel.HIGH
    parameters = {"type": "object", "properties": {
        "file_path": {"type": "string"},
        "content": {"type": "string"},
        "overwrite": {"type": "boolean"}
    }, "required": ["file_path", "content"]}

    def __init__(self, guard):
        self.guard = guard

    def execute(self, file_path, content, overwrite=False):
        try:
            p = self.guard.resolve(file_path, allow_nonexistent=True)
            if p.exists() and not overwrite:
                return ToolResult(False, error="Target exists; overwrite=true is required")
            p.parent.mkdir(parents=True, exist_ok=True)
            tmp = p.with_name(p.name + ".kayan_tmp")
            tmp.write_text(str(content), encoding="utf-8")
            tmp.replace(p)
            return ToolResult(True, output=f"Written: {p}",
                              metadata={"path": str(p), "size": p.stat().st_size})
        except Exception as e:
            return ToolResult(False, error=str(e))


class CreateDirectoryTool(Tool):
    name = "create_directory"
    description = "Create an allowed directory."
    parameters = {"type": "object", "properties": {"path": {"type": "string"}}, "required": ["path"]}

    def __init__(self, guard):
        self.guard = guard

    def execute(self, path):
        try:
            p = self.guard.resolve(path, allow_nonexistent=True)
            p.mkdir(parents=True, exist_ok=True)
            return ToolResult(True, output=f"Directory ready: {p}",
                              metadata={"path": str(p), "exists": p.is_dir()})
        except Exception as e:
            return ToolResult(False, error=str(e))


class GetFileInfoTool(Tool):
    name = "get_file_info"
    description = "Return deterministic filesystem metadata."
    parameters = {"type": "object", "properties": {"file_path": {"type": "string"}}, "required": ["file_path"]}

    def __init__(self, guard):
        self.guard = guard

    def execute(self, file_path):
        try:
            p = self.guard.resolve(file_path)
            s = p.stat()
            return ToolResult(True, output={
                "path": str(p), "name": p.name, "exists": True,
                "is_file": p.is_file(), "is_dir": p.is_dir(),
                "size": s.st_size, "modified_ns": s.st_mtime_ns
            })
        except Exception as e:
            return ToolResult(False, error=str(e))


class MoveFileTool(Tool):
    name = "move_file"
    description = "Move an allowed file or directory."
    risk_level = RiskLevel.MEDIUM
    parameters = {"type": "object", "properties": {
        "source": {"type": "string"}, "destination": {"type": "string"}
    }, "required": ["source", "destination"]}

    def __init__(self, guard):
        self.guard = guard

    def execute(self, source, destination):
        try:
            s = self.guard.resolve(source)
            d = self.guard.resolve(destination, allow_nonexistent=True)
            if not s.exists():
                return ToolResult(False, error=f"Source not found: {s}")
            d.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(s), str(d))
            return ToolResult(True, output=f"Moved {s} -> {d}",
                              metadata={"source": str(s), "destination": str(d)})
        except Exception as e:
            return ToolResult(False, error=str(e))


class CopyFileTool(Tool):
    name = "copy_file"
    description = "Copy an allowed file or directory."
    risk_level = RiskLevel.MEDIUM
    parameters = {"type": "object", "properties": {
        "source": {"type": "string"}, "destination": {"type": "string"}
    }, "required": ["source", "destination"]}

    def __init__(self, guard):
        self.guard = guard

    def execute(self, source, destination):
        try:
            s = self.guard.resolve(source)
            d = self.guard.resolve(destination, allow_nonexistent=True)
            if not s.exists():
                return ToolResult(False, error=f"Source not found: {s}")
            d.parent.mkdir(parents=True, exist_ok=True)
            if s.is_dir():
                shutil.copytree(s, d, dirs_exist_ok=True)
            else:
                shutil.copy2(s, d)
            return ToolResult(True, output=f"Copied {s} -> {d}",
                              metadata={"source": str(s), "destination": str(d)})
        except Exception as e:
            return ToolResult(False, error=str(e))


class DeleteFileTool(Tool):
    name = "delete_file"
    description = "Delete an allowed file. Directories require explicit recursive=true."
    risk_level = RiskLevel.CRITICAL
    parameters = {"type": "object", "properties": {
        "file_path": {"type": "string"}, "recursive": {"type": "boolean"}
    }, "required": ["file_path"]}

    def __init__(self, guard):
        self.guard = guard

    def execute(self, file_path, recursive=False):
        try:
            p = self.guard.resolve(file_path)
            if p.is_dir():
                if not recursive:
                    return ToolResult(False, error="Directory deletion requires recursive=true")
                shutil.rmtree(p)
            else:
                p.unlink()
            return ToolResult(True, output=f"Deleted: {p}",
                              metadata={"path": str(p), "exists_after": p.exists()})
        except Exception as e:
            return ToolResult(False, error=str(e))


class SearchFilesTool(Tool):
    name = "search_files"
    description = "Search filenames under an allowed directory."
    parameters = {"type": "object", "properties": {
        "path": {"type": "string"}, "pattern": {"type": "string"},
        "recursive": {"type": "boolean"}
    }, "required": ["path", "pattern"]}

    def __init__(self, guard):
        self.guard = guard

    def execute(self, path, pattern, recursive=True):
        try:
            p = self.guard.resolve(path)
            if not p.is_dir():
                return ToolResult(False, error=f"Not a directory: {p}")
            matches = []
            iterator = p.rglob("*") if recursive else p.glob("*")
            needle = str(pattern).casefold()
            for x in iterator:
                if needle in x.name.casefold():
                    matches.append(str(x))
                    if len(matches) >= 500:
                        break
            return ToolResult(True, output=matches, metadata={"count": len(matches)})
        except Exception as e:
            return ToolResult(False, error=str(e))
