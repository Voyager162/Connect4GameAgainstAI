@echo off
setlocal
pushd "%~dp0"

if not defined JAVA_HOME (
    echo JAVA_HOME is not set.
    echo Point JAVA_HOME at a JDK 17+ install and try again.
    popd
    exit /b 1
)

if not defined APP_VERSION set "APP_VERSION=1.0.0"
call :normalizeVersion "%APP_VERSION%" JPACKAGE_APP_VERSION

if not defined JPACKAGE_APP_VERSION (
    echo APP_VERSION must contain at least one number, for example 1.0.0 or v1.0.0.
    popd
    exit /b 1
)

set "JAVAFX_BASE_JAR="
set "JAVAFX_GRAPHICS_JAR="
set "JAVAFX_CONTROLS_JAR="

if defined JAVAFX_LIB (
    call :resolveJavaFxJar "%JAVAFX_LIB%" "javafx.base.jar" "javafx-base-*-win.jar" JAVAFX_BASE_JAR
    call :resolveJavaFxJar "%JAVAFX_LIB%" "javafx.graphics.jar" "javafx-graphics-*-win.jar" JAVAFX_GRAPHICS_JAR
    call :resolveJavaFxJar "%JAVAFX_LIB%" "javafx.controls.jar" "javafx-controls-*-win.jar" JAVAFX_CONTROLS_JAR
)

if not defined JAVAFX_BASE_JAR (
    call :resolveJavaFxJar "%~dp0javafx-sdk\lib" "javafx.base.jar" "javafx-base-*-win.jar" JAVAFX_BASE_JAR
    call :resolveJavaFxJar "%~dp0javafx-sdk\lib" "javafx.graphics.jar" "javafx-graphics-*-win.jar" JAVAFX_GRAPHICS_JAR
    call :resolveJavaFxJar "%~dp0javafx-sdk\lib" "javafx.controls.jar" "javafx-controls-*-win.jar" JAVAFX_CONTROLS_JAR
)

if not defined JAVAFX_BASE_JAR (
    echo Could not find JavaFX runtime jars.
    echo Set JAVAFX_LIB to a JavaFX lib folder or place the jars in "%~dp0javafx-sdk\lib".
    popd
    exit /b 1
)

set "APP_NAME=Connect Four FX"
set "BUILD_ROOT=%~dp0build\package"
set "CLASSES_DIR=%BUILD_ROOT%\classes"
set "INPUT_DIR=%BUILD_ROOT%\input"
set "DIST_DIR=%~dp0dist"
set "APP_DIR=%DIST_DIR%\%APP_NAME%"
set "ZIP_PATH=%DIST_DIR%\%APP_NAME%-%APP_VERSION%-portable.zip"
set "JAVAFX_COMPILE_MODULE_PATH=%JAVAFX_BASE_JAR%;%JAVAFX_GRAPHICS_JAR%;%JAVAFX_CONTROLS_JAR%"

if exist "%BUILD_ROOT%" rmdir /s /q "%BUILD_ROOT%"
if exist "%APP_DIR%" rmdir /s /q "%APP_DIR%"
if exist "%ZIP_PATH%" del /q "%ZIP_PATH%"

mkdir "%CLASSES_DIR%"
mkdir "%INPUT_DIR%"

"%JAVA_HOME%\bin\javac.exe" --module-path "%JAVAFX_COMPILE_MODULE_PATH%" --add-modules javafx.controls -d "%CLASSES_DIR%" ConnectFourFX.java
if errorlevel 1 (
    popd
    exit /b %errorlevel%
)

copy /y "%~dp0connect-four.css" "%CLASSES_DIR%\connect-four.css" >nul

"%JAVA_HOME%\bin\jar.exe" --create --file "%INPUT_DIR%\ConnectFourFX.jar" --main-class ConnectFourFX -C "%CLASSES_DIR%" .
if errorlevel 1 (
    popd
    exit /b %errorlevel%
)

copy /y "%JAVAFX_BASE_JAR%" "%INPUT_DIR%\" >nul
copy /y "%JAVAFX_GRAPHICS_JAR%" "%INPUT_DIR%\" >nul
copy /y "%JAVAFX_CONTROLS_JAR%" "%INPUT_DIR%\" >nul

"%JAVA_HOME%\bin\jpackage.exe" ^
    --type app-image ^
    --dest "%DIST_DIR%" ^
    --input "%INPUT_DIR%" ^
    --name "%APP_NAME%" ^
    --app-version "%JPACKAGE_APP_VERSION%" ^
    --vendor "Westo" ^
    --description "Desktop Connect Four with a JavaFX UI and AI opponent." ^
    --main-jar "ConnectFourFX.jar" ^
    --main-class "ConnectFourFX"
if errorlevel 1 (
    popd
    exit /b %errorlevel%
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '%APP_DIR%' -DestinationPath '%ZIP_PATH%' -Force"
if errorlevel 1 (
    popd
    exit /b %errorlevel%
)

echo.
echo Packaged desktop app created at:
echo %APP_DIR%
echo.
echo Portable zip created at:
echo %ZIP_PATH%

popd
exit /b 0

:resolveJavaFxJar
setlocal
set "SEARCH_DIR=%~1"
set "SDK_NAME=%~2"
set "PATTERN=%~3"
set "FOUND_FILE="

if defined SEARCH_DIR (
    if exist "%SEARCH_DIR%\%SDK_NAME%" (
        set "FOUND_FILE=%SEARCH_DIR%\%SDK_NAME%"
        goto :resolveJavaFxJarDone
    )

    for %%F in ("%SEARCH_DIR%\%PATTERN%") do (
        if exist "%%~fF" (
            set "FOUND_FILE=%%~fF"
            goto :resolveJavaFxJarDone
        )
    )
)

:resolveJavaFxJarDone
endlocal & set "%~4=%FOUND_FILE%"
exit /b 0

:normalizeVersion
setlocal
set "RAW_VERSION_INPUT=%~1"
set "NORMALIZED_VERSION="

for /f "usebackq delims=" %%V in (`powershell -NoProfile -Command "$parts = [regex]::Matches($env:RAW_VERSION_INPUT, '\d+') | ForEach-Object { $_.Value }; if ($parts.Count -gt 0) { [string]::Join('.', $parts) }"`) do (
    set "NORMALIZED_VERSION=%%V"
)

endlocal & set "%~2=%NORMALIZED_VERSION%"
exit /b 0
