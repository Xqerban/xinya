@echo off
chcp 65001 >nul
echo 启动小芽智能体...

REM 激活虚拟环境
cd /d "%~dp0"
call venv\Scripts\activate.bat

cd Code
python main.py
pause