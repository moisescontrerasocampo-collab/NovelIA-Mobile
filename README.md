# NovelIA Kivy - Paso 6

Esta versión avanza el MVP hacia una app realmente utilizable.

## Nuevo en Paso 6

- Ajustes IA:
  - Simulado
  - OpenRouter
  - Groq
  - modelos configurables
  - API keys guardadas en `app/data/settings.json`

- Proyecto:
  - abrir/crear proyecto por ruta manual
  - crear carpetas
  - crear DOCX
  - explorador real
  - metadata `_proyecto.json`

- Capítulos:
  - crear capítulo con número y título
  - listar DOCX reales
  - abrir en editor
  - sincronizar RCG desde cada capítulo

- Personajes:
  - crear ficha DOCX desde nombre
  - plantilla base con encuesta, hazañas y conexiones
  - abrir ficha en editor

- Biblioteca:
  - Universo.docx
  - Hazañas.docx
  - Conexiones.docx
  - apertura y edición real

- Editor:
  - leer DOCX
  - guardar DOCX
  - sincronizar RCG desde documento abierto
  - panel IA aplicable

- RCG básico:
  - lee capítulo
  - pide JSON a IA si hay proveedor real configurado
  - si no hay IA real, usa análisis simulado/heurístico
  - crea resumen del capítulo
  - actualiza Universo.docx
  - actualiza Hazañas.docx
  - actualiza Conexiones.docx
  - actualiza fichas de personajes detectados

## Ejecutar

```bash
pip install -r requirements.txt
python main.py
```

## Flujo recomendado

1. Ejecuta `python main.py`
2. En Inicio pulsa `Crear/abrir proyecto demo`
3. Ve a Capítulos
4. Abre un capítulo
5. Pulsa `Sincronizar RCG`
6. Abre Biblioteca
7. Revisa Universo, Hazañas y Conexiones
8. Abre Personajes y revisa Max/Rang/Alicia
