"""Armazenamento simples em JSON, com escrita atômica e trava entre threads."""

import json
import os
import threading
from pathlib import Path


class JsonStore:
    def __init__(self, path, default):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        self._default = default
        self.data = self._load()

    def _load(self):
        try:
            with open(self.path, "r", encoding="utf-8") as f:
                return json.load(f)
        except (OSError, ValueError):
            return json.loads(json.dumps(self._default))

    def save(self):
        with self.lock:
            tmp = self.path.with_suffix(self.path.suffix + ".tmp")
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(self.data, f, ensure_ascii=False)
            os.replace(tmp, self.path)

    def update(self, fn):
        with self.lock:
            result = fn(self.data)
            self.save()
            return result
