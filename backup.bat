@echo off
chcp 65001 >nul
title 变更前备份 - AI 培训计划管理系统
cd /d "%~dp0"
setlocal enabledelayedexpansion

rem ============================================================
rem  用途：在动手改代码之前跑一次，得到一个可回退的干净存档
rem
rem  做两件事：
rem    1. 本地快照  → ..\backups\bi-YYYYMMDD（当天一份，多次跑增量更新）
rem    2. GitHub    → 把当前改动提交并推送，形成明确的时间锚点
rem
rem  排除项（可用命令重建，不占备份空间）：
rem    node_modules / target / dist / installers / _diagnostics / .git
rem
rem  注意：本地快照不含 .git，版本历史以 GitHub 上的为准。
rem ============================================================

echo ==================================================
echo    变更前备份
echo ==================================================
echo.

if not exist ".git" (
    echo [错误] 当前目录不是 git 仓库，请确认脚本放在 bi\ 根目录
    goto end
)

rem ---------- 时间戳（一次调用 powershell，避免 %date% 受区域设置影响）----------
for /f "tokens=1,2" %%a in ('powershell -NoProfile -Command "Get-Date -Format 'yyyyMMdd HHmmss'"') do (
    set DAY=%%a
    set HMS=%%b
)
if "%DAY%"=="" (
    echo [错误] 获取系统时间失败
    goto end
)
echo [时间] %DAY% %HMS%
echo.

rem ---------- 1. 本地快照 ----------
set BK=..\backups\bi-%DAY%
if exist "%BK%" (
    echo [本地] 今日备份已存在，增量更新
) else (
    echo [本地] 创建今日备份
)
echo [本地] 目标: %BK%
robocopy "%~dp0." "%BK%" /E /XD node_modules target dist installers _diagnostics .git /XF *.log /NFL /NDL /NJH /NP >nul
if errorlevel 8 (
    echo [错误] 本地备份失败，robocopy 返回 %errorlevel%
    goto end
)
echo [本地] 完成
echo.

rem ---------- 2. 提交并推送到 GitHub ----------
set CHANGED=0
for /f "delims=" %%i in ('git status --porcelain') do set CHANGED=1

if "%CHANGED%"=="1" (
    echo [GitHub] 检测到未提交改动，生成快照提交
    git add -A
    git commit -m "chore(snapshot): 变更前备份 %DAY%-%HMS%"
) else (
    echo [GitHub] 工作区干净，无需新增提交
)

echo [GitHub] 推送中...
git push
if errorlevel 1 (
    echo.
    echo [警告] 推送失败。常见原因：
    echo         1. 网络未走代理，执行: set HTTPS_PROXY=http://127.0.0.1:7897
    echo         2. 远程有新提交，先执行: git pull --rebase
    echo         本地提交已保留，网络恢复后重新 git push 即可。
) else (
    echo [GitHub] 推送成功
)

echo.
echo ================== 备份完成 ==================
echo 本地快照: %BK%
for /f "delims=" %%i in ('git log --oneline -1') do echo 最近提交: %%i
echo.
echo 现在可以放心修改代码了。
echo.

:end
pause
endlocal
