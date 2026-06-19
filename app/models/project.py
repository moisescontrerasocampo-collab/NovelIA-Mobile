from dataclasses import dataclass, field
from pathlib import Path

@dataclass
class Project:
    name: str = "Sin proyecto"
    root: Path | None = None
    chapters: list = field(default_factory=list)
    characters: list = field(default_factory=list)

    @property
    def active(self) -> bool:
        return self.root is not None
