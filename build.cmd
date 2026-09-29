@echo off
rem ============================================================
rem  Neox Admin - Build helper
rem  Uso:
rem    build.cmd            -> compila APK debug
rem    build.cmd debug      -> compila APK debug
rem    build.cmd release    -> compila APK release (firmado con my-upload-key.jks)
rem    build.cmd clean      -> limpia el proyecto
rem ============================================================
setlocal

set JAVA_HOME=C:\Users\Cesar\Documents\Neox\tools\jdk-21
set KEYSTORE_PATH=%~dp0my-upload-key.jks
set STORE_PASSWORD=neoxadmin
set KEY_PASSWORD=neoxadmin

set TASK=%1
if "%TASK%"=="" set TASK=debug

if /I "%TASK%"=="debug" (
  call gradlew.bat assembleDebug
) else if /I "%TASK%"=="release" (
  call gradlew.bat assembleRelease
) else if /I "%TASK%"=="clean" (
  call gradlew.bat clean
) else (
  echo Opcion desconocida: %TASK%  (usa debug, release o clean)
  exit /b 1
)

if %ERRORLEVEL%==0 (
  echo.
  echo APK listo en: app\build\outputs\apk\
)
endlocal
