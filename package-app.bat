@echo off
setlocal
pushd "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package-app.ps1"
set "EXIT_CODE=%errorlevel%"

popd
exit /b %EXIT_CODE%
