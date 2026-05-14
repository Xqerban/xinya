@echo off
chcp 65001 >nul
echo 启动小芽智能体...
cd /d "%~dp0"
if exist ".venv\Scripts\activate.bat" (
    call .venv\Scripts\activate.bat
)
python Code\main.py
pause
