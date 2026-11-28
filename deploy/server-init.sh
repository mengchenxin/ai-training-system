#!/usr/bin/env bash
# 服务器初始化脚本 —— 只需执行一次
# 适用：Ubuntu 20.04+ / Debian 11+ / CentOS 7+ / 阿里云、腾讯云的 Alibaba Cloud Linux
# 用法：sudo bash server-init.sh

set -euo pipefail

info() { echo -e "\033[32m[INFO]\033[0m $*"; }
warn() { echo -e "\033[33m[WARN]\033[0m $*"; }

if [ "$(id -u)" -ne 0 ]; then
  echo "请用 root 或 sudo 执行"; exit 1
fi

# ---------- 1. 加 2G swap（2G 内存机器必做，否则 MySQL + JVM 容易 OOM） ----------
if ! swapon --show | grep -q '/swapfile'; then
  info "创建 2G swap"
  fallocate -l 2G /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -w vm.swappiness=10
  grep -q 'vm.swappiness' /etc/sysctl.conf || echo 'vm.swappiness=10' >> /etc/sysctl.conf
else
  info "swap 已存在，跳过"
fi

# ---------- 2. 安装 Docker 与 compose 插件 ----------
if ! command -v docker >/dev/null 2>&1; then
  info "安装 Docker"
  if [ -f /etc/os-release ] && grep -qiE 'ubuntu|debian' /etc/os-release; then
    apt-get update -y
    apt-get install -y ca-certificates curl gnupg
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL "https://mirrors.aliyun.com/docker-ce/linux/$(. /etc/os-release && echo "$ID")/gpg" \
      | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/$(. /etc/os-release && echo "$ID") $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
      > /etc/apt/sources.list.d/docker.list
    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  else
    curl -fsSL https://get.docker.com | bash -s -- --mirror Aliyun
    yum install -y docker-compose-plugin || true
  fi
  systemctl enable --now docker
else
  info "Docker 已安装，跳过"
fi

# ---------- 3. 配置镜像加速（国内拉镜像不加这个会一直 timeout） ----------
mkdir -p /etc/docker
if [ ! -f /etc/docker/daemon.json ]; then
  info "写入镜像加速配置"
  cat > /etc/docker/daemon.json <<'JSON'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1panel.live",
    "https://hub.rat.dev"
  ],
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "3" }
}
JSON
  systemctl daemon-reload
  systemctl restart docker
  sleep 3
fi

# ---------- 4. 结果 ----------
info "Docker 版本：$(docker --version)"
info "Compose 版本：$(docker compose version 2>/dev/null || echo '未安装 compose 插件')"
info "内存：$(free -h | awk '/Mem:/{print $2}')　Swap：$(free -h | awk '/Swap:/{print $2}')"
info "初始化完成。接下来：解压部署包 → 配置 .env → docker compose up -d"
