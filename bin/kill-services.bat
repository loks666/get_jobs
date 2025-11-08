@echo off
REM kill port 6866 and 8888
setlocal
set SCRIPT_DIR=%~dp0
powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%kill-services.ps1" %*
endlocal