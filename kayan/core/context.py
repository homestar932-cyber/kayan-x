import json


def select_tools(registry, task: str):
    """Cheap deterministic tool retrieval before asking the LLM."""
    t = task.casefold()
    groups = {
        "list_files": ["list", "files", "ملف", "ملفات", "مجلد", "folder", "directory", "داونلود", "download"],
        "read_file": ["read", "اقرأ", "اقرا", "محتوى", "content"],
        "search_files": ["search", "find", "ابحث", "بحث", "اعثر", "عن ملف"],
        "get_file_info": ["size", "حجم", "معلومات", "info", "modified"],
        "create_directory": ["create folder", "mkdir", "أنشئ مجلد", "انشئ مجلد", "مجلد باسم"],
        "write_file": ["write", "اكتب", "أنشئ ملف", "انشئ ملف", "عدّل", "عدل"],
        "move_file": ["move", "انقل", "نقل"],
        "copy_file": ["copy", "انسخ", "نسخ"],
        "delete_file": ["delete", "احذف", "حذف"],
    }
    chosen = []
    for name, words in groups.items():
        if any(w in t for w in words):
            chosen.append(name)
    if not chosen:
        chosen = ["list_files", "search_files", "get_file_info", "read_file"]
    return [registry.get(x).schema() for x in chosen if x in registry.tools]


def schemas_text(schemas):
    return json.dumps(schemas, ensure_ascii=False, separators=(",", ":"))
