@echo off
chcp 65001 >nul
setlocal EnableExtensions

cd /d "%~dp0"

echo ========================================
echo 启动小芽智能体 CLI
echo ========================================
echo.

call :resolve_python
if errorlevel 1 goto :failed

call :ensure_mysql
if errorlevel 1 goto :failed

call :check_database
if errorlevel 1 goto :failed

if "%XIAOYA_STARTUP_CHECK_ONLY%"=="1" (
    echo 启动前检查通过。
    exit /b 0
)

echo 正在启动命令行项目...
echo.
"%PYTHON_EXE%" Code\main.py
set "EXIT_CODE=%ERRORLEVEL%"
echo.
if not "%EXIT_CODE%"=="0" echo 小芽智能体已退出，退出码：%EXIT_CODE%
pause
exit /b %EXIT_CODE%

:resolve_python
if exist ".venv\Scripts\python.exe" (
    set "PYTHON_EXE=%CD%\.venv\Scripts\python.exe"
) else (
    echo 警告：未找到 .venv\Scripts\python.exe，将尝试使用系统 python。
    set "PYTHON_EXE=python"
)
set "PYTHONPATH=%CD%\Code"
exit /b 0

:ensure_mysql
if exist "scripts\ensure_mysql.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%CD%\scripts\ensure_mysql.ps1"
    if errorlevel 1 exit /b 1
)
exit /b 0

:check_database
"%PYTHON_EXE%" -c "from xiaoya_agent.database import database_storage_enabled, get_database_repository; enabled=database_storage_enabled(); print('存储后端: MySQL' if enabled else '存储后端: JSON'); cm=get_database_repository().connection() if enabled else None; conn=cm.__enter__() if enabled else None; cm.__exit__(None, None, None) if enabled else None; print('数据库连接正常' if enabled else '未启用 MySQL，跳过数据库连接检查')"
if errorlevel 1 (
    echo.
    echo 数据库连接检查失败，请确认 MySQL 已启动、config.env 配置正确，并且依赖已安装。
    exit /b 1
)
exit /b 0

:failed
echo.
echo 启动失败，请根据上面的错误信息修复后重试。
pause
exit /b 1
