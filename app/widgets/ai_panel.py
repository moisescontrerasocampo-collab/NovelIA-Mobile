from kivy.uix.boxlayout import BoxLayout
from kivy.clock import Clock
from app.widgets.base import label, button, input_box, Card
from app.theme import COLORS

class AIPanel(Card):
    def __init__(self, app_state, **kwargs):
        super().__init__(orientation="vertical", spacing=8, padding=12, bg_color=COLORS["panel2"], **kwargs)
        self.app_state = app_state
        self.add_widget(label("Asistente IA", 22, True, size_hint_y=None, height=34))
        self.context_label = label("Contexto: sin selección", 13, color=COLORS["accent2"], size_hint_y=None, height=44)
        self.add_widget(self.context_label)
        self.feed = label("IA: Puedo reescribir, resumir, extraer RCG o aplicar cambios.", 14)
        self.add_widget(self.feed)

        quick = BoxLayout(size_hint_y=None, height=42, spacing=6)
        quick.add_widget(button("Reescribir", on_press=lambda *_: self.quick("Reescribe con más fuerza narrativa.")))
        quick.add_widget(button("Resumir", on_press=lambda *_: self.quick("Resume este texto.")))
        quick.add_widget(button("RCG", on_press=lambda *_: self.quick("Extrae hazañas, conexiones y universo.")))
        self.add_widget(quick)

        self.prompt = input_box("Pregunta o instrucción...", multiline=False, size_hint_y=None, height=48)
        self.add_widget(self.prompt)

        row = BoxLayout(size_hint_y=None, height=50, spacing=8)
        row.add_widget(button("Enviar", bg=COLORS["accent"], on_press=self.send))
        row.add_widget(button("Aplicar", bg=COLORS["success"], on_press=lambda *_: self.apply()))
        self.add_widget(row)

    def set_context(self, text):
        self.context_label.text = f"Contexto: {text}"

    def quick(self, text):
        self.prompt.text = text
        self.send()

    def send(self, *_):
        prompt = self.prompt.text.strip()
        if not prompt:
            return
        self.prompt.text = ""
        self.feed.text = "Tú: " + prompt + "\n\nIA: generando..."

        def done(response):
            Clock.schedule_once(lambda dt: setattr(self.feed, "text", "Tú: " + prompt + "\n\nIA: " + response), 0)

        self.app_state.ai.ask_async(prompt, done)

    def apply(self):
        if self.app_state.editor_ref:
            self.app_state.editor_ref.insert_ai_text("\n\n[IA aplicada] Texto propuesto desde el asistente contextual.")
        self.app_state.toast("Cambio aplicado al editor")
