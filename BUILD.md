# Guía de build — Neox Admin

## Entorno (instalado en esta máquina)

| Herramienta | Ubicación |
|---|---|
| JDK 21 (Temurin) | `C:\Users\Cesar\Documents\Neox\tools\jdk-21` |
| Gradle 9.3.1 | `C:\Users\Cesar\Documents\Neox\tools\gradle-9.3.1` |
| Android SDK | `C:\Users\Cesar\Documents\Neox\tools\android-sdk` (platform 36.1, build-tools 36.1.0, platform-tools) |

El proyecto apunta al SDK vía `local.properties` (no se commitea).

## Compilar

Forma rápida (script incluido):

```cmd
build.cmd          :: APK debug
build.cmd release  :: APK release firmado
build.cmd clean    :: limpiar
```

Forma manual:

```bash
export JAVA_HOME="C:/Users/Cesar/Documents/Neox/tools/jdk-21"
export KEYSTORE_PATH="$PWD/my-upload-key.jks"
export STORE_PASSWORD=neoxadmin
export KEY_PASSWORD=neoxadmin
./gradlew.bat assembleDebug     # o assembleRelease
```

Los APK salen en:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## Firma

- **Debug**: `debug.keystore` (raíz del proyecto, password `android`). No se commitea.
- **Release**: `my-upload-key.jks` (raíz del proyecto).
  - Alias: `upload`
  - Passwords (store y key): `neoxadmin`
  - No se commitea (está en `.gitignore`). **Guárdalo** si planeas publicar en Play Store: todas las actualizaciones deben firmarse con esta misma clave.

## API key de Gemini (opcional)

Si la app necesita la API de Gemini, crea un archivo `.env` en la raíz con:

```
GEMINI_API_KEY=tu_clave
```

Sin él, el build usa los defaults de `.env.example` y compila igual (la clave no se empaqueta).

## Instalar en un dispositivo

```bash
C:/Users/Cesar/Documents/Neox/tools/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
