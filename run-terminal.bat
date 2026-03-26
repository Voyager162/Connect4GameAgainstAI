@echo off
setlocal
pushd "%~dp0"

if defined JAVA_HOME (
    "%JAVA_HOME%\bin\javac.exe" --release 8 TerminalConnectFour.java
    if errorlevel 1 (
        popd
        exit /b %errorlevel%
    )
    "%JAVA_HOME%\bin\java.exe" TerminalConnectFour
) else (
    javac --release 8 TerminalConnectFour.java
    if errorlevel 1 (
        popd
        exit /b %errorlevel%
    )
    java TerminalConnectFour
)

set "EXIT_CODE=%errorlevel%"
popd
exit /b %EXIT_CODE%
