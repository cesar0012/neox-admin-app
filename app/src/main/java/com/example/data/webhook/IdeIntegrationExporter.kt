package com.example.data.webhook

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object IdeIntegrationExporter {

    fun generateIntegrationMarkdown(context: Context, phoneIp: String, port: Int = 8765): File {
        val endpoint = "http://$phoneIp:$port"
        val content = """
# Guía Maestra de Integración de Editores con OmniWork

Conecta tus entornos de desarrollo de escritorio (**VS Code, Cursor, Cloud Code, Neovim o scripts de terminal**) directamente con tu asistente **OmniWork** en tu teléfono celular a través de la red local Wi-Fi.

---

## 1. Datos de Conexión en Red Wi-Fi
- **Host Endpoint:** `$endpoint`
- **Endpoint para Tareas:** `$endpoint/webhook/task`
- **Endpoint para Contexto / Código:** `$endpoint/webhook/context`
- **Estado del Asistente:** `$endpoint/status`

> **Requisito:** Tu computadora y tu teléfono deben estar conectados a la misma red Wi-Fi.

---

## 2. Configuración en Visual Studio Code

Agrega una tarea personalizada en tu proyecto dentro de `.vscode/tasks.json`:

```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "Enviar Instrucción a OmniWork",
      "type": "shell",
      "command": "curl",
      "args": [
        "-X", "POST",
        "$endpoint/webhook/task",
        "-H", "Content-Type: application/json",
        "-d", "{\"title\": \"Revisar código actual\", \"description\": \"${'$'}{selectedText}\", \"jobTag\": \"Dev\", \"source\": \"VS Code Task\"}"
      ],
      "problemMatcher": []
    }
  ]
}
```

Para asociarlo a un atajo de teclado en `keybindings.json`:
```json
{
  "key": "ctrl+alt+o",
  "command": "workbench.action.tasks.runTask",
  "args": "Enviar Instrucción a OmniWork"
}
```

---

## 3. Configuración en Cursor (`.cursorrules`)

Crea o edita el archivo `.cursorrules` en la raíz de tu proyecto para que Cursor notifique a tu celular:

```markdown
# Integración con OmniWork Assistant
Siempre que finalices un cambio de código importante o una refactorización, ejecuta un webhook silencioso a OmniWork:
curl -s -X POST $endpoint/webhook/context \
  -H "Content-Type: application/json" \
  -d '{"title": "Cursor: Refactor completado", "content": "Se actualizaron los archivos del proyecto", "jobTag": "Dev", "source": "Cursor AI"}' > /dev/null 2>&1
```

---

## 4. Google Cloud Code / Git Hook (Automático al hacer Commit)

En tu repositorio de Git, crea el archivo `.git/hooks/post-commit`:

```bash
#!/bin/bash
LAST_COMMIT=$(git log -1 --pretty=format:"%s (%an)")
curl -s -X POST "$endpoint/webhook/task" \
  -H "Content-Type: application/json" \
  -d "{\"title\": \"Commit: ${'$'}LAST_COMMIT\", \"jobTag\": \"Dev\", \"source\": \"Git Post-Commit\"}" > /dev/null 2>&1
```
Recuerda darle permisos de ejecución: `chmod +x .git/hooks/post-commit`

---

## 5. Script Rápido en Python para Sincronizar el Portapapeles

```python
import urllib.request
import json
import tkinter as tk

# Obtener portapapeles
root = tk.Tk()
root.withdraw()
clipboard_text = root.clipboard_get()

payload = {
    "title": "Portapapeles de PC",
    "content": clipboard_text,
    "jobTag": "Dev",
    "source": "Python Script"
}

req = urllib.request.Request(
    "$endpoint/webhook/context",
    data=json.dumps(payload).encode('utf-8'),
    headers={"Content-Type": "application/json"}
)

try:
    with urllib.request.urlopen(req) as response:
        print("Sincronizado con OmniWork exitosamente!")
except Exception as e:
    print("Error conectando con el celular en Wi-Fi:", e)
```

---

## 6. Verificación Manual por Terminal (cURL)

```bash
curl -X POST $endpoint/webhook/task \
  -H "Content-Type: application/json" \
  -d '{"title": "Prueba de Integración Wi-Fi", "description": "Mensaje enviado desde terminal", "jobTag": "Dev", "priority": "ALTA"}'
```
""".trimIndent()

        val docFile = File(context.cacheDir, "OMNIWORK_INTEGRATION.md")
        docFile.writeText(content, Charsets.UTF_8)
        return docFile
    }

    fun shareIntegrationDoc(context: Context, docFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", docFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Guía de Integración OmniWork (Markdown)")
            putExtra(Intent.EXTRA_TEXT, "Guía completa de integración para VS Code, Cloud Code, Cursor y Terminal.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir OMNIWORK_INTEGRATION.md"))
    }
}
