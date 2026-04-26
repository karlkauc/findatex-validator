@echo off
REM Build a Windows .msi installer using jpackage.
REM Run after `mvn -DskipTests package`.

setlocal
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
set "TARGET_DIR=%PROJECT_DIR%\target"
set "APP_NAME=TPT Validator"
set "APP_VERSION=1.0.0"
set "SHADED_JAR=%TARGET_DIR%\tpt-validator-%APP_VERSION%-shaded.jar"

if not exist "%SHADED_JAR%" (
  echo Shaded JAR not found: %SHADED_JAR%
  echo Run "mvn -DskipTests package" first.
  exit /b 1
)

where jpackage >nul 2>nul
if errorlevel 1 (
  echo jpackage not found on PATH ^(needs JDK 17+^).
  exit /b 2
)

set "OUT_DIR=%TARGET_DIR%\installer"
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"

set "INPUT_DIR=%TARGET_DIR%\jpackage-input"
if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
mkdir "%INPUT_DIR%"
copy /Y "%SHADED_JAR%" "%INPUT_DIR%\" >nul

set "ICON_ARG="
if exist "%SCRIPT_DIR%icon.ico" set "ICON_ARG=--icon "%SCRIPT_DIR%icon.ico""

jpackage ^
  --type msi ^
  --name "%APP_NAME%" ^
  --app-version %APP_VERSION% ^
  --vendor "TPT Validator" ^
  --description "Quality and conformance validator for TPT V7 files" ^
  --input "%INPUT_DIR%" ^
  --main-jar "tpt-validator-%APP_VERSION%-shaded.jar" ^
  --main-class com.tpt.validator.AppLauncher ^
  %ICON_ARG% ^
  --dest "%OUT_DIR%" ^
  --win-shortcut --win-menu

echo.
echo Installer artefacts at %OUT_DIR%:
dir "%OUT_DIR%"
endlocal
