@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

rem ============================================================
rem  为 Render 构建前端产物
rem
rem  用法（在后端部署完成、拿到域名之后执行）：
rem    build-for-render.bat https://ai-training-server-xxxx.onrender.com
rem
rem  说明：前端 API 地址在构建时烧进 JS，所以必须先有后端域名。
rem        Render 上不做前端构建（项目用 pnpm 且无 lock 文件，
rem        云端 npm 重装会导致依赖版本漂移），直接托管这里产出的 dist。
rem ============================================================

if "%~1"=="" (
    echo [错误] 缺少后端地址参数
    echo 用法: build-for-render.bat https://ai-training-server-xxxx.onrender.com
    pause
    exit /b 1
)

set API_BASE=%~1
rem 去掉末尾的斜杠，避免拼出 //api
if "%API_BASE:~-1%"=="/" set API_BASE=%API_BASE:~0,-1%

set VITE_API_BASE=%API_BASE%/api
echo [1/3] 后端地址: %API_BASE%
echo [2/3] 前端将请求: %VITE_API_BASE%

cd /d "%~dp0"
call npx vite build --outDir dist
if errorlevel 1 (
    echo [错误] 构建失败，请查看上面的报错
    pause
    exit /b 1
)

echo [3/3] 构建完成，产物在 dist\
echo.
echo 接下来提交并推送，Render 会自动重新部署静态站：
echo   cd /d %~dp0
echo   git add -A
echo   git commit -m "build: 更新前端产物"
echo   git push
echo.
pause
endlocal
