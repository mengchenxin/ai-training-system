@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0web"

if exist node_modules goto run

echo.
echo 首次运行，正在安装前端依赖（约 1-3 分钟）...
echo.
call npm install
if errorlevel 1 (
    echo [错误] 依赖安装失败
    pause
    exit /b 1
)

:run
echo.
echo 启动前端: http://127.0.0.1:3000
echo 关闭此窗口即停止前端
echo.
call npm run dev

pause
