@echo off
REM Build a Windows .msi installer or portable app-image using jpackage.
REM Run after `mvn -DskipTests package`.
REM
REM Pipeline (mirrors jpackage.sh):
REM   1. Build intermediate app-image (jlink runtime + launcher).
REM   2. Copy bin\java.exe from %JAVA_HOME% into the bundled runtime so the
REM      AOT-cache child-process step can spawn it (jpackage strips bin/java
REM      from the runtime by default).
REM   3. Patch the launcher .cfg with -Dfindatex.training and the archive
REM      output flag, run the launcher (which auto-exits via App.java's
REM      training hook), revert the .cfg.
REM   4. Re-patch the .cfg with -XX:AOTCache / -XX:SharedArchiveFile pointing
REM      at the produced archive so user runs pick it up.
REM   5. Wrap into installer (or hand back the augmented app-image).
REM
REM Env overrides:
REM   APP_VERSION    Installer version (default: derived from the shaded jar
REM                  filename so it tracks pom.xml). Must be numeric.
REM   APP_VENDOR     Vendor (default "Karl Kauc").
REM   APP_NAME       Display name (default "FinDatEx Validator").
REM   PACKAGE_TYPE   "msi" (default) or "app-image" for the no-admin .zip path.
REM   JAVA_OPTIONS   JVM flags baked into the launcher (default:
REM                  "-Xms128m -Xmx8g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m
REM                   -XX:TieredStopAtLevel=1").
REM   ENABLE_SPLASH  1 (default) → -splash:%%APPDIR%%\splash.png baked in.
REM   ENABLE_CDS     1 (default) → dynamic-CDS archive (app.jsa).
REM   ENABLE_AOT     1 (default if JDK >= 24) → AOT cache (app.aot).
REM                  Mutually exclusive with ENABLE_CDS.
REM   TRAINING_MS    Stage.show()-to-exit delay during training (default 2500).
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
if "%JAVA_OPTIONS%"=="" set "JAVA_OPTIONS=-Xms128m -Xmx8g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m -XX:TieredStopAtLevel=1"
if "%TRAINING_MS%"=="" set "TRAINING_MS=2500"
if "%ENABLE_SPLASH%"=="" set "ENABLE_SPLASH=1"
if "%ENABLE_CDS%"=="" set "ENABLE_CDS=1"

REM Detect JDK feature version → drives ENABLE_AOT default.
set "JDK_FEATURE=0"
for /f "tokens=3 delims=. " %%v in ('java -version 2^>^&1 ^| findstr /c:"version"') do (
  set "JDK_FEATURE=%%v"
  goto :GOT_JDK
)
:GOT_JDK
REM Strip leading-quote artifacts from the parsed version.
set "JDK_FEATURE=%JDK_FEATURE:"=%"
if "%ENABLE_AOT%"=="" (
  if !JDK_FEATURE! GEQ 24 ( set "ENABLE_AOT=1" ) else ( set "ENABLE_AOT=0" )
)
if "%ENABLE_AOT%"=="1" set "ENABLE_CDS=0"
if "%ENABLE_AOT%"=="1" if !JDK_FEATURE! LSS 24 (
  echo ENABLE_AOT=1 requires JDK 24+, but local JDK is !JDK_FEATURE! - disabling AOT.
  set "ENABLE_AOT=0"
)

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
set "STAGE1_OPTS="
set "JAVA_OPT_REMAIN=%JAVA_OPTIONS%"
:JAVA_OPT_LOOP
if "!JAVA_OPT_REMAIN!"=="" goto :JAVA_OPT_DONE
for /f "tokens=1* delims= " %%a in ("!JAVA_OPT_REMAIN!") do (
  set "STAGE1_OPTS=!STAGE1_OPTS! --java-options %%a"
  set "JAVA_OPT_REMAIN=%%b"
)
goto :JAVA_OPT_LOOP
:JAVA_OPT_DONE

if "%ENABLE_SPLASH%"=="1" if exist "%SCRIPT_DIR%splash.png" (
  set "STAGE1_OPTS=!STAGE1_OPTS! --java-options -splash:$APPDIR\splash.png"
)

REM Pick the runtime archive flag depending on AOT vs CDS.
set "ARCHIVE_RUNTIME_OPT="
if "%ENABLE_AOT%"=="1" set "ARCHIVE_RUNTIME_OPT=-XX:AOTCache=$APPDIR\app.aot"
if "%ENABLE_CDS%"=="1" set "ARCHIVE_RUNTIME_OPT=-XX:SharedArchiveFile=$APPDIR\app.jsa"

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
if "%ENABLE_SPLASH%"=="1" if exist "%SCRIPT_DIR%splash.png" (
  copy /Y "%SCRIPT_DIR%splash.png" "%INPUT_DIR%\splash.png" >nul
)

REM JDK 24+ jpackage refactored BuildEnvBuilder requires --temp explicitly;
REM the auto-temp-dir path NPEs (BuildEnvBuilder.java:39, Objects.requireNonNull
REM on Path root). JDK 21 doesn't need this, but setting it is harmless on 21.
set "TEMP_DIR_STAGE1=%TARGET_DIR%\jpackage-temp-stage1"
set "TEMP_DIR_STAGE2=%TARGET_DIR%\jpackage-temp-stage2"
if exist "%TEMP_DIR_STAGE1%" rmdir /s /q "%TEMP_DIR_STAGE1%"
if exist "%TEMP_DIR_STAGE2%" rmdir /s /q "%TEMP_DIR_STAGE2%"
mkdir "%TEMP_DIR_STAGE1%"
mkdir "%TEMP_DIR_STAGE2%"

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

set "INTERMEDIATE_DIR=%TARGET_DIR%\jpackage-stage1"
if exist "%INTERMEDIATE_DIR%" rmdir /s /q "%INTERMEDIATE_DIR%"
mkdir "%INTERMEDIATE_DIR%"

echo Building %PACKAGE_TYPE% for Windows - version %APP_VERSION%, vendor "%APP_VENDOR%"
echo   add-modules:    !ADD_MODULES!
echo   java-options:   %JAVA_OPTIONS%
echo   splash:         %ENABLE_SPLASH%
echo   archive:        %ARCHIVE_RUNTIME_OPT%
echo   training delay: %TRAINING_MS% ms

REM ---- Stage 1: intermediate app-image -----------------------------------
jpackage ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --app-version %APP_VERSION% ^
  --vendor "%APP_VENDOR%" ^
  --description "FinDatEx data-template validator (TPT, EET, EMT, EPT)" ^
  --input "%INPUT_DIR%" ^
  --main-jar "!SHADED_JAR_NAME!" ^
  --main-class com.findatex.validator.AppLauncher ^
  --add-modules !ADD_MODULES! ^
  !STAGE1_OPTS! ^
  %ICON_ARG% ^
  %CONSOLE_FLAG% ^
  --temp "%TEMP_DIR_STAGE1%" ^
  --dest "%INTERMEDIATE_DIR%"
if errorlevel 1 (
  echo Stage-1 jpackage failed.
  exit /b 4
)

REM ---- Locate launcher + .cfg --------------------------------------------
set "APP_BUNDLE=%INTERMEDIATE_DIR%\%APP_NAME%"
set "LAUNCHER_BIN=%APP_BUNDLE%\%APP_NAME%.exe"
set "APP_DIR_RUNTIME=%APP_BUNDLE%\app"
set "CFG_FILE=%APP_DIR_RUNTIME%\%APP_NAME%.cfg"
set "RUNTIME_BIN_DIR=%APP_BUNDLE%\runtime\bin"

if not exist "%LAUNCHER_BIN%" (
  echo Stage-1 launcher not found at "%LAUNCHER_BIN%" - jpackage layout changed?
  exit /b 3
)

REM ---- Stage 2: training run → dump archive -------------------------------
set "ARCHIVE_FILE="
if "%ENABLE_AOT%"=="1" set "ARCHIVE_FILE=%APP_DIR_RUNTIME%\app.aot"
if "%ENABLE_CDS%"=="1" set "ARCHIVE_FILE=%APP_DIR_RUNTIME%\app.jsa"

if not "%ARCHIVE_FILE%"=="" (
  REM jpackage strips bin/java.exe from the runtime. AOT cache assembly spawns
  REM a child java process — we copy the system java.exe in for training, then
  REM remove it. Must be the same JDK build as %JAVA_HOME%.
  set "RUNTIME_JAVA=%RUNTIME_BIN_DIR%\java.exe"
  if not exist "%RUNTIME_BIN_DIR%" mkdir "%RUNTIME_BIN_DIR%"
  set "SYS_JAVA="
  for /f "delims=" %%J in ('where java 2^>nul') do (
    if not defined SYS_JAVA set "SYS_JAVA=%%J"
  )
  if not defined SYS_JAVA (
    echo No system 'java' on PATH - cannot run training. Skipping archive.
    set "ARCHIVE_FILE="
  ) else (
    copy /Y "!SYS_JAVA!" "!RUNTIME_JAVA!" >nul
  )

  if not "!ARCHIVE_FILE!"=="" (
    if "%ENABLE_AOT%"=="1" (
      set "ARCHIVE_TRAIN_FLAG=-XX:AOTCacheOutput=!ARCHIVE_FILE!"
    ) else (
      set "ARCHIVE_TRAIN_FLAG=-XX:ArchiveClassesAtExit=!ARCHIVE_FILE!"
    )

    echo.
    echo Training run - generating !ARCHIVE_FILE! ^(delay %TRAINING_MS% ms^)...

    copy /Y "%CFG_FILE%" "%CFG_FILE%.pretrain" >nul
    REM Append training-only options to the .cfg. _JAVA_OPTIONS would mangle
    REM paths containing spaces ("FinDatEx Validator"); .cfg parses each
    REM java-options line whole.
    >>"%CFG_FILE%" echo java-options=-Dfindatex.training=%TRAINING_MS%
    >>"%CFG_FILE%" echo java-options=!ARCHIVE_TRAIN_FLAG!

    "%LAUNCHER_BIN%"
    set "TRAIN_RC=!ERRORLEVEL!"

    REM Revert .cfg unconditionally so leftover training flags don't leak.
    move /Y "%CFG_FILE%.pretrain" "%CFG_FILE%" >nul

    if not "!TRAIN_RC!"=="0" (
      echo Training run failed ^(exit !TRAIN_RC!^); archive not generated.
      set "ARCHIVE_FILE="
    )
  )

  REM Drop the borrowed java.exe and any stale .config artefact.
  if exist "%RUNTIME_BIN_DIR%\java.exe" del /Q "%RUNTIME_BIN_DIR%\java.exe"
  if exist "!ARCHIVE_FILE!.config" del /Q "!ARCHIVE_FILE!.config"
  rmdir "%RUNTIME_BIN_DIR%" 2>nul

  if not "!ARCHIVE_FILE!"=="" if not exist "!ARCHIVE_FILE!" (
    echo   expected archive not produced at !ARCHIVE_FILE! - falling back to no-archive build.
    set "ARCHIVE_FILE="
  )

  if not "!ARCHIVE_FILE!"=="" (
    for %%S in ("!ARCHIVE_FILE!") do echo   archive: %%~zS bytes  ^(!ARCHIVE_FILE!^)
    >>"%CFG_FILE%" echo java-options=%ARCHIVE_RUNTIME_OPT%
    echo   patched %APP_NAME%.cfg with %ARCHIVE_RUNTIME_OPT%
  )
)

REM ---- Stage 3: package the augmented app-image --------------------------
echo.
if /I "%PACKAGE_TYPE%"=="app-image" (
  echo Final output is portable app-image ^(no installer wrap^).
  if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
  mkdir "%OUT_DIR%"
  REM /COPY:DAT + /DCOPY:DAT preserves timestamps so AOT cache file-stat hashes
  REM match. Don't use /COPYALL: its O (owner) and U (auditing/SACL) parts need
  REM SeRestorePrivilege / SeSecurityPrivilege which a normal user shell lacks,
  REM and robocopy then refuses to copy anything — silently, because it writes
  REM diagnostics to stdout which our >nul used to swallow. /E (no /PURGE)
  REM because the dest was just freshly mkdir'd and there's nothing to mirror.
  robocopy "%APP_BUNDLE%" "%OUT_DIR%\%APP_NAME%" /E /COPY:DAT /DCOPY:DAT /NJH /NJS /NP /NFL /NDL
  REM Robocopy exit codes 0-7 are success variants (0=nothing to do, 1=files
  REM copied, ...). 8+ means real failure.
  if errorlevel 8 (
    echo robocopy failed with errorlevel !errorlevel! - portable bundle not produced.
    exit /b 5
  )
) else (
  echo Wrapping app-image into final %PACKAGE_TYPE% ...
  jpackage ^
    --type %PACKAGE_TYPE% ^
    --name "%APP_NAME%" ^
    --app-version %APP_VERSION% ^
    --vendor "%APP_VENDOR%" ^
    --description "FinDatEx data-template validator (TPT, EET, EMT, EPT)" ^
    --app-image "%APP_BUNDLE%" ^
    %ICON_ARG% ^
    %INSTALLER_FLAGS% ^
    --temp "%TEMP_DIR_STAGE2%" ^
    --dest "%OUT_DIR%"
)

echo.
echo Output at %OUT_DIR%:
dir "%OUT_DIR%"
endlocal
