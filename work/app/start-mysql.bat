@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem  启动本项目专用的 MySQL 8.0 实例（3306）
rem
rem  说明：本机原有 MySQL80 服务用的是另一个数据目录（含其他库），
rem        本项目为隔离起见使用独立实例：
rem          数据目录  D:\develop\mysql-data\ai-training
rem          配置文件  D:\develop\mysql-data\conf\ai-training.cnf
rem        两者共用 3306 端口，因此不要同时启动 MySQL80 服务。
rem ============================================================

set MYSQLD=D:\develop\MySQL\MySQL Server 8.0\bin\mysqld.exe
set MYCNF=D:\develop\mysql-data\conf\ai-training.cnf

rem ---- 已在监听则直接返回 ----
netstat -ano | findstr ":3306 " | findstr LISTENING >nul 2>nul
if not errorlevel 1 (
    echo [OK] MySQL 已在 3306 监听，无需启动
    exit /b 0
)

if not exist "%MYSQLD%" (
    echo [错误] 找不到 mysqld：%MYSQLD%
    pause
    exit /b 1
)
if not exist "%MYCNF%" (
    echo [错误] 找不到配置文件：%MYCNF%
    pause
    exit /b 1
)

echo 正在启动 MySQL 实例（数据目录 D:\develop\mysql-data\ai-training）...
start "AI培训-MySQL" cmd /k ""%MYSQLD%" --defaults-file="%MYCNF%" --console"

rem ---- 最多等 30 秒 ----
for /l %%i in (1,1,30) do (
    netstat -ano | findstr ":3306 " | findstr LISTENING >nul 2>nul
    if not errorlevel 1 goto ready
    timeout /t 1 /nobreak >nul
)

echo [警告] 30 秒内未检测到 3306 监听，请查看弹出的 MySQL 窗口报错
exit /b 1

:ready
echo [OK] MySQL 已就绪（3306）
exit /b 0
