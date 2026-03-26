@echo off
setlocal
pushd "%~dp0"

if not defined JAVA_HOME (
    echo JAVA_HOME is not set.
    echo Point JAVA_HOME at a JDK 17+ install and try again.
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
    echo Could not find JavaFX libraries.
    echo Set JAVAFX_LIB to a JavaFX lib folder or place the jars in "%~dp0javafx-sdk\lib".
    popd
    exit /b 1
)

set "JAVAFX_MODULE_PATH=%JAVAFX_BASE_JAR%;%JAVAFX_GRAPHICS_JAR%;%JAVAFX_CONTROLS_JAR%"

"%JAVA_HOME%\bin\javac.exe" --module-path "%JAVAFX_MODULE_PATH%" --add-modules javafx.controls ConnectFourFX.java
if errorlevel 1 (
    popd
    exit /b %errorlevel%
)

"%JAVA_HOME%\bin\java.exe" --module-path "%JAVAFX_MODULE_PATH%" --add-modules javafx.controls ConnectFourFX
set "EXIT_CODE=%errorlevel%"
popd
exit /b %EXIT_CODE%

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
