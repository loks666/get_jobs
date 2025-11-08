@echo off
setlocal ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION
chcp 65001 >nul

REM 一键结束前/后端端口并清理 Next.js 开发锁（纯 bat，无需 PowerShell）
set SCRIPT_DIR=%~dp0
for %%A in ("%SCRIPT_DIR%..") do set ROOT=%%~fA
set FRONT=%ROOT%\front
set LOCK=%FRONT%\.next\dev\lock
set PORTS=6866 6868 8888

echo ===============================================
echo 关闭端口进程: %PORTS%
echo ===============================================

for %%P in (%PORTS%) do call :KILL_BY_PORT %%P

if exist "%LOCK%" (
  del /f /q "%LOCK%" >nul 2>&1
  echo 已删除 Next 开发锁: "%LOCK%"
) else (
  echo 未发现 Next 开发锁: "%LOCK%"
)

echo 完成。
goto :EOF

:KILL_BY_PORT
set PORT=%~1
echo [端口 %PORT%] 检查监听中进程...
set FOUND=
for /f "tokens=5" %%I in ('netstat -ano ^| findstr /R /C:"LISTENING" ^| findstr /C:":%PORT%"') do (
  set FOUND=1
  set PID=%%I
  echo [端口 %PORT%] 结束 PID !PID! ...
  taskkill /F /PID !PID! >nul 2>&1
  if !ERRORLEVEL! EQU 0 (
    echo [端口 %PORT%] 已结束 PID !PID!
  ) else (
    echo [端口 %PORT%] 结束 PID !PID! 失败(可能已退出)
  )
)
if not defined FOUND (
  echo [端口 %PORT%] 未发现监听进程
)
goto :EOF