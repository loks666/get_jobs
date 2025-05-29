#!/bin/bash

# Linux系统启动脚本
echo "正在启动 Get Jobs 应用程序..."
echo

# 检查Java是否安装
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境"
    echo "请使用以下命令安装Java:"
    echo "Ubuntu/Debian: sudo apt update && sudo apt install openjdk-11-jdk"
    echo "CentOS/RHEL/Fedora: sudo yum install java-11-openjdk-devel"
    echo "或从Oracle官网下载: https://www.oracle.com/java/technologies/downloads/"
    echo
    read -p "按任意键退出..."
    exit 1
fi

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 检查jar文件是否存在
JAR_FILE="$SCRIPT_DIR/get_jobs-v2.0.1.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: 未找到 get_jobs-v2.0.1.jar 文件"
    echo "请确保jar文件与此脚本在同一目录下"
    echo
    read -p "按任意键退出..."
    exit 1
fi

# 启动应用程序
echo "启动中..."
java -jar "$JAR_FILE"

# 检查程序退出状态
EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
    echo
    echo "程序异常退出，错误代码: $EXIT_CODE"
    read -p "按任意键退出..."
fi 