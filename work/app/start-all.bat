@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ==========================================
echo   AI 培训计划管理系统 - 一键启动
echo ==========================================
echo.

rem ---- 前置检查：MySQL ----
netstat -ano | findstr ":3306 " | findstr LISTENING >nul 2>nul
if errorlevel 1 (
    echo [提示] 3306 未监听，尝试启动本项目专用 MySQL 实例...
    echo.
    call "%~dp0start-mysql.bat"
    if errorlevel 1 (
        echo.
        echo [错误] MySQL 启动失败，请手动检查。
        echo        也可单独运行 start-mysql.bat 查看详细报错。
        echo.
        choice /c YN /m "仍要继续启动后端与前端吗"
        if errorlevel 2 exit /b 1
    )
) else (
    echo [OK] MySQL 已在 3306 监听
)

echo.
echo 正在打开两个窗口：后端(8000) 与 前端(3000)
echo 两个窗口都关掉才算全部停止。
echo.

start "AI培训-后端" cmd /k "%~dp0start-backend.bat"
timeout /t 3 /nobreak >nul
start "AI培训-前端" cmd /k "%~dp0start-frontend.bat"
timeout /t 8 /nobreak >nul

echo 正在探测服务...
timeout /t 6 /nobreak >nul
curl -s -o nul -w "后端 8000: %%{http_code}\n" http://127.0.0.1:8000/api/ai/status
curl -s -o nul -w "前端 3000: %%{http_code}\n" http://127.0.0.1:3000/
echo.
echo 后端返回 401 属正常（未登录），说明服务已起来。
echo 浏览器访问 http://127.0.0.1:3000
pause
