import json
from pathlib import Path
from app.models.project import Project
from app.services.docx_service import DocxService

class ProjectManager:
    DEFAULT_FOLDERS = [
        "Personajes",
        "Universo",
        "Capítulos",
        "Capítulos/Resúmenes",
        "Contexto_IA",
        "Backups",
    ]

    def __init__(self):
        self.current = Project()
        self.docx = DocxService()

    def load_or_create(self, root_path: str | Path) -> Project:
        root = Path(root_path)
        root.mkdir(parents=True, exist_ok=True)
        for folder in self.DEFAULT_FOLDERS:
            (root / folder).mkdir(parents=True, exist_ok=True)
        self._ensure_base_docs(root)

        json_path = root / "_proyecto.json"
        if json_path.exists():
            try:
                data = json.loads(json_path.read_text(encoding="utf-8"))
            except Exception:
                data = self._default_json(root)
        else:
            data = self._default_json(root)
            json_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

        self.current = Project(
            name=data.get("nombre", root.name),
            root=root,
            chapters=data.get("capitulos", []),
            characters=data.get("personajes", []),
        )
        self.scan()
        return self.current

    def _default_json(self, root: Path):
        return {
            "nombre": root.name,
            "personajes": [],
            "capitulos": [],
            "universo": {"resumen": "", "ultima_edicion": ""},
            "version_movil": "paso6",
        }

    def _ensure_base_docs(self, root: Path):
        docs = {
            root / "Universo" / "Universo.docx": (
                "Geografía\\n\\nAportes del Autor\\n\\nSincronización Cronológica\\n\\n"
                "Sistema de Poderes\\n\\nAportes del Autor\\n\\nSincronización Cronológica\\n\\n"
                "Historia de entidades\\n\\nAportes del Autor\\n\\nSincronización Cronológica\\n\\n"
                "Elementos de la historia\\n\\nAportes del Autor\\n\\nSincronización Cronológica"
            ),
            root / "Universo" / "Hazañas.docx": "Aportes del Autor\\n\\nSincronización Cronológica",
            root / "Universo" / "Conexiones.docx": "Aportes del Autor\\n\\nSincronización Cronológica",
        }
        for path, text in docs.items():
            if not path.exists():
                self.docx.create_docx(path, text)

    def scan(self):
        if not self.current.active:
            return
        root = self.current.root
        self.current.chapters = [p.name for p in sorted((root / "Capítulos").glob("*.docx"))]
        self.current.characters = [p.name for p in sorted((root / "Personajes").glob("*.docx"))]
        self.save()

    def save(self):
        if not self.current.active:
            return
        data = {
            "nombre": self.current.name,
            "personajes": self.current.characters,
            "capitulos": self.current.chapters,
            "universo": {"resumen": "", "ultima_edicion": ""},
            "version_movil": "paso6",
        }
        (self.current.root / "_proyecto.json").write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    def create_folder(self, name: str):
        if not self.current.active:
            return False, "No hay proyecto activo"
        clean = name.strip().replace("\\", "/").strip("/")
        if not clean:
            return False, "Nombre vacío"
        (self.current.root / clean).mkdir(parents=True, exist_ok=True)
        return True, f"Carpeta creada: {clean}"

    def create_doc(self, relative_path: str, text: str = ""):
        if not self.current.active:
            return False, "No hay proyecto activo"
        clean = relative_path.strip().replace("\\", "/").strip("/")
        if not clean:
            return False, "Nombre vacío"
        if not clean.lower().endswith(".docx"):
            clean += ".docx"
        ok, msg = self.docx.create_docx(self.current.root / clean, text)
        self.scan()
        return ok, msg

    def create_chapter(self, number: str, title: str, text: str = ""):
        if not self.current.active:
            return False, "No hay proyecto activo"
        number = str(number).strip() or str(len(self.current.chapters) + 1)
        title = title.strip() or f"Capítulo {number}"
        filename = f"Capítulo {number} - {title}.docx"
        body = text or f"{title}\\n\\nEscribe aquí el contenido del capítulo."
        return self.create_doc(f"Capítulos/{filename}", body)

    def create_character(self, name: str):
        if not self.current.active:
            return False, "No hay proyecto activo"
        name = name.strip()
        if not name:
            return False, "Nombre vacío"
        body = (
            f"Nombre: {name}\\n\\n"
            "Rasgo principal:\\n\\nTendencia:\\n\\nRol narrativo:\\n\\n"
            "Historia:\\n\\nPersonalidad:\\n\\nRelaciones:\\n\\n"
            "Hazañas\\n\\nConexiones\\n\\n"
        )
        return self.create_doc(f"Personajes/{name}.docx", body)

    def get_path(self, relative_path: str):
        if not self.current.active:
            return None
        return self.current.root / relative_path

    def tree_items(self):
        if not self.current.active:
            return [{"type": "empty", "name": "Sin proyecto activo", "path": ""}]
        root = self.current.root
        items = [{"type": "root", "name": root.name, "path": str(root), "depth": 0}]

        def walk(path: Path, depth=0):
            result = []
            try:
                children = sorted(path.iterdir(), key=lambda p: (p.is_file(), p.name.lower()))
            except Exception:
                return result
            for child in children:
                rel = str(child.relative_to(root))
                result.append({"type": "file" if child.is_file() else "folder", "name": child.name, "path": rel, "depth": depth})
                if child.is_dir() and depth < 2:
                    result.extend(walk(child, depth + 1))
            return result

        items.extend(walk(root))
        return items

    def demo_project(self):
        root = Path("projects") / "Los_Adentrados"
        self.load_or_create(root)
        self.create_chapter("1", "El Diamante del Destino", "Max descubre el Diamante del Destino. Rang observa una señal antigua.")
        self.create_chapter("12", "La fuente de los deseos perdidos", "Max, Rang y Alicia llegan a una fuente antigua. Alicia percibe una conexión con el universo.")
        self.create_chapter("24", "La Oculta y Aterradora Verdad", "Max comprende una verdad antigua. Rang descubre una conexión con El Gran Omega.")
        self.create_character("Max")
        self.create_character("Rang")
        self.create_character("Alicia")
        self.scan()
        return self.current
