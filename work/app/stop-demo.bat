@echo off
chcp 936 >nul
setlocal enabledelayedexpansion
title AI 培训计划管理系统 - 停止演示环境
cd /d "%~dp0"

rem ============================================================
rem  停止演示环境
rem
rem  默认只停后端(8000)与前端(3000)，数据库留着（停库要十几秒，
rem  下次启动又要等；答辩中途重启服务时数据库不该跟着走）。
rem
rem  要连数据库一起停：stop-demo.bat all
rem ============================================================

echo ================================================================
echo    停止演示环境
echo ================================================================

call :killport 3000 "前端"
call :killport 8000 "后端"

if /i not "%~1"=="all" goto done

echo.
echo 停止数据库（3307 / 3306）...
set LOCALYML=%~dp0server\application-local.yml
set PW=
if exist "%LOCALYML%" (
    for /f "tokens=2 delims=:" %%a in ('findstr "password:" "%LOCALYML%"') do set PW=%%a
)
set PW=%PW: =%
if not defined PW (
    echo   [!] 读不到数据库密码，跳过
    goto done
)
set MYSQL_PWD=%PW%
set MYSQLADMIN=D:\develop\MySQL\MySQL Server 8.0\bin\mysqladmin.exe
if not exist "%MYSQLADMIN%" (
    echo   [!] 找不到 mysqladmin，跳过
    goto done
)
"%MYSQLADMIN%" --connect-timeout=3 -h127.0.0.1 -P3307 -uroot shutdown >nul 2>nul
if errorlevel 1 (echo   3307 未在运行) else (echo   [OK] 3307 已停止)

:done
echo.
echo ================================================================
echo    已停止。重新启动请双击 start-demo.bat
echo ================================================================
pause
exit /b 0

:killport
set HIT=
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%~1 " ^| findstr LISTENING') do set HIT=%%p
if not defined HIT (
    echo   %~2 %~1：未在运行
    exit /b 0
)
taskkill /F /PID !HIT! >nul 2>nul
if errorlevel 1 (
    echo   [!] %~2 %~1：停止失败（PID !HIT!），可能需管理员权限
) else (
    echo   [OK] %~2 %~1：已停止（PID !HIT!）
)
exit /b 0
