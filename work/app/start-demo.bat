@echo off
chcp 936 >nul
setlocal enabledelayedexpansion
title AI 培训计划管理系统 - 答辩演示环境
cd /d "%~dp0"

rem ============================================================
rem  答辩演示环境 一键启动
rem
rem  与 start-all.bat 的区别（后者是日常开发用，答辩时会翻车）：
rem   1. 端口自适应：3306 被系统 MySQL80 占着也不慌，自动退到 3307
rem      （start-mysql.bat 在 quiet 模式下遇到这种情况会直接退出）
rem   2. 前端不用 npm run dev，改为预构建 + preview
rem      （dev 的依赖优化器在本机删临时目录会撞沙箱策略直接崩）
rem   3. 严格按依赖顺序等待：库 - 后端 - 前端，每步有超时与诊断
rem   4. 每步都做真实探测（连库查库、curl 探 HTTP），不靠"启动了就算"
rem   5. 清掉上次残留的 8000/3000 进程，避免连到旧实例却看不出来
rem   6. 最后自动打开浏览器
rem
rem  用法：双击即可。想强制重建前端产物：
rem        start-demo.bat rebuild
rem ============================================================

set MYSQLD=D:\develop\MySQL\MySQL Server 8.0\bin\mysqld.exe
set MYSQLCLI=D:\develop\MySQL\MySQL Server 8.0\bin\mysql.exe
set MYCNF=D:\develop\mysql-data\conf\ai-training.cnf
set LOCALYML=%~dp0server\application-local.yml
set JAR=%~dp0server\target\ai-training-server-1.0.0.jar
set CHK=%TEMP%\demo_dbchk.tmp
set TRACE=%~dp0logs\start-demo-trace.log
if not exist "%~dp0logs" mkdir "%~dp0logs" 2>nul
echo ================ %date% %time% 启动 ================>>"%TRACE%"
set DBPORT=
set ERRORS=0

echo ================================================================
echo    AI 培训计划管理系统 - 答辩演示环境
echo ================================================================
echo.

rem ---------- 0. 环境预检 ----------
echo [0/5] 环境预检

where java >nul 2>nul
if errorlevel 1 (
    echo       [X] 找不到 java，请确认 JDK 21 已安装且加入 PATH
    goto fatal
)

if exist "%JAR%" goto jarok
echo       [!] 后端 jar 不存在，正在打包（约 1 分钟）...
pushd "%~dp0server"
call mvn -B -o package -DskipTests >nul 2>&1
popd
if not exist "%JAR%" (
    echo       [X] 打包失败，请手动执行 mvn package -DskipTests
    goto fatal
)
:jarok
echo       [OK] 后端 jar 就绪

if exist "%~dp0web\node_modules" goto wmok
echo       [X] 前端依赖未安装，请先执行 cd web 然后 npm install
goto fatal
:wmok
echo       [OK] 前端依赖就绪

if not exist "%LOCALYML%" (
    echo       [X] 缺少 server\application-local.yml（数据库密码配置，不入库）
    echo           请从备份或原电脑复制此文件
    goto fatal
)
set PW=
for /f "tokens=2 delims=:" %%a in ('findstr "password:" "%LOCALYML%"') do set PW=%%a
set PW=%PW: =%
if not defined PW (
    echo       [X] 读不到数据库密码，请检查 application-local.yml
    goto fatal
)
set MYSQL_PWD=%PW%
echo       [OK] 数据库配置文件就绪

if exist "%MYSQLCLI%" goto cliok
echo       [X] 找不到 mysql 客户端：%MYSQLCLI%
goto fatal
:cliok
echo.

rem ---------- 1. 数据库：找端口 ----------
echo [1/5] 数据库

call :probe 3306
if defined DBPORT goto db_found
call :probe 3307
if defined DBPORT goto db_found

