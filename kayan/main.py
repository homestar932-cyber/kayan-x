from rich.console import Console
from rich.prompt import Prompt, Confirm

from .config import Config
from .llm.client import LLMClient
from .tools.registry import ToolRegistry
from .safety.path_guard import PathGuard
from .safety.policy import PolicyEngine
from .core.planner import Planner
from .core.executor import Executor
from .core.verifier import Verifier
from .core.orchestrator import Orchestrator
from .memory.store import MemoryStore

console = Console()


def main():
    config = Config()
    guard = PathGuard(config.roots)
    registry = ToolRegistry()
    registry.load_builtin(guard, config.max_tool_output)

    llm = LLMClient(config)
    policy = PolicyEngine(config.policy_path)
    memory = MemoryStore(config.db_path)

    def confirm(tool, params, risk):
        console.print(f"[yellow]تأكيد مطلوب[/yellow] الأداة: {tool} | الخطورة: {risk}")
        console.print(f"المعاملات: {params}")
        return Confirm.ask("هل تريد السماح؟", default=False)

    executor = Executor(registry, policy, confirm)
    planner = Planner(llm, registry, config)
    verifier = Verifier()
    agent = Orchestrator(planner, executor, verifier, config)

    console.print("[bold green]Kayan X 2.0 — Local Cognitive Agent[/bold green]")
    console.print(f"Workspace: {config.workspace}")
    if config.downloads.exists():
        console.print(f"Downloads: {config.downloads}")
    else:
        console.print(
            "[yellow]Downloads غير متاح. نفّذ termux-setup-storage ثم أعد التشغيل.[/yellow]"
        )
    console.print("اكتب 'خروج' للإنهاء.\n")

    try:
        while True:
            task = Prompt.ask("[bold cyan]أنت[/bold cyan]")
            if task.strip().casefold() in {"خروج", "exit", "quit"}:
                break
            if not task.strip():
                continue
            response = agent.run(task)
            console.print(f"[bold magenta]Kayan X[/bold magenta]: {response}")
            memory.save_task(task, "completed", [])
    except KeyboardInterrupt:
        console.print("\nتم الإيقاف.")
    finally:
        memory.close()


if __name__ == "__main__":
    main()
