@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0server"

set JAR=target\ai-training-server-1.0.0.jar

if exist "%JAR%" goto run

echo.
echo [1/2] 首次运行，正在打包后端（跳过测试）...
echo.

where mvn >nul 2>nul
if %errorlevel%==0 (
    call mvn -B package -DskipTests
) else if exist "C:\Users\梦萦\.workbuddy\binaries\apache-maven-3.9.16\bin\mvn.cmd" (
    call "C:\Users\梦萦\.workbuddy\binaries\apache-maven-3.9.16\bin\mvn.cmd" -B package -DskipTests
) else (
    echo [错误] 找不到 mvn。请安装 Maven 3.9+ 并加入 PATH，或用 IDE 打包。
    pause
    exit /b 1
)

if not exist "%JAR%" (
    echo.
    echo [错误] 打包失败，未生成 %JAR%
    pause
    exit /b 1
)

:run
echo.
echo [2/2] 启动后端: http://127.0.0.1:8000/api
echo       接口文档: http://127.0.0.1:8000/api/doc.html
echo       关闭此窗口即停止后端
echo.
java -jar %JAR% --server.port=8000

pause
