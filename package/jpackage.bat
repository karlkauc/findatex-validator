@echo off
REM Build a Windows .msi installer or portable app-image using jpackage.
REM Run after `mvn -DskipTests package`.
REM
REM Env overrides:
REM   APP_VERSION    Installer version (default: derived from the shaded jar
REM                  filename so it tracks pom.xml). Must be numeric.
REM   APP_VENDOR     Vendor (default "Karl Kauc").
REM   APP_NAME       Display name (default "FinDatEx Validator").
REM   PACKAGE_TYPE   "msi" (default) or "app-image" for the no-admin .zip path.
REM   JAVA_OPTIONS   JVM flags baked into the launcher (default:
REM                  "-Xms512m -Xmx8g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m").
REM
REM The shaded jar already contains JavaFX classes + native libraries, so we
REM only pass a curated JDK module list to jlink. No --module-path javafx.*.

setlocal EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
REM Resolve PROJECT_DIR to a canonical absolute path (no "..\") — JDK 21 jpackage
REM trips on parent-traversal segments in some path-normalisation paths and
REM crashes with "Cannot invoke java.nio.file.Path.getFileSystem() because path
REM is null". Going through pushd/popd gives us the resolved form.
pushd "%SCRIPT_DIR%.." >nul
set "PROJECT_DIR=%CD%"
popd >nul
set "TARGET_DIR=%PROJECT_DIR%\javafx-app\target"

if "%APP_NAME%"==""    set "APP_NAME=FinDatEx Validator"
if "%APP_VENDOR%"==""  set "APP_VENDOR=Karl Kauc"
if "%PACKAGE_TYPE%"=="" set "PACKAGE_TYPE=msi"
if "%JAVA_OPTIONS%"=="" set "JAVA_OPTIONS=-Xms512m -Xmx8g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m"

REM Debug switch: set WIN_CONSOLE=1 to build a launcher that opens a console
REM window so JVM startup errors / stack traces are visible. Default off
REM (regular GUI app — no console flicker on doubleclick).
set "CONSOLE_FLAG="
if "%WIN_CONSOLE%"=="1" set "CONSOLE_FLAG=--win-console"

REM Expand each space-separated flag into its own --java-options arg.
REM Hand-rolled tokenizer because plain "for %%O in (set)" splits on '=' as
REM well as whitespace, which mangles -XX:MaxMetaspaceSize=512m into two
REM broken options (-XX:MaxMetaspaceSize, 512m) and makes the JVM refuse to
REM start with "Improperly specified VM option". "for /f delims= " is
REM space-only and preserves the '=' inside option values.
set "JAVA_OPTION_ARGS="
set "JAVA_OPT_REMAIN=%JAVA_OPTIONS%"
:JAVA_OPT_LOOP
if "!JAVA_OPT_REMAIN!"=="" goto :JAVA_OPT_DONE
for /f "tokens=1* delims= " %%a in ("!JAVA_OPT_REMAIN!") do (
  set "JAVA_OPTION_ARGS=!JAVA_OPTION_ARGS! --java-options %%a"
  set "JAVA_OPT_REMAIN=%%b"
)
goto :JAVA_OPT_LOOP
:JAVA_OPT_DONE

REM Discover the shaded jar by glob — pom.xml's <version> is in the filename,
REM so this tracks bumps automatically without re-reading the pom.
set "SHADED_JAR="
set "SHADED_JAR_NAME="
for /f "usebackq delims=" %%F in (`dir /b /a-d "%TARGET_DIR%\findatex-validator-javafx-*-shaded.jar" 2^>nul`) do (
  set "SHADED_JAR=%TARGET_DIR%\%%F"
  set "SHADED_JAR_NAME=%%F"
)
if "!SHADED_JAR!"=="" (
  echo No findatex-validator-javafx-*-shaded.jar in %TARGET_DIR%
  echo Run "mvn -DskipTests package" first.
  exit /b 1
)

REM Default APP_VERSION from the jar name (findatex-validator-javafx-X.Y.Z-shaded.jar -> X.Y.Z).
if "%APP_VERSION%"=="" (
  set "TMP=!SHADED_JAR_NAME:findatex-validator-javafx-=!"
  set "APP_VERSION=!TMP:-shaded.jar=!"
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

REM JDK 24+ jpackage refactored BuildEnvBuilder requires --temp explicitly;
REM the auto-temp-dir path NPEs (BuildEnvBuilder.java:39, Objects.requireNonNull
REM on Path root). JDK 21 doesn't need this, but setting it is harmless on 21.
set "TEMP_DIR=%TARGET_DIR%\jpackage-temp"
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%"
mkdir "%TEMP_DIR%"

REM Build "--icon <path>" with a single set+quote pass; using
REM   set "ICON_ARG=--icon "%SCRIPT_DIR%icon.ico""
REM nests quotes inside the assignment value and on some cmd builds collapses
REM into an empty second arg that jpackage then dereferences as a null Path.
REM A literal SP-separated value with the path quoted ONCE avoids that.
set "ICON_ARG="
if exist "%SCRIPT_DIR%icon.ico" set ICON_ARG=--icon "%SCRIPT_DIR%icon.ico"

REM jdeps on JDKs with bundled JavaFX (Liberica Full, Azul FX, ...) hits split
REM packages between the shaded jar and the JDK's javafx.* modules and returns
REM a truncated module list missing java.logging / java.xml / java.management.
REM Result: jlink builds a slim runtime, JavaFX silently exits on first
REM Logger.getLogger(...) call. We always use the curated fallback to keep the
REM runtime self-contained — adds ~10 MB but the launcher actually works.
set "ADD_MODULES=java.base,java.desktop,java.naming,java.net.http,java.scripting,java.security.jgss,java.sql,java.xml,java.xml.crypto,java.logging,java.management,jdk.crypto.ec,jdk.jfr,jdk.unsupported"

echo Building %PACKAGE_TYPE% for Windows - version %APP_VERSION%, vendor "%APP_VENDOR%"
echo   add-modules:  !ADD_MODULES!
echo   java-options: %JAVA_OPTIONS%

REM --verbose left in so the next NPE / jlink miss prints a stack trace.
jpackage ^
  --type %PACKAGE_TYPE% ^
  --name "%APP_NAME%" ^
  --app-version %APP_VERSION% ^
  --vendor "%APP_VENDOR%" ^
  --description "FinDatEx data-template validator (TPT, EET, EMT, EPT)" ^
  --input "%INPUT_DIR%" ^
  --main-jar "!SHADED_JAR_NAME!" ^
  --main-class com.findatex.validator.AppLauncher ^
  --add-modules !ADD_MODULES! ^
  !JAVA_OPTION_ARGS! ^
  %ICON_ARG% ^
  %INSTALLER_FLAGS% ^
  %CONSOLE_FLAG% ^
  --temp "%TEMP_DIR%" ^
  --verbose ^
  --dest "%OUT_DIR%"

echo.
echo Output at %OUT_DIR%:
dir "%OUT_DIR%"
endlocal
