from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.screenmanager import ScreenManager, Screen
from kivy.uix.popup import Popup
from kivy.core.window import Window
from kivy.uix.label import Label

from app.theme import COLORS
from app.services.settings_service import SettingsService
from app.services.project_manager import ProjectManager
from app.services.docx_service import DocxService
from app.services.ai_service import AIService
from app.services.rcg_service import RCGService

from app.screens.home import HomeScreen
from app.screens.chat import ChatScreen
from app.screens.chapters import ChaptersScreen
from app.screens.characters import CharactersScreen
from app.screens.library import LibraryScreen
from app.screens.project import ProjectScreen
from app.screens.settings import SettingsScreen
from app.screens.editor import EditorScreen
from app.widgets.ai_panel import AIPanel
from app.widgets.base import button, label, Card

class AppState:
    def __init__(self, app):
        self.app = app
        self.settings = SettingsService()
        self.project_manager = ProjectManager()
        self.docx = DocxService()
        self.ai = AIService(self.settings)
        self.rcg = RCGService(self.project_manager, self.ai, self.docx)
        self.editor_ref = None

    def switch(self, key):
        self.app.shell.switch(key)

    def refresh_all(self):
        self.app.shell.refresh_all()

    def create_demo(self):
        self.project_manager.demo_project()
        self.toast("Proyecto demo listo")
        self.refresh_all()

    def open_editor_title(self, title):
        self.app.editor.open_title(title)
        self.editor_ref = self.app.editor
        self.app.manager.current = "editor"

    def open_document(self, path):
        self.app.editor.open_path(path)
        self.editor_ref = self.app.editor
        self.app.manager.current = "editor"

    def open_relative_document(self, rel):
        path = self.project_manager.get_path(rel)
        if not path:
            self.toast("No hay proyecto activo")
            return
        self.open_document(path)

    def close_editor(self):
        self.app.manager.current = "main"
        self.refresh_all()

    def open_ai(self, context):
        self.app.shell.show_ai(context)

    def sync_current_document(self):
        if not self.editor_ref or not self.editor_ref.current_path:
            self.toast("No hay DOCX abierto para sincronizar")
            return
        self.sync_chapter(self.editor_ref.current_path)

    def open_sync_current(self):
        self.toast("Abre un capítulo y pulsa Sincronizar RCG para actualizar DOCX reales.")

    def sync_chapter(self, path):
        ok, msg = self.rcg.sync_chapter(path)
        self.toast(msg)
        self.refresh_all()

    def toast(self, text):
        self.app.toast(text)

class NovelIAApp(App):
    title = "NovelIA Mobile - Paso 6"

    def build(self):
        Window.clearcolor = (0.04, 0.06, 0.13, 1)
        self.state = AppState(self)

        self.manager = ScreenManager()
        main = Screen(name="main")
        self.shell = ResponsiveShell(self.state)
        main.add_widget(self.shell)

        editor = Screen(name="editor")
        self.editor = EditorScreen(self.state)
        self.state.editor_ref = self.editor
        editor.add_widget(self.editor)

        self.manager.add_widget(main)
        self.manager.add_widget(editor)
        return self.manager

    def toast(self, text):
        popup = Popup(title="NovelIA", content=Label(text=text), size_hint=(0.86, 0.25))
        popup.open()

class ResponsiveShell(BoxLayout):
    def __init__(self, app_state, **kwargs):
        super().__init__(orientation="vertical", **kwargs)
        self.app_state = app_state
        self.current_name = "home"
        self.ai_visible = False

        self.body = BoxLayout()
        self.add_widget(self.body)

        self.navbar = BoxLayout(size_hint_y=None, height=70, spacing=4, padding=4)
        self.add_widget(self.navbar)

        self.screens = {
            "home": HomeScreen(app_state),
            "chat": ChatScreen(app_state),
            "chapters": ChaptersScreen(app_state),
            "characters": CharactersScreen(app_state),
            "library": LibraryScreen(app_state),
            "project": ProjectScreen(app_state),
            "settings": SettingsScreen(app_state),
        }
        self.ai_panel = AIPanel(app_state)
        self.bind(size=lambda *_: self.rebuild())
        self.rebuild()

    def is_landscape_or_tablet(self):
        return self.width > self.height or self.width >= 900

    def rebuild(self):
        self.body.clear_widgets()
        self.navbar.clear_widgets()
        landscape = self.is_landscape_or_tablet()

        if landscape:
            self.orientation = "horizontal"
            if self.navbar.parent:
                self.remove_widget(self.navbar)

            side = Card(orientation="vertical", size_hint_x=None, width=220, spacing=7, padding=10, bg_color=COLORS["panel"])
            side.add_widget(label("NovelIA", 25, True, size_hint_y=None, height=48))
            self._fill_nav(side)
            self.body.add_widget(side)
            self.body.add_widget(self.screens[self.current_name])
            if self.ai_visible:
                self.body.add_widget(self.ai_panel)
        else:
            self.orientation = "vertical"
            if not self.navbar.parent:
                self.add_widget(self.navbar)
            self.body.add_widget(self.ai_panel if self.ai_visible else self.screens[self.current_name])
            self._fill_nav(self.navbar)

    def _fill_nav(self, container):
        items = [
            ("home", "🏠\nInicio"),
            ("chat", "💬\nChat"),
            ("chapters", "📖\nCap."),
            ("characters", "👤\nPers."),
            ("library", "📚\nBibl."),
            ("project", "⚙️\nProy."),
            ("settings", "🔑\nIA"),
        ]
        for key, text in items:
            bg = COLORS["accent"] if key == self.current_name else COLORS["card"]
            container.add_widget(button(text, bg=bg, on_press=lambda _, k=key: self.switch(k)))

    def switch(self, key):
        self.current_name = key
        self.ai_visible = False
        self.rebuild()
        self.refresh_all()

    def refresh_all(self):
        for screen in self.screens.values():
            if hasattr(screen, "refresh"):
                screen.refresh()

    def show_ai(self, context):
        self.ai_visible = True
        self.ai_panel.set_context(context)
        self.rebuild()
