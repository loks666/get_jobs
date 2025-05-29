@echo off
chcp 65001 >nul
title Get Jobs Application

echo 正在启动 Get Jobs 应用程序...
echo.

REM 检查Java是否安装
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到Java环境，请确保已安装Java 8或更高版本
    echo 请从 https://www.oracle.com/java/technologies/downloads/ 下载并安装Java
    pause
    exit /b 1
)

REM 获取脚本所在目录
set SCRIPT_DIR=%~dp0

REM 启动应用程序
echo 启动中...
java -jar "%SCRIPT_DIR%get_jobs-v2.0.1.jar"

REM 如果程序异常退出，暂停以显示错误信息
if %errorlevel% neq 0 (
    echo.
    echo 程序异常退出，错误代码: %errorlevel%
    pause
) 