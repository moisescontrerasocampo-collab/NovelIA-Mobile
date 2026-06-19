import threading
import requests

class AIService:
    def __init__(self, settings):
        self.settings = settings

    def ask_async(self, prompt: str, callback):
        def worker():
            callback(self.ask(prompt))
        threading.Thread(target=worker, daemon=True).start()

    def ask(self, prompt: str) -> str:
        provider = self.settings.get("provider", "Simulado")
        if provider == "OpenRouter":
            return self._ask_openrouter(prompt)
        if provider == "Groq":
            return self._ask_groq(prompt)
        return self._simulated(prompt)

    def _simulated(self, prompt: str) -> str:
        low = prompt.lower()
        if "json" in low or "rcg" in low or "hazañas" in low or "conexiones" in low:
            return (
                "{\n"
                '  "resumen": "Resumen simulado del capítulo.",\n'
                '  "universo": "Aporte simulado para Universo.",\n'
                '  "hazañas": "Hazaña simulada detectada en el capítulo.",\n'
                '  "conexiones": "Conexión simulada entre personajes.",\n'
                '  "personajes": ["Max", "Rang", "Alicia"]\n'
                "}"
            )
        return "Respuesta simulada IA:\n\nPuedo reescribir, resumir, extraer hazañas, conexiones y universo."

    def _ask_openrouter(self, prompt: str) -> str:
        key = self.settings.get("openrouter_key", "")
        model = self.settings.get("openrouter_model", "qwen/qwen3-coder")
        if not key:
            return "Falta API key de OpenRouter. Configúrala en Ajustes."
        url = "https://openrouter.ai/api/v1/chat/completions"
        headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
        payload = {"model": model, "messages": [{"role": "user", "content": prompt}]}
        try:
            r = requests.post(url, json=payload, headers=headers, timeout=60)
            r.raise_for_status()
            return r.json()["choices"][0]["message"]["content"]
        except Exception as exc:
            return f"Error OpenRouter: {exc}"

    def _ask_groq(self, prompt: str) -> str:
        key = self.settings.get("groq_key", "")
        model = self.settings.get("groq_model", "llama-3.3-70b-versatile")
        if not key:
            return "Falta API key de Groq. Configúrala en Ajustes."
        url = "https://api.groq.com/openai/v1/chat/completions"
        headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
        payload = {"model": model, "messages": [{"role": "user", "content": prompt}]}
        try:
            r = requests.post(url, json=payload, headers=headers, timeout=60)
            r.raise_for_status()
            return r.json()["choices"][0]["message"]["content"]
        except Exception as exc:
            return f"Error Groq: {exc}"
