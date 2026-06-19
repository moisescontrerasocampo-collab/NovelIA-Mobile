from kivy.uix.boxlayout import BoxLayout
from app.widgets.base import label, button, Card
from app.theme import COLORS

class HomeScreen(BoxLayout):
    def __init__(self, app_state, **kwargs):
        super().__init__(orientation="vertical", spacing=12, padding=16, **kwargs)
        self.app_state = app_state
        self.add_widget(label("Escritorio", 31, True, size_hint_y=None, height=46))
        self.subtitle = label("Sin proyecto activo", 14, color=COLORS["muted"], size_hint_y=None, height=28)
        self.add_widget(self.subtitle)
        self.stats = BoxLayout(spacing=10, size_hint_y=None, height=100)
        self.add_widget(self.stats)

        feature = Card(orientation="vertical", bg_color=COLORS["panel2"], size_hint_y=None, height=150)
        feature.add_widget(label("Siguiente acción", 12, color=COLORS["accent2"], size_hint_y=None, height=24))
        feature.add_widget(label("Construcción del MVP móvil", 21, True))
        feature.add_widget(label("Crea proyecto, capítulos, personajes y prueba RCG básico.", 13, color=COLORS["muted"]))
        feature.add_widget(button("Abrir proyecto", size_hint_y=None, height=42, bg=COLORS["accent"], on_press=lambda *_: app_state.switch("project")))
        self.add_widget(feature)

        self.add_widget(button("Crear/abrir proyecto demo", size_hint_y=None, height=54, bg=COLORS["success"], on_press=lambda *_: app_state.create_demo()))
        self.add_widget(button("Abrir sincronización RCG", size_hint_y=None, height=54, on_press=lambda *_: app_state.open_sync_current()))
        self.refresh()

    def refresh(self):
        self.stats.clear_widgets()
        pm = self.app_state.project_manager
        if pm.current.active:
            self.subtitle.text = f"Proyecto activo: {pm.current.name}"
            values = [("Capítulos", str(len(pm.current.chapters))), ("Personajes", str(len(pm.current.characters))), ("Estado", "Activo")]
        else:
            self.subtitle.text = "Sin proyecto activo"
            values = [("Capítulos", "0"), ("Personajes", "0"), ("Estado", "—")]

        for title, value in values:
            card = Card(orientation="vertical", bg_color=COLORS["card2"])
            card.add_widget(label(value, 25, True))
            card.add_widget(label(title, 13, color=COLORS["muted"]))
            self.stats.add_widget(card)
