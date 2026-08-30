from .filesystem import (
    ListFilesTool, ReadFileTool, WriteFileTool, CreateDirectoryTool,
    GetFileInfoTool, MoveFileTool, CopyFileTool, DeleteFileTool, SearchFilesTool
)


class ToolRegistry:
    def __init__(self):
        self.tools = {}

    def register(self, tool):
        self.tools[tool.name] = tool

    def get(self, name):
        if name not in self.tools:
            raise KeyError(f"Unknown tool: {name}")
        return self.tools[name]

    def schemas(self):
        return [t.schema() for t in self.tools.values()]

    def execute(self, name, params):
        return self.get(name).execute(**params)

    def load_builtin(self, guard, max_output=12000):
        for tool in [
            ListFilesTool(guard, max_output),
            ReadFileTool(guard),
            WriteFileTool(guard),
            CreateDirectoryTool(guard),
            GetFileInfoTool(guard),
            MoveFileTool(guard),
            CopyFileTool(guard),
            DeleteFileTool(guard),
            SearchFilesTool(guard),
        ]:
            self.register(tool)
