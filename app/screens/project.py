from kivy.uix.boxlayout import BoxLayout
from kivy.uix.scrollview import ScrollView
from app.widgets.base import label, button, input_box, Card
from app.theme import COLORS

class ProjectScreen(BoxLayout):
    def __init__(self, app_state, **kwargs):
        super().__init__(orientation="vertical", spacing=10, padding=16, **kwargs)
        self.app_state = app_state
        self.add_widget(label("Proyecto", 31, True, size_hint_y=None, height=46))
        self.path_label = label("Sin proyecto activo", 13, color=COLORS["accent2"], size_hint_y=None, height=34)
        self.add_widget(self.path_label)

        form_load = Card(orientation="vertical", size_hint_y=None, height=132, bg_color=COLORS["panel2"])
        form_load.add_widget(label("Abrir o crear por ruta", 16, True, size_hint_y=None, height=28))
        row_load = BoxLayout(size_hint_y=None, height=48, spacing=8)
        self.path_input = input_box("Ej: projects/Mi_Novela", multiline=False)
        row_load.add_widget(self.path_input)
        row_load.add_widget(button("Abrir", size_hint_x=None, width=86, bg=COLORS["accent"], on_press=self.load_path))
        form_load.add_widget(row_load)
        self.add_widget(form_load)

        self.add_widget(button("Crear proyecto demo", size_hint_y=None, height=50, bg=COLORS["success"], on_press=lambda *_: self.create_demo()))

        form = Card(orientation="vertical", size_hint_y=None, height=180, bg_color=COLORS["panel2"])
        form.add_widget(label("Crear dentro del proyecto", 16, True, size_hint_y=None, height=30))
        self.folder_input = input_box("Nueva carpeta. Ej: Notas/Ideas", multiline=False, size_hint_y=None, height=44)
        form.add_widget(self.folder_input)
        self.doc_input = input_box("Nuevo DOCX. Ej: Capítulos/Capítulo 25.docx", multiline=False, size_hint_y=None, height=44)
        form.add_widget(self.doc_input)
        row = BoxLayout(spacing=8, size_hint_y=None, height=44)
        row.add_widget(button("Crear carpeta", on_press=self.create_folder))
        row.add_widget(button("Crear DOCX", bg=COLORS["success"], on_press=self.create_doc))
        form.add_widget(row)
        self.add_widget(form)

        self.tree = BoxLayout(orientation="vertical", spacing=6, size_hint_y=None)
        self.tree.bind(minimum_height=self.tree.setter("height"))
        sv = ScrollView()
        sv.add_widget(self.tree)
        self.add_widget(sv)
        self.refresh()

    def load_path(self, *_):
        path = self.path_input.text.strip()
        if not path:
            self.app_state.toast("Escribe una ruta")
            return
        self.app_state.project_manager.load_or_create(path)
        self.app_state.toast("Proyecto cargado")
        self.refresh()
        self.app_state.refresh_all()

    def create_demo(self):
        self.app_state.create_demo()
        self.refresh()

    def create_folder(self, *_):
        ok, msg = self.app_state.project_manager.create_folder(self.folder_input.text)
        self.folder_input.text = ""
        self.app_state.toast(msg)
        self.refresh()

    def create_doc(self, *_):
        ok, msg = self.app_state.project_manager.create_doc(self.doc_input.text, "Documento nuevo\n\n")
        self.doc_input.text = ""
        self.app_state.toast(msg)
        self.refresh()
        self.app_state.refresh_all()

    def refresh(self):
        pm = self.app_state.project_manager
        self.path_label.text = str(pm.current.root) if pm.current.active else "Sin proyecto activo"
        self.tree.clear_widgets()
        for item in pm.tree_items():
            depth = item.get("depth", 0)
            prefix = "   " * depth
            icon = "📄" if item["type"] == "file" else "📁"
            text = f"{prefix}{icon} {item['name']}"
            if item["type"] == "file" and item["name"].lower().endswith(".docx"):
                self.tree.add_widget(button(text, size_hint_y=None, height=42, on_press=lambda _, p=item["path"]: self.app_state.open_relative_document(p)))
            else:
                self.tree.add_widget(label(text, 14, color=COLORS["muted"], size_hint_y=None, height=34))
