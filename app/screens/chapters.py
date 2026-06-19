from kivy.uix.boxlayout import BoxLayout
from kivy.uix.scrollview import ScrollView
from app.widgets.base import label, button, input_box, Card
from app.theme import COLORS

class ChaptersScreen(BoxLayout):
    def __init__(self, app_state, **kwargs):
        super().__init__(orientation="vertical", spacing=10, padding=16, **kwargs)
        self.app_state = app_state
        self.add_widget(label("Capítulos", 31, True, size_hint_y=None, height=46))

        form = Card(orientation="vertical", size_hint_y=None, height=188, bg_color=COLORS["panel2"])
        form.add_widget(label("Nuevo capítulo", 16, True, size_hint_y=None, height=28))
        self.num_input = input_box("Número. Ej: 25", multiline=False, size_hint_y=None, height=42)
        self.title_input = input_box("Título", multiline=False, size_hint_y=None, height=42)
        form.add_widget(self.num_input)
        form.add_widget(self.title_input)
        row = BoxLayout(size_hint_y=None, height=42, spacing=8)
        row.add_widget(button("Crear DOCX", bg=COLORS["accent"], on_press=self.new_chapter))
        row.add_widget(button("RCG actual", bg=COLORS["success"], on_press=lambda *_: app_state.open_sync_current()))
        form.add_widget(row)
        self.add_widget(form)

        self.list_box = BoxLayout(orientation="vertical", spacing=9, size_hint_y=None)
        self.list_box.bind(minimum_height=self.list_box.setter("height"))
        sv = ScrollView()
        sv.add_widget(self.list_box)
        self.add_widget(sv)
        self.refresh()

    def refresh(self):
        self.list_box.clear_widgets()
        pm = self.app_state.project_manager
        if not pm.current.active:
            self.list_box.add_widget(label("Crea un proyecto demo desde Inicio o Proyecto.", 15, color=COLORS["muted"], size_hint_y=None, height=50))
            return
        for path in sorted((pm.current.root / "Capítulos").glob("*.docx")):
            row = BoxLayout(size_hint_y=None, height=58, spacing=6)
            row.add_widget(button("📖 " + path.stem, on_press=lambda _, p=path: self.app_state.open_document(p)))
            row.add_widget(button("RCG", size_hint_x=None, width=70, bg=COLORS["success"], on_press=lambda _, p=path: self.app_state.sync_chapter(p)))
            self.list_box.add_widget(row)

    def new_chapter(self, *_):
        pm = self.app_state.project_manager
        if not pm.current.active:
            self.app_state.toast("Primero crea o abre un proyecto")
            return
        ok, msg = pm.create_chapter(self.num_input.text, self.title_input.text)
        self.num_input.text = ""
        self.title_input.text = ""
        self.app_state.toast(msg)
        self.refresh()
