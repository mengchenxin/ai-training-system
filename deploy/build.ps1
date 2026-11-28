# 本地构建 + 打包部署包（Windows / PowerShell）
# 用法：在 bi 目录执行  powershell -ExecutionPolicy Bypass -File deploy\build.ps1
# 产出：bi\deploy-ai-training.tar.gz

$ErrorActionPreference = "Stop"
$deploy = $PSScriptRoot
$root = Split-Path -Parent $deploy
$server = Join-Path $root "work\app\server"
$web = Join-Path $root "work\app\web"

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

Step "1/4 后端打包（mvn package -DskipTests）"
Push-Location $server
mvn -q clean package -DskipTests
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Maven 打包失败" }
Pop-Location

$jar = Get-ChildItem (Join-Path $server "target\*.jar") |
  Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*javadoc*" } |
  Select-Object -First 1
if (-not $jar) { throw "target 下没找到 jar" }
Copy-Item $jar.FullName (Join-Path $deploy "backend\app.jar") -Force
Write-Host ("    jar: {0}  ({1:N1} MB)" -f $jar.Name, ($jar.Length / 1MB))

Step "2/4 前端构建（vite build → deploy\dist）"
$dist = Join-Path $deploy "dist"
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
Push-Location $web
npx vite build --outDir $dist --emptyOutDir
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "前端构建失败" }
Pop-Location
$size = (Get-ChildItem $dist -Recurse -File | Measure-Object Length -Sum).Sum
Write-Host ("    dist: {0:N1} MB / {1} 个文件" -f ($size / 1MB), (Get-ChildItem $dist -Recurse -File).Count)

Step "3/4 检查必要文件"
$need = @("docker-compose.yml", ".env.example", "nginx\nginx.conf", "mysql\my.cnf",
  "backend\Dockerfile", "backend\app.jar", "init\10-data.sql", "dist\index.html")
$missing = $need | Where-Object { -not (Test-Path (Join-Path $deploy $_)) }
if ($missing) { throw ("缺少文件：" + ($missing -join ", ")) }
Write-Host "    必需文件齐全"

Step "4/4 打 tar.gz"
$pkg = Join-Path $root "deploy-ai-training.tar.gz"
if (Test-Path $pkg) { Remove-Item $pkg -Force }
tar -czf $pkg --exclude "deploy/.env" -C $root deploy
if ($LASTEXITCODE -ne 0) { throw "打包失败" }
Write-Host ("`n完成：{0}  ({1:N1} MB)" -f $pkg, ((Get-Item $pkg).Length / 1MB)) -ForegroundColor Green
Write-Host "上传：scp `"$pkg`" root@<服务器IP>:/opt/"
Write-Host "服务器：cd /opt && tar -xzf deploy-ai-training.tar.gz && cd deploy && cp .env.example .env && vi .env"
