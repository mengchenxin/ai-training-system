@echo off
chcp 936 >nul
setlocal
title 启动 MySQL - AI 培训计划管理系统

rem ============================================================
rem  启动本项目专用的 MySQL 8.0 实例（3306）
rem
rem  本机还有系统自带的 MySQL80 服务，用的是另一个数据目录（含其他库）。
rem  本项目为隔离起见使用独立实例：
rem      数据目录  D:\develop\mysql-data\ai-training
rem      配置文件  D:\develop\mysql-data\conf\ai-training.cnf
rem  两者都抢 3306，所以启动前必须先确认端口上跑的是谁。
rem
rem  判定方式：连上去执行 show databases like 'ai_training'
rem    查得到  -> 本项目的实例，直接用
rem    查不到  -> 是 MySQL80，会让后端报 Unknown database，需要先关掉
rem
rem  参数：quiet  被 start-all.bat 调用时使用，不暂停、不交互
rem ============================================================

rem ---- 是否静默（由 start-all.bat 传入）----
set PZ=pause
if /i "%~1"=="quiet" set PZ=rem

set MYSQLD=D:\develop\MySQL\MySQL Server 8.0\bin\mysqld.exe
set MYSQLADMIN=D:\develop\MySQL\MySQL Server 8.0\bin\mysqladmin.exe
set MYSQLCLI=D:\develop\MySQL\MySQL Server 8.0\bin\mysql.exe
set MYCNF=D:\develop\mysql-data\conf\ai-training.cnf
set LOCALYML=%~dp0server\application-local.yml
set CHK=%TEMP%\ai_training_port_check.tmp

rem ---------- 从 application-local.yml 读数据库密码 ----------
rem 只取含 password: 的那一行，不依赖缩进空格数
set PW=
if exist "%LOCALYML%" (
    for /f "tokens=2 delims=:" %%a in ('findstr "password:" "%LOCALYML%"') do set PW=%%a
)
set PW=%PW: =%
if defined PW set MYSQL_PWD=%PW%

echo ==================================================
echo    启动 MySQL（项目专用实例）
echo ==================================================
echo.

rem ---------- 1. 3306 是否已被占用 ----------
netstat -ano | findstr ":3306 " | findstr LISTENING >nul 2>nul
if errorlevel 1 goto startours

echo [检查] 3306 已被占用，判断是不是本项目的实例...

if not defined PW goto foreign

"%MYSQLCLI%" --connect-timeout=5 -h127.0.0.1 -P3306 -uroot -N -B -e "show databases like 'ai_training'" >"%CHK%" 2>nul
if errorlevel 1 goto foreign

set FOUND=
for /f "usebackq delims=" %%a in ("%CHK%") do set FOUND=%%a
if /i not "%FOUND%"=="ai_training" goto foreign

echo [OK] 本项目的 MySQL 实例已在 3306 运行，无需启动
del "%CHK%" >nul 2>nul
%PZ%
exit /b 0

rem ---------- 2. 端口被别的实例占了 ----------
:foreign
echo.
echo [警告] 3306 上跑的不是本项目的实例
echo.
echo        通常是系统自带的 MySQL80 服务（开机自启），
echo        它的数据目录里没有 ai_training 库。
echo        直接启动会让后端报：Unknown database 'ai_training'
echo.
echo        正在运行的 MySQL 服务：
net start | findstr /i "mysql"
if errorlevel 1 echo        （没有，占用者可能是手动启动的 mysqld 进程）
echo.

if not defined PW goto manual
if /i "%~1"=="quiet" goto manual

choice /c YN /n /m "  是否关闭它并启动本项目实例? [Y 关闭 / N 退出] "
if errorlevel 2 goto abort

echo.
echo [处理] 正在关闭占用 3306 的实例...
"%MYSQLADMIN%" --connect-timeout=5 -h127.0.0.1 -P3306 -uroot shutdown >nul 2>nul
if errorlevel 1 (
    echo [提示] 客户端关闭失败，改用服务方式停止 MySQL80（需要管理员权限）...
    net stop MySQL80 >nul 2>nul
)

for /l %%i in (1,1,15) do (
    netstat -ano | findstr ":3306 " | findstr LISTENING >nul 2>nul
    if errorlevel 1 goto portfree
    timeout /t 1 /nobreak >nul
)

echo.
echo [错误] 3306 仍被占用，无法启动项目实例
goto manual

:portfree
echo [OK] 3306 已释放
echo.

rem ---------- 3. 启动本项目实例 ----------
:startours
if not exist "%MYSQLD%" (
    echo [错误] 找不到 mysqld：%MYSQLD%
    %PZ%
    exit /b 1
)
if not exist "%MYCNF%" (
    echo [错误] 找不到配置文件：%MYCNF%
    %PZ%
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

echo.
echo [警告] 30 秒内未检测到 3306 监听，请查看弹出的 MySQL 窗口报错
%PZ%
exit /b 1

:ready
echo [OK] MySQL 已就绪（3306）

rem ---- 再确认一次库里有没有 ai_training ----
if not defined PW (
    echo [提示] 未读到数据库密码，跳过库校验
    echo.
    %PZ%
    exit /b 0
)

"%MYSQLCLI%" --connect-timeout=5 -h127.0.0.1 -P3306 -uroot -N -B -e "show databases like 'ai_training'" >"%CHK%" 2>nul
if errorlevel 1 (
    echo [警告] 已监听但连不上，请检查 application-local.yml 里的密码
) else (
    findstr /i "ai_training" "%CHK%" >nul 2>nul
    if errorlevel 1 (
        echo [警告] 连上了但找不到 ai_training 库，请确认数据目录未被清空
    ) else (
        echo [OK] ai_training 库存在，可以启动后端了
    )
)
del "%CHK%" >nul 2>nul

echo.
%PZ%
exit /b 0

rem ---------- 收尾分支 ----------
:manual
echo.
echo        手动处理办法（任选一种）：
echo          1) 以管理员身份打开 cmd，执行： net stop MySQL80
echo          2) 在 services.msc 里把 MySQL80 的启动类型改成「手动」
echo        处理完再双击本脚本
echo.
%PZ%
exit /b 1

:abort
echo.
echo [退出] 未做任何改动
echo        若要走手动流程：管理员 cmd 里执行 net stop MySQL80，再双击本脚本
echo.
%PZ%
exit /b 1
