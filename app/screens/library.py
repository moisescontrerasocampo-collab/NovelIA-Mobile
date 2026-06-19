from kivy.uix.boxlayout import BoxLayout
from app.widgets.base import label, button
from app.theme import COLORS

class LibraryScreen(BoxLayout):
    def __init__(self, app_state, **kwargs):
        super().__init__(orientation="vertical", spacing=10, padding=16, **kwargs)
        self.app_state = app_state
        self.add_widget(label("Biblioteca", 31, True, size_hint_y=None, height=46))

        items = [
            ("🌌 Universo", "Universo/Universo.docx"),
            ("🏆 Hazañas", "Universo/Hazañas.docx"),
            ("🔗 Conexiones", "Universo/Conexiones.docx"),
        ]
        for title, rel in items:
            row = BoxLayout(size_hint_y=None, height=58, spacing=8)
            row.add_widget(button(title, on_press=lambda _, r=rel: app_state.open_relative_document(r)))
            row.add_widget(button("IA", size_hint_x=None, width=70, bg=COLORS["accent"], on_press=lambda _, t=title: app_state.open_ai(t)))
            self.add_widget(row)

        self.add_widget(label("Secciones internas de Universo", 18, True, size_hint_y=None, height=42))
        for title in ["🗺️ Geografía", "⚡ Sistema de Poderes", "🏛️ Historia de entidades", "🔮 Elementos de la historia"]:
            self.add_widget(button(title + " · editar con IA", size_hint_y=None, height=54, on_press=lambda _, t=title: app_state.open_ai(t)))
