package com.example.data.webhook

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ChromeExtensionExporter {

    fun generateExtensionZip(context: Context, phoneIp: String, port: Int = 8765): File {
        val exportDir = File(context.cacheDir, "omniwork_extension")
        if (exportDir.exists()) exportDir.deleteRecursively()
        exportDir.mkdirs()

        val manifestContent = """
{
  "manifest_version": 3,
  "name": "OmniWork Bridge — Chrome Sync",
  "version": "1.0.0",
  "description": "Sincroniza tus páginas, instrucciones y contexto de código directamente con tu asistente OmniWork en tu celular por Wi-Fi.",
  "permissions": ["activeTab", "scripting", "storage", "contextMenus"],
  "host_permissions": ["http://*/*", "https://*/*"],
  "action": {
    "default_popup": "popup.html",
    "default_title": "OmniWork Assistant Sync"
  },
  "background": {
    "service_worker": "background.js"
  }
}
""".trimIndent()

        val popupHtml = """
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>OmniWork Bridge</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      width: 340px;
      margin: 0;
      padding: 16px;
      background: #0f172a;
      color: #f8fafc;
    }
    h2 { font-size: 16px; margin: 0 0 12px 0; color: #38bdf8; display: flex; align-items: center; gap: 8px; }
    label { font-size: 11px; text-transform: uppercase; color: #94a3b8; font-weight: bold; margin-bottom: 4px; display: block; }
    input, textarea, select {
      width: 100%;
      box-sizing: border-box;
      padding: 8px 10px;
      margin-bottom: 10px;
      background: #1e293b;
      border: 1px solid #334155;
      color: #f1f5f9;
      border-radius: 6px;
      font-size: 13px;
    }
    button {
      width: 100%;
      padding: 10px;
      background: #0284c7;
      color: white;
      border: none;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
      margin-bottom: 8px;
    }
    button:hover { background: #0369a1; }
    button.secondary { background: #334155; }
    button.secondary:hover { background: #475569; }
    #status { font-size: 12px; margin-top: 8px; min-height: 18px; text-align: center; }
    .status-ok { color: #4ade80; }
    .status-err { color: #f87171; }
    .badge { background: #0369a1; font-size: 10px; padding: 2px 6px; border-radius: 4px; }
  </style>
</head>
<body>
  <h2><span>⚡</span> OmniWork Bridge <span class="badge">Wi-Fi</span></h2>
  
  <label for="phoneIp">IP del Celular (Puerto $port)</label>
  <input type="text" id="phoneIp" value="http://$phoneIp:$port" placeholder="http://192.168.x.x:$port">

  <label for="jobTag">Proyecto / Trabajo</label>
  <select id="jobTag">
    <option value="General">General</option>
    <option value="Trabajo 1">Trabajo 1</option>
    <option value="Trabajo 2">Trabajo 2</option>
    <option value="Dev Freelance">Dev Freelance</option>
  </select>

  <label for="taskTitle">Título / Instrucción</label>
  <input type="text" id="taskTitle" placeholder="Ej: Revisar PR de Cloud Code">

  <label for="taskDesc">Detalle / Código / Contexto</label>
  <textarea id="taskDesc" rows="3" placeholder="Pega aquí código, instrucciones o notas..."></textarea>

  <button id="btnSendTask">🚀 Enviar a OmniWork (Celular)</button>
  <button id="btnSendTab" class="secondary">📄 Enviar Pestaña Actual</button>

  <div id="status"></div>

  <script src="popup.js"></script>
</body>
</html>
""".trimIndent()

        val popupJs = """
document.addEventListener('DOMContentLoaded', () => {
  const ipInput = document.getElementById('phoneIp');
  const jobTag = document.getElementById('jobTag');
  const taskTitle = document.getElementById('taskTitle');
  const taskDesc = document.getElementById('taskDesc');
  const statusDiv = document.getElementById('status');
  const btnSendTask = document.getElementById('btnSendTask');
  const btnSendTab = document.getElementById('btnSendTab');

  chrome.storage.local.get(['phoneIp'], (res) => {
    if (res.phoneIp) ipInput.value = res.phoneIp;
  });

  function showStatus(msg, isError = false) {
    statusDiv.textContent = msg;
    statusDiv.className = isError ? 'status-err' : 'status-ok';
    setTimeout(() => { statusDiv.textContent = ''; }, 4000);
  }

  btnSendTask.addEventListener('click', async () => {
    const base = ipInput.value.trim().replace(/\/+$/, '');
    chrome.storage.local.set({ phoneIp: base });
    const title = taskTitle.value.trim();
    if (!title) { showStatus('Escribe un título', true); return; }

    try {
      showStatus('Enviando...');
      const res = await fetch(base + '/webhook/task', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: title,
          description: taskDesc.value.trim(),
          jobTag: jobTag.value,
          source: 'Chrome Extension'
        })
      });
      if (res.ok) {
        showStatus('¡Enviado a OmniWork con éxito!');
        taskTitle.value = '';
        taskDesc.value = '';
      } else {
        showStatus('Error de respuesta: ' + res.status, true);
      }
    } catch (e) {
      showStatus('No se pudo conectar al celular en Wi-Fi', true);
    }
  });

  btnSendTab.addEventListener('click', async () => {
    const base = ipInput.value.trim().replace(/\/+$/, '');
    chrome.storage.local.set({ phoneIp: base });
    
    chrome.tabs.query({ active: true, currentWindow: true }, async (tabs) => {
      if (!tabs || !tabs[0]) return;
      const tab = tabs[0];
      try {
        showStatus('Sincronizando pestaña...');
        const res = await fetch(base + '/webhook/context', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            title: 'Navegación: ' + tab.title,
            content: 'URL: ' + tab.url + '\n' + (tab.favIconUrl || ''),
            jobTag: jobTag.value,
            source: 'Chrome Extension ActiveTab'
          })
        });
        if (res.ok) showStatus('¡Pestaña guardada en la bóveda!');
        else showStatus('Error al enviar: ' + res.status, true);
      } catch (e) {
        showStatus('Error de conexión Wi-Fi', true);
      }
    });
  });
});
""".trimIndent()

        val backgroundJs = """
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: "send-to-omniwork",
    title: "⚡ Enviar texto seleccionado a OmniWork",
    contexts: ["selection"]
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === "send-to-omniwork" && info.selectionText) {
    chrome.storage.local.get(['phoneIp'], async (res) => {
      const base = (res.phoneIp || "http://$phoneIp:$port").replace(/\/+$/, '');
      try {
        await fetch(base + '/webhook/context', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            title: 'Texto seleccionado de: ' + (tab ? tab.title : 'Web'),
            content: info.selectionText,
            jobTag: 'General',
            source: 'Chrome ContextMenu'
          })
        });
      } catch (e) {
        console.error('Failed to sync to OmniWork phone', e);
      }
    });
  }
});
""".trimIndent()

        val readme = """
# OmniWork Chrome Extension (Wi-Fi Bridge)

Esta extensión conecta tu navegador en la computadora directamente con la aplicación Android **OmniWork** a través de la misma red local Wi-Fi.

## Pasos para instalar en Google Chrome / Brave / Edge:
1. Descomprime este archivo ZIP en una carpeta de tu computadora.
2. Abre tu navegador y dirígete a: `chrome://extensions/`
3. Activa la casilla **Modo de desarrollador** (Developer Mode) en la esquina superior derecha.
4. Haz clic en el botón **Cargar descomprimida** (Load unpacked) y selecciona la carpeta que acabas de descomprimir.
5. ¡Listo! Verás el icono de OmniWork en la barra de extensiones.
6. Asegúrate de que tu celular y tu computadora estén conectados a la misma red Wi-Fi.
7. La IP configurada por defecto es: `http://$phoneIp:$port`.
""".trimIndent()

        // Create zip
        val zipFile = File(context.cacheDir, "omniwork_chrome_extension.zip")
        if (zipFile.exists()) zipFile.delete()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            addZipEntry(zos, "manifest.json", manifestContent)
            addZipEntry(zos, "popup.html", popupHtml)
            addZipEntry(zos, "popup.js", popupJs)
            addZipEntry(zos, "background.js", backgroundJs)
            addZipEntry(zos, "README.md", readme)
        }

        return zipFile
    }

    private fun addZipEntry(zos: ZipOutputStream, fileName: String, content: String) {
        val entry = ZipEntry(fileName)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    fun shareExtensionZip(context: Context, zipFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "OmniWork Chrome Extension")
            putExtra(Intent.EXTRA_TEXT, "Extensión de Chrome para sincronizar con OmniWork en la red local Wi-Fi.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Exportar extensión de Chrome"))
    }
}
