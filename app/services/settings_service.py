import json
from pathlib import Path

class SettingsService:
    DEFAULTS = {
        "provider": "Simulado",
        "openrouter_key": "",
        "groq_key": "",
        "openrouter_model": "qwen/qwen3-coder",
        "groq_model": "llama-3.3-70b-versatile",
        "language": "es",
    }

    def __init__(self, path="app/data/settings.json"):
        self.path = Path(path)
        self.data = self.DEFAULTS.copy()
        self.load()

    def load(self):
        if self.path.exists():
            try:
                self.data.update(json.loads(self.path.read_text(encoding="utf-8")))
            except Exception:
                pass
        else:
            self.save()

    def save(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self.data, ensure_ascii=False, indent=2), encoding="utf-8")

    def set(self, key, value):
        self.data[key] = value
        self.save()

    def get(self, key, default=""):
        return self.data.get(key, default)
