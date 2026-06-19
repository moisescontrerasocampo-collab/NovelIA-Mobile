def hex_to_rgba(hex_color: str, alpha: float = 1.0):
    hex_color = hex_color.strip().replace("#", "")
    return (
        int(hex_color[0:2], 16) / 255,
        int(hex_color[2:4], 16) / 255,
        int(hex_color[4:6], 16) / 255,
        alpha,
    )

PALETTE = {
    "bg": "#0b1020",
    "bg2": "#121a38",
    "panel": "#111936",
    "panel2": "#172247",
    "card": "#1b2854",
    "card2": "#22336c",
    "soft": "#263466",
    "text": "#eef4ff",
    "muted": "#9fb0d8",
    "accent": "#e94560",
    "accent2": "#a8dadc",
    "success": "#57cc99",
    "warning": "#ffd166",
    "danger": "#ff6f91",
}
COLORS = {k: hex_to_rgba(v) for k, v in PALETTE.items()}
TABLET_BREAKPOINT = 900
