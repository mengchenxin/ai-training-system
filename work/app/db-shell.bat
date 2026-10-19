@echo off
chcp 65001 >nul
title MySQL - ai_training (localhost:3306)
echo ================================================
echo   MySQL 命令行  ai_training  @ 127.0.0.1:3306
echo   账号 root，回车后输入密码（不显示任何字符）
echo   退出请输入： exit   或   quit
echo ================================================
echo.
"D:\develop\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -p -h127.0.0.1 -P3306 --default-character-set=utf8mb4 ai_training
echo.
echo [已退出 MySQL]
pause
