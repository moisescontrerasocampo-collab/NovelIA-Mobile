import json
import re
from datetime import datetime
from pathlib import Path

class RCGService:
    def __init__(self, project_manager, ai_service, docx_service):
        self.pm = project_manager
        self.ai = ai_service
        self.docx = docx_service

    def sync_chapter(self, chapter_path: Path):
        if not self.pm.current.active:
            return False, "No hay proyecto activo"
        chapter_path = Path(chapter_path)
        if not chapter_path.exists():
            return False, "Capítulo no existe"

        text = self.docx.read_docx(chapter_path)
        chapter_title = chapter_path.stem
        data = self._analyze(chapter_title, text)

        root = self.pm.current.root
        stamp = datetime.now().strftime("%Y-%m-%d %H:%M")
        header = f"{chapter_title} · {stamp}"

        resumen_path = root / "Capítulos" / "Resúmenes" / f"{chapter_title} - Resumen.docx"
        self.docx.write_docx(resumen_path, data.get("resumen", ""))

        self.docx.append_docx(root / "Universo" / "Universo.docx", header, data.get("universo", ""))
        self.docx.append_docx(root / "Universo" / "Hazañas.docx", header, data.get("hazañas", ""))
        self.docx.append_docx(root / "Universo" / "Conexiones.docx", header, data.get("conexiones", ""))

        for char in data.get("personajes", []):
            char_path = root / "Personajes" / f"{char}.docx"
            if char_path.exists():
                self.docx.append_docx(
                    char_path,
                    f"Actualización desde {chapter_title}",
                    f"Hazañas:\n{data.get('hazañas', '')}\n\nConexiones:\n{data.get('conexiones', '')}"
                )

        self.pm.scan()
        return True, f"RCG completado para {chapter_title}"

    def _analyze(self, title, text):
        prompt = (
            "Responde SOLO con JSON válido. Analiza este capítulo de novela. "
            "Devuelve claves: resumen, universo, hazañas, conexiones, personajes. "
            "personajes debe ser una lista de nombres.\n\n"
            f"Título: {title}\n\nTexto:\n{text[:12000]}"
        )
        raw = self.ai.ask(prompt)
        data = self._extract_json(raw)
        if data:
            return data
        return self._heuristic(title, text)

    def _extract_json(self, raw):
        try:
            return json.loads(raw)
        except Exception:
            pass
        try:
            match = re.search(r"\{.*\}", raw, flags=re.S)
            if match:
                return json.loads(match.group(0))
        except Exception:
            return None
        return None

    def _heuristic(self, title, text):
        names = []
        for name in self.pm.current.characters:
            clean = Path(name).stem
            if clean.lower() in text.lower():
                names.append(clean)
        first = " ".join(text.split()[:70])
        return {
            "resumen": f"Resumen automático básico de {title}: {first}",
            "universo": f"Aporte cronológico básico desde {title}.",
            "hazañas": f"Hazaña detectada en {title}: los personajes avanzan en la trama principal.",
            "conexiones": f"Conexión detectada en {title}: vínculos narrativos entre {', '.join(names) if names else 'personajes no identificados'}.",
            "personajes": names,
        }
