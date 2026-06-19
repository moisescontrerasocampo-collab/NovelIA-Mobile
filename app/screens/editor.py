from pathlib import Path
from kivy.uix.boxlayout import BoxLayout
from app.widgets.base import label, button, input_box
from app.theme import COLORS

class EditorScreen(BoxLayout):
    def __init__(self, app_state, **kwargs):
        super().__init__(orientation="vertical", spacing=10, padding=14, **kwargs)
        self.app_state = app_state
        self.current_path = None

        top = BoxLayout(spacing=8, size_hint_y=None, height=54)
        top.add_widget(button("←", size_hint_x=None, width=52, on_press=lambda *_: app_state.close_editor()))
        self.title = label("Editor", 18, True)
        top.add_widget(self.title)
        top.add_widget(button("💾", size_hint_x=None, width=56, bg=COLORS["success"], on_press=lambda *_: self.save()))
        self.add_widget(top)

        actions = BoxLayout(spacing=8, size_hint_y=None, height=50)
        actions.add_widget(button("Responder", bg=COLORS["accent"], on_press=lambda *_: app_state.open_ai("Texto seleccionado del editor")))
        actions.add_widget(button("Sincronizar RCG", bg=COLORS["success"], on_press=lambda *_: app_state.sync_current_document()))
        self.add_widget(actions)

        self.text = input_box("Documento", multiline=True)
        self.add_widget(self.text)

        bottom = BoxLayout(spacing=8, size_hint_y=None, height=54)
        bottom.add_widget(button("Guardar DOCX", bg=COLORS["success"], on_press=lambda *_: self.save()))
        bottom.add_widget(button("Aplicar IA", on_press=lambda *_: app_state.open_ai("Documento actual")))
        self.add_widget(bottom)

    def open_path(self, path):
        self.current_path = Path(path)
        self.title.text = self.current_path.name
        self.text.text = self.app_state.docx.read_docx(self.current_path)

    def open_title(self, title):
        self.current_path = None
        self.title.text = title
        self.text.text = "Documento simulado\n\nEscribe aquí..."

    def save(self):
        if not self.current_path:
            self.app_state.toast("Editor simulado: no hay ruta DOCX asignada")
            return
        ok, msg = self.app_state.docx.write_docx(self.current_path, self.text.text)
        self.app_state.project_manager.scan()
        self.app_state.toast(msg)
        self.app_state.refresh_all()

    def insert_ai_text(self, text):
        self.text.text += text