echo       两个端口都没有本项目实例，在 3307 启动（不碰系统 MySQL80）
if not exist "%MYSQLD%" (
    echo       [X] 找不到 mysqld：%MYSQLD%
    goto fatal
)
if not exist "%MYCNF%" (
    echo       [X] 找不到配置：%MYCNF%
    goto fatal
)
netstat -ano | findstr ":3307 " | findstr LISTENING >nul 2>nul
if not errorlevel 1 (
    echo       [X] 3307 已被占用但不是本项目实例
    echo           多半是 application-local.yml 里的密码与实际库不一致
    goto fatal
)
start "AI培训-MySQL(3307)" "!MYSQLD!" --defaults-file="!MYCNF!" --port=3307 --console

echo       等待数据库就绪（最多 40 秒）...
set /a WAIT=0
:wait_db
call :probe 3307
if defined DBPORT goto db_found
set /a WAIT+=1
if %WAIT% geq 20 (
    echo       [X] 40 秒内未能连上 3307，请查看弹出的 MySQL 窗口报错
    goto fatal
)
call :nap 2
goto wait_db

:db_found
echo       [OK] 数据库可用，端口 %DBPORT%
set MYSQL_PWD=%PW%

echo       检查演示数据...
"%MYSQLCLI%" --connect-timeout=5 -h127.0.0.1 -P%DBPORT% -uroot -N -B -e "select (select count(*) from sys_user),(select count(*) from course),(select count(*) from train_plan),(select count(*) from kb_chunk)" >"%CHK%" 2>nul
if errorlevel 1 (
    echo       [!] 数据统计查询失败，可能表结构未初始化
) else (
    for /f "tokens=1-4" %%a in ('type "%CHK%"') do (
        echo       用户 %%a / 课程 %%b / 计划 %%c / 知识片段 %%d
        if "%%a"=="0" (
            echo       [X] 用户表为空，答辩无数据可演示
            set ERRORS=1
        )
    )
)
del "%CHK%" >nul 2>nul
echo.

rem ---------- 2. 后端 ----------
echo [2/5] 后端（8000）

rem 上一次的残留进程可能连的是别的库（比如 MySQL80 的），看起来正常但数据不对。
rem 答辩只启动一次，直接重启最省心。
call :killport 8000 "旧后端"
if defined KILLED call :nap 2

rem 用环境变量传连接串：命令行参数里的 & 在 start 下容易被当命令分隔符
set "SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:%DBPORT%/ai_training?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
rem 沙箱会注入 SERVER__PORT=0，被 relaxed binding 解析成随机端口，这里清掉
set SERVER__PORT=

start "AI培训-后端(8000)" /d "%~dp0server" cmd /k java -jar target\ai-training-server-1.0.0.jar --server.port=8000
echo [start] cwd=%~dp0server jar=target\ai-training-server-1.0.0.jar port=8000 >>"%TRACE%"
echo [dbport] %DBPORT% >>"%TRACE%"
call :nap 3
for /f "tokens=1,2" %%x in ('tasklist /fi "imagename eq java.exe" /fo table /nh') do echo [proc] java PID=%%y >>"%TRACE%"

echo       等待后端就绪（最多 90 秒）...
set /a WAIT=0
:wait_be
call :http_ok http://127.0.0.1:8000/api/ai/status
if not errorlevel 1 goto be_ready
set /a WAIT+=1
echo [wait_be %WAIT%] 8000 无响应 >>"%TRACE%"
if %WAIT% geq 45 (
    echo       [X] 90 秒内后端未响应，请查看「AI培训-后端」窗口的报错
    call :snap
    goto fatal
)
call :nap 2
goto wait_be

:be_ready
echo       [OK] 后端已响应
echo.

rem ---------- 3. 前端 ----------
echo [3/5] 前端（3000）

call :killport 3000 "旧前端"
if defined KILLED call :nap 2

if /i "%~1"=="rebuild" goto do_build
if exist "%~dp0web\dist-demo\index.html" goto do_preview

:do_build
echo       构建前端产物（约 40 秒，仅首次或改了前端代码时需要）...
pushd "%~dp0web"
call npx vite build --outDir dist-demo
popd
if not exist "%~dp0web\dist-demo\index.html" (
    echo       [X] 前端构建失败，请查看上方报错
    goto fatal
)
echo       [OK] 构建完成

