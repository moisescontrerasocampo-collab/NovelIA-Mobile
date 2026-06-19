from pathlib import Path

class DocxService:
    def create_docx(self, path: str | Path, text: str = ""):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        try:
            from docx import Document
            doc = Document()
            if text:
                for line in text.splitlines():
                    doc.add_paragraph(line)
            doc.save(str(path))
            return True, "DOCX creado"
        except Exception as exc:
            return False, f"No se pudo crear DOCX: {exc}"

    def read_docx(self, path: str | Path) -> str:
        path = Path(path)
        if not path.exists():
            return ""
        try:
            from docx import Document
            doc = Document(str(path))
            return "\n".join(p.text for p in doc.paragraphs)
        except Exception as exc:
            return f"[Error leyendo DOCX: {exc}]"

    def write_docx(self, path: str | Path, text: str):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        try:
            from docx import Document
            doc = Document()
            for line in text.splitlines():
                doc.add_paragraph(line)
            doc.save(str(path))
            return True, "DOCX guardado"
        except Exception as exc:
            return False, f"No se pudo guardar DOCX: {exc}"

    def append_docx(self, path: str | Path, title: str, text: str):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        try:
            from docx import Document
            doc = Document(str(path)) if path.exists() else Document()
            doc.add_paragraph("")
            doc.add_heading(title, level=2)
            for line in text.splitlines():
                doc.add_paragraph(line)
            doc.save(str(path))
            return True, "DOCX actualizado"
        except Exception as exc:
            return False, f"No se pudo actualizar DOCX: {exc}"
