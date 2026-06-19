from kivy.uix.boxlayout import BoxLayout
from kivy.uix.scrollview import ScrollView
from kivy.clock import Clock
from app.widgets.base import label, button, input_box, Card
from app.theme import COLORS

class ChatScreen(BoxLayout):
    def __init__(self, app_state, **kwargs):
        super().__init__(orientation="vertical", spacing=10, padding=16, **kwargs)
        self.app_state = app_state
        self.add_widget(label("Chat IA", 31, True, size_hint_y=None, height=46))

        row = BoxLayout(size_hint_y=None, height=42, spacing=6)
        self.provider_label = label("Proveedor: " + app_state.settings.get("provider", "Simulado"), 13, color=COLORS["accent2"])
        row.add_widget(self.provider_label)
        row.add_widget(button("Ajustes", size_hint_x=None, width=92, on_press=lambda *_: app_state.switch("settings")))
        self.add_widget(row)

        self.feed = BoxLayout(orientation="vertical", spacing=8, size_hint_y=None)
        self.feed.bind(minimum_height=self.feed.setter("height"))
        sv = ScrollView()
        sv.add_widget(self.feed)
        self.add_widget(sv)
        self.add_message("IA", "Hola. Estoy listo para trabajar con tus DOCX, capítulos, personajes y biblioteca.")

        composer = BoxLayout(size_hint_y=None, height=60, spacing=8)
        self.prompt = input_box("Escribe a la IA...", multiline=False)
        composer.add_widget(self.prompt)
        composer.add_widget(button("Enviar", size_hint_x=None, width=92, bg=COLORS["accent"], on_press=self.send))
        self.add_widget(composer)

    def refresh(self):
        self.provider_label.text = "Proveedor: " + self.app_state.settings.get("provider", "Simulado")

    def add_message(self, who, text):
        card = Card(orientation="vertical", size_hint_y=None, height=max(64, 44 + len(text)//2), bg_color=COLORS["card"] if who == "IA" else COLORS["accent"])
        card.add_widget(label(f"[b]{who}[/b]\n{text}", 14))
        self.feed.add_widget(card)

    def send(self, *_):
        text = self.prompt.text.strip()
        if not text:
            return
        self.prompt.text = ""
        self.add_message("Tú", text)

        def done(response):
            Clock.schedule_once(lambda dt: self.add_message("IA", response), 0)

        self.app_state.ai.ask_async(text, done)
