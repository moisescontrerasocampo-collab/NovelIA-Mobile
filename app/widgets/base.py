from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.graphics import Color, RoundedRectangle
from kivy.properties import ListProperty, NumericProperty
from app.theme import COLORS, hex_to_rgba

class Card(BoxLayout):
    bg_color = ListProperty(COLORS["card"])
    radius = NumericProperty(22)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.padding = kwargs.get("padding", 14)
        self.spacing = kwargs.get("spacing", 8)
        with self.canvas.before:
            self._color = Color(*self.bg_color)
            self._rect = RoundedRectangle(pos=self.pos, size=self.size, radius=[self.radius])
        self.bind(pos=self._update, size=self._update, bg_color=self._update_color)

    def _update(self, *_):
        self._rect.pos = self.pos
        self._rect.size = self.size
        self._rect.radius = [self.radius]

    def _update_color(self, *_):
        self._color.rgba = self.bg_color

class NLabel(Label):
    def __init__(self, text="", size=15, bold=False, color=None, **kwargs):
        super().__init__(
            text=text,
            font_size=size,
            bold=bold,
            color=color or COLORS["text"],
            halign="left",
            valign="middle",
            markup=True,
            **kwargs
        )
        self.bind(size=lambda *_: setattr(self, "text_size", self.size))

class NButton(Button):
    def __init__(self, text="", bg=None, **kwargs):
        super().__init__(
            text=text,
            background_normal="",
            background_down="",
            background_color=bg or COLORS["card"],
            color=COLORS["text"],
            font_size=14,
            bold=True,
            **kwargs
        )

class NInput(TextInput):
    def __init__(self, hint="", multiline=False, **kwargs):
        super().__init__(
            hint_text=hint,
            multiline=multiline,
            background_color=hex_to_rgba("#0e1530"),
            foreground_color=COLORS["text"],
            cursor_color=COLORS["accent"],
            hint_text_color=COLORS["muted"],
            padding=(12, 12),
            **kwargs
        )

def label(text, size=15, bold=False, color=None, **kwargs):
    return NLabel(text=text, size=size, bold=bold, color=color, **kwargs)

def button(text, bg=None, **kwargs):
    return NButton(text=text, bg=bg, **kwargs)

def input_box(hint="", multiline=False, **kwargs):
    return NInput(hint=hint, multiline=multiline, **kwargs)
