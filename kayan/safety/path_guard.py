from pathlib import Path


class PathViolation(ValueError):
    pass


class PathGuard:
    """Centralized filesystem boundary. Never trust model-generated paths."""

    def __init__(self, roots: dict[str, Path]):
        self.roots = {k: p.resolve() for k, p in roots.items()}

    def aliases(self):
        return {k: str(v) for k, v in self.roots.items()}

    def resolve(self, raw: str, allow_nonexistent: bool = False) -> Path:
        raw = str(raw).strip()
        if not raw:
            raise PathViolation("Empty path")

        if raw.startswith("downloads:/"):
            raw = raw[len("downloads:/"):]
            base = self.roots.get("downloads")
            if base is None:
                raise PathViolation(
                    "Download folder is unavailable. Run termux-setup-storage and grant storage permission."
                )
            candidate = base / raw
        elif raw.startswith("workspace:/"):
            candidate = self.roots["workspace"] / raw[len("workspace:/"):]
        else:
            candidate = Path(raw).expanduser()
            if not candidate.is_absolute():
                candidate = self.roots["workspace"] / candidate

        candidate = candidate.resolve(strict=False)
        for root in self.roots.values():
            try:
                candidate.relative_to(root)
                if allow_nonexistent or candidate.exists():
                    return candidate
                return candidate
            except ValueError:
                continue
        raise PathViolation(f"Path is outside allowed roots: {raw}")

    def root_name(self, path: Path) -> str:
        path = path.resolve()
        for name, root in self.roots.items():
            try:
                path.relative_to(root)
                return name
            except ValueError:
                continue
        return "unknown"
