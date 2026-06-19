from kivy.uix.boxlayout import BoxLayout
from kivy.uix.scrollview import ScrollView
from app.widgets.base import label, button, input_box, Card
from app.theme import COLORS

class CharactersScreen(BoxLayout):
    def __init__(self, app_state, **kwargs):
        super().__init__(orientation="vertical", spacing=10, padding=16, **kwargs)
        self.app_state = app_state
        self.add_widget(label("Personajes", 31, True, size_hint_y=None, height=46))

        form = Card(orientation="vertical", size_hint_y=None, height=124, bg_color=COLORS["panel2"])
        form.add_widget(label("Crear personaje", 16, True, size_hint_y=None, height=28))
        row = BoxLayout(size_hint_y=None, height=48, spacing=8)
        self.name_input = input_box("Nombre", multiline=False)
        row.add_widget(self.name_input)
        row.add_widget(button("Crear", size_hint_x=None, width=94, bg=COLORS["accent"], on_press=self.new_character))
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
        for path in sorted((pm.current.root / "Personajes").glob("*.docx")):
            self.list_box.add_widget(button("👤 " + path.stem, size_hint_y=None, height=58, on_press=lambda _, p=path: self.app_state.open_document(p)))

    def new_character(self, *_):
        pm = self.app_state.project_manager
        if not pm.current.active:
            self.app_state.toast("Primero crea o abre un proyecto")
            return
        ok, msg = pm.create_character(self.name_input.text)
        self.name_input.text = ""
        self.app_state.toast(msg)
        self.refresh()
