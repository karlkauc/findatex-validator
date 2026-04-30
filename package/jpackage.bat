@echo off
REM Build a Windows .msi installer or portable app-image using jpackage.
REM Run after `mvn -DskipTests package`.
REM
REM Env overrides:
REM   APP_VERSION    Installer version (default 1.0.0). Must be numeric.
REM   APP_VENDOR     Vendor (default "Karl Kauc").
REM   APP_NAME       Display name (default "FinDatEx Validator").
REM   PACKAGE_TYPE   "msi" (default) or "app-image" for the no-admin .zip path.
REM   JAVA_OPTIONS   JVM flags baked into the launcher (default:
REM                  "-Xms512m -Xmx8g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m").
REM
REM The shaded jar already contains JavaFX classes + native libraries, so we
REM only enumerate JDK modules via jdeps for jlink. No --module-path javafx.*.

setlocal EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
set "TARGET_DIR=%PROJECT_DIR%\javafx-app\target"

if "%APP_NAME%"==""    set "APP_NAME=FinDatEx Validator"
if "%APP_VERSION%"=="" set "APP_VERSION=1.0.0"
if "%APP_VENDOR%"==""  set "APP_VENDOR=Karl Kauc"
if "%PACKAGE_TYPE%"=="" set "PACKAGE_TYPE=msi"
if "%JAVA_OPTIONS%"=="" set "JAVA_OPTIONS=-Xms512m -Xmx8g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m"

REM Expand each space-separated flag into its own --java-options arg.
set "JAVA_OPTION_ARGS="
for %%O in (%JAVA_OPTIONS%) do set "JAVA_OPTION_ARGS=!JAVA_OPTION_ARGS! --java-options %%O"

set "SHADED_JAR=%TARGET_DIR%\findatex-validator-javafx-1.0.0-shaded.jar"

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

if /I "%PACKAGE_TYPE%"=="app-image" (
  set "OUT_DIR=%TARGET_DIR%\portable"
  set "INSTALLER_FLAGS="
) else (
  set "OUT_DIR=%TARGET_DIR%\installer"
  set "INSTALLER_FLAGS=--win-shortcut --win-menu"
)

if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"

set "INPUT_DIR=%TARGET_DIR%\jpackage-input"
if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
mkdir "%INPUT_DIR%"
copy /Y "%SHADED_JAR%" "%INPUT_DIR%\" >nul

set "ICON_ARG="
if exist "%SCRIPT_DIR%icon.ico" set "ICON_ARG=--icon "%SCRIPT_DIR%icon.ico""

set "ADD_MODULES="
for /f "usebackq delims=" %%M in (`jdeps --multi-release 21 --ignore-missing-deps --print-module-deps "%SHADED_JAR%" 2^>nul`) do set "ADD_MODULES=%%M"
if "!ADD_MODULES!"=="" set "ADD_MODULES=java.base,java.desktop,java.naming,java.net.http,java.scripting,java.security.jgss,java.sql,java.xml,java.xml.crypto,java.logging,java.management,jdk.crypto.ec,jdk.jfr,jdk.unsupported"

echo Building %PACKAGE_TYPE% for Windows - version %APP_VERSION%, vendor "%APP_VENDOR%"
echo   add-modules:  !ADD_MODULES!
echo   java-options: %JAVA_OPTIONS%

jpackage ^
  --type %PACKAGE_TYPE% ^
  --name "%APP_NAME%" ^
  --app-version %APP_VERSION% ^
  --vendor "%APP_VENDOR%" ^
  --description "FinDatEx data-template validator (TPT, EET, EMT, EPT)" ^
  --input "%INPUT_DIR%" ^
  --main-jar "findatex-validator-javafx-1.0.0-shaded.jar" ^
  --main-class com.findatex.validator.AppLauncher ^
  --add-modules !ADD_MODULES! ^
  !JAVA_OPTION_ARGS! ^
  %ICON_ARG% ^
  %INSTALLER_FLAGS% ^
  --dest "%OUT_DIR%"

echo.
echo Output at %OUT_DIR%:
dir "%OUT_DIR%"
endlocal
