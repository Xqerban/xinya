@echo off
chcp 65001 >nul
echo ========================================
echo 启动小芽 Agent API 服务
echo ========================================
echo.

cd /d "%~dp0"

if not exist ".venv\Scripts\activate.bat" (
    echo 错误: 虚拟环境不存在，请先运行 start.bat 创建虚拟环境
    pause
    exit /b 1
)

call .venv\Scripts\activate.bat

echo 正在启动 API 服务...
echo 服务地址: http://localhost:8000
echo 按 Ctrl+C 停止服务
echo.

python Code\api_server.py

pause
