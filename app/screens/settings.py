from kivy.uix.boxlayout import BoxLayout
from app.widgets.base import label, button, input_box, Card
from app.theme import COLORS

class SettingsScreen(BoxLayout):
    def __init__(self, app_state, **kwargs):
        super().__init__(orientation="vertical", spacing=10, padding=16, **kwargs)
        self.app_state = app_state
        self.add_widget(label("Ajustes IA", 31, True, size_hint_y=None, height=46))

        card = Card(orientation="vertical", bg_color=COLORS["panel2"])
        card.add_widget(label("Proveedor", 16, True, size_hint_y=None, height=28))

        rowp = BoxLayout(size_hint_y=None, height=44, spacing=8)
        rowp.add_widget(button("Simulado", on_press=lambda *_: self.set_provider("Simulado")))
        rowp.add_widget(button("OpenRouter", on_press=lambda *_: self.set_provider("OpenRouter")))
        rowp.add_widget(button("Groq", on_press=lambda *_: self.set_provider("Groq")))
        card.add_widget(rowp)

        self.openrouter_model = input_box("Modelo OpenRouter", multiline=False, size_hint_y=None, height=44)
        self.openrouter_key = input_box("OpenRouter API key", multiline=False, size_hint_y=None, height=44)
        self.groq_model = input_box("Modelo Groq", multiline=False, size_hint_y=None, height=44)
        self.groq_key = input_box("Groq API key", multiline=False, size_hint_y=None, height=44)

        card.add_widget(self.openrouter_model)
        card.add_widget(self.openrouter_key)
        card.add_widget(self.groq_model)
        card.add_widget(self.groq_key)
        card.add_widget(button("Guardar ajustes", size_hint_y=None, height=48, bg=COLORS["success"], on_press=self.save))
        self.add_widget(card)
        self.load()

    def load(self):
        s = self.app_state.settings
        self.openrouter_model.text = s.get("openrouter_model")
        self.openrouter_key.text = s.get("openrouter_key")
        self.groq_model.text = s.get("groq_model")
        self.groq_key.text = s.get("groq_key")

    def set_provider(self, provider):
        self.app_state.settings.set("provider", provider)
        self.app_state.toast("Proveedor activo: " + provider)
        self.app_state.refresh_all()

    def save(self, *_):
        s = self.app_state.settings
        s.set("openrouter_model", self.openrouter_model.text.strip())
        s.set("openrouter_key", self.openrouter_key.text.strip())
        s.set("groq_model", self.groq_model.text.strip())
        s.set("groq_key", self.groq_key.text.strip())
        self.app_state.toast("Ajustes guardados")
