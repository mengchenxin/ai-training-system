@echo off
chcp 936 >nul
setlocal
title 把 MySQL80 改为手动启动

rem ============================================================
rem  目的：让系统服务 MySQL80 不再开机自启，把 3306 让出来
rem
rem  背景：本机有两套 MySQL，都抢 3306
rem    1) 系统服务 MySQL80  数据目录含其他库，没有 ai_training
rem    2) 项目独立实例      数据目录 D:\develop\mysql-data\ai-training
rem  MySQL80 开机自启时先占住 3306，项目后端会报 Unknown database。
rem  改为「手动」后开机 3306 是空的，项目实例就能直接用 3306。
rem
rem  需要管理员权限，双击后按提示授权即可。
rem  改回自动启动的命令写在文件末尾的注释里。
rem ============================================================

rem ---------- 1. 检查管理员权限，没有就提权 ----------
fltmc >nul 2>&1
if not errorlevel 1 goto elevated

echo [提示] 当前不是管理员，正在申请提权，请在弹窗里点「是」
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
exit /b

:elevated
echo ==================================================
echo    把 MySQL80 改为「手动」启动
echo ==================================================
echo.
echo 修改前的状态：
powershell -NoProfile -Command "Get-Service MySQL80 | Format-Table Name,Status,StartType -AutoSize"
echo.
echo   1  只改启动类型，服务继续运行（下次开机不再自启）
echo   2  改启动类型，并立即停止服务（把 3306 立刻让出来）
echo.
choice /c 12 /n /m "  请选择 [1/2]："
set STOPNOW=0
if errorlevel 2 set STOPNOW=1

echo.
echo [1/3] 修改启动类型为「手动」...
powershell -NoProfile -Command "Set-Service -Name MySQL80 -StartupType Manual -ErrorAction Stop"
if errorlevel 1 (
    echo [错误] 修改失败，可能被安全软件拦截，请改用 services.msc 手动设置
    goto end
)
echo [OK] 已改为手动

if "%STOPNOW%"=="1" (
    echo.
    echo [2/3] 停止 MySQL80，释放 3306 ...
    powershell -NoProfile -Command "Stop-Service MySQL80 -Force -ErrorAction Stop"
    if errorlevel 1 (
        echo [警告] 停止失败，可稍后手动执行：net stop MySQL80
    ) else (
        echo [OK] 已停止
    )
) else (
    echo.
    echo [2/3] 按选择跳过停止，服务仍在运行
)

echo.
echo [3/3] 当前状态：
powershell -NoProfile -Command "Get-Service MySQL80 | Format-Table Name,Status,StartType -AutoSize"

echo.
echo ==================================================
echo   完成
echo.
echo   以后启动项目数据库：双击 work\app\start-mysql.bat
echo   它会在 3306 起项目实例，不再和其它实例冲突
echo.
echo   需要临时启用 MySQL80（其他库）时，在管理员窗口执行：
echo       net start MySQL80
echo ==================================================

:end
echo.
pause
endlocal
exit /b

rem ============================================================
rem  改回开机自启（管理员 PowerShell）：
rem      Set-Service -Name MySQL80 -StartupType Automatic
rem  或（管理员命令行）：
rem      sc config MySQL80 start= auto
rem ============================================================
