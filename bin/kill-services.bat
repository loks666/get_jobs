@echo off
REM 一键终止前端与后端服务（端口 6866 / 8888）
setlocal
set SCRIPT_DIR=%~dp0
powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%kill-services.ps1" %*
endlocal