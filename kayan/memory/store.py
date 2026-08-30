import json
import sqlite3
from pathlib import Path


class MemoryStore:
    def __init__(self, db_path: Path):
        self.conn = sqlite3.connect(str(db_path))
        self.conn.execute("""CREATE TABLE IF NOT EXISTS tasks(
            id INTEGER PRIMARY KEY, task TEXT, status TEXT, history_json TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP)""")
        self.conn.commit()

    def save_task(self, task, status, history):
        self.conn.execute(
            "INSERT INTO tasks(task,status,history_json) VALUES(?,?,?)",
            (task, status, json.dumps(history, ensure_ascii=False))
        )
        self.conn.commit()

    def close(self):
        self.conn.close()