:do_preview
start "AI培训-前端(3000)" /d "%~dp0web" cmd /k npx vite preview --outDir dist-demo --port 3000

echo       等待前端就绪（最多 40 秒）...
set /a WAIT=0
:wait_fe
call :http_ok http://127.0.0.1:3000/
if not errorlevel 1 goto fe_ready
set /a WAIT+=1
if %WAIT% geq 20 (
    echo       [X] 40 秒内前端未响应，请查看「AI培训-前端」窗口
    goto fatal
)
call :nap 2
goto wait_fe

:fe_ready
echo       [OK] 前端已响应
echo.

rem ---------- 4. 汇总 ----------
echo [4/5] 服务状态
curl -s -o nul --noproxy * --max-time 3 -w "      后端 8000 : HTTP %%{http_code}  (%%{time_total}s)\n" http://127.0.0.1:8000/api/ai/status
curl -s -o nul --noproxy * --max-time 3 -w "      前端 3000 : HTTP %%{http_code}  (%%{time_total}s)\n" http://127.0.0.1:3000/
echo.

rem ---------- 5. 打开浏览器 ----------
echo [5/5] 打开浏览器
start "" http://127.0.0.1:3000
echo.
echo ================================================================
echo    环境已就绪
echo.
echo    访问地址   http://127.0.0.1:3000
echo    登录账号   admin / 123456
echo    数据库     127.0.0.1:%DBPORT%
echo.
echo    关闭演示：双击 stop-demo.bat（或关掉弹出的服务窗口）
echo ================================================================
if "%ERRORS%"=="1" (
    echo.
    echo    [!] 数据检查发现问题，见上方提示
)
echo.
pause
exit /b 0

rem ============================================================
rem  子过程
rem ============================================================

:probe
rem 探测指定端口上是否是本项目的 ai_training 库，是则设置 DBPORT
set DBPORT=
if not defined PW exit /b 0
"%MYSQLCLI%" --connect-timeout=3 -h127.0.0.1 -P%~1 -uroot -N -B -e "show databases like 'ai_training'" >"%CHK%" 2>nul
if errorlevel 1 exit /b 0
findstr /i "ai_training" "%CHK%" >nul 2>nul
if errorlevel 1 exit /b 0
set DBPORT=%~1
exit /b 0

:snap
echo ---- 现场 ---- >>"%TRACE%"
netstat -ano | findstr ":8000 " >>"%TRACE%" 2>nul
tasklist /fi "imagename eq java.exe" >>"%TRACE%" 2>nul
exit /b 0

:nap
rem 等待约 N 秒。不用 timeout 是因为它在 stdin 非 console 时会直接失败返回
set /a NP=%~1+1
ping -n %NP% 127.0.0.1 >nul 2>nul
exit /b 0

:http_ok
rem URL 可访问（任何 HTTP 状态码都算服务活着）则 errorlevel=0
curl -s -o nul --noproxy * --max-time 3 "%~1" >nul 2>nul
exit /b %errorlevel%

:killport
rem 杀掉监听指定端口的进程；设 KILLED=1 表示确实杀了
set KILLED=
set HIT=
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%~1 " ^| findstr LISTENING') do set HIT=%%p
if not defined HIT (
    echo       %~2 未在运行，直接启动
    exit /b 0
)
taskkill /F /PID !HIT! >nul 2>nul
if errorlevel 1 (
    echo       [!] %~2 停止失败（PID !HIT!），可能需管理员权限
) else (
    echo       %~2 已在运行（PID !HIT!），已停止以便重连数据库
    set KILLED=1
)
exit /b 0

:fatal
echo.
echo ================================================================
echo    启动中断
echo.
echo    常见原因与处理：
echo      - 找不到 java      : 装 JDK 21，把 bin 加进 PATH
echo      - 缺少 local yml   : 复制 server\application-local.yml
echo      - 后端起不来       : 看「AI培训-后端」窗口，多半是库连不上
echo      - 端口被占         : 双击 stop-demo.bat 清理后重试
echo ================================================================
pause
exit /b 1
