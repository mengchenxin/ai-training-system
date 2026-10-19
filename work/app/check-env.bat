@echo off
chcp 65001 >nul
title 环境自检 - AI 培训计划管理系统
cd /d "%~dp0"

echo ================================================
echo    AI 培训计划管理系统 - 环境自检
echo ================================================
echo.

echo [1] Java 21
java -version >nul 2>&1
if errorlevel 1 (
    echo     [X] 没有找到 java，请安装 JDK 21 并配置 PATH
) else (
    java -version 2>&1 | findstr /i "version"
)
echo.

echo [2] Node.js
node -v >nul 2>&1
if errorlevel 1 (
    echo     [X] 没有找到 node，请安装 Node.js 22
) else (
    echo     node: 
    node -v
    echo     npm:
    npm -v
)
echo.

echo [3] MySQL 服务
net start 2>nul | findstr /i "mysql" >nul 2>&1
if errorlevel 1 (
    echo     [!] 没有运行中的 MySQL 服务
    echo         如果用的是绿色版,请手动启动 mysqld
) else (
    echo     MySQL 服务正在运行:
    net start 2>nul | findstr /i "mysql"
)
echo.

echo [4] 3306 端口
netstat -ano | findstr ":3306 " | findstr LISTENING
if errorlevel 1 (
    echo     [X] 3306 没有监听,数据库没启动
)
echo.

echo [5] 后端 jar 包
if exist "server\target\ai-training-server-1.0.0.jar" (
    echo     [OK] jar 存在
) else (
    echo     [X] 缺少 jar,需要 mvn package 或从原电脑复制 target 目录
)
echo.

echo [6] 前端依赖
if exist "web\node_modules" (
    echo     [OK] node_modules 已安装
) else (
    echo     [X] 未安装,请执行: cd web 然后 npm install
)
echo.

echo ================================================
echo    检查完毕
echo    带 [X] 的项,按 outputs\新电脑环境安装说明.md 处理
echo ================================================
pause
