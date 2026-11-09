#!/usr/bin/env bash
# One-time provisioning for a fresh Ubuntu 24.04 VPS. Safe to re-run.
# Installs Docker + Compose plugin, opens 22/80/443, creates /opt/thelist-sync.
#
#   scp setup-vps.sh root@<vps-ip>:/root/ && ssh root@<vps-ip> 'bash /root/setup-vps.sh'
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "run as root" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl gnupg rsync ufw

if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc

  # shellcheck disable=SC1091
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list

  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
  echo "docker already installed"
fi

systemctl enable --now docker

ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

mkdir -p /opt/thelist-sync

echo
echo "Provisioned. Remember to also allow TCP 22/80/443 in the Kamatera firewall panel."
echo "Next, from your machine: cp deploy/kamatera/config.example deploy/kamatera/deploy.env && ./deploy/kamatera/deploy.sh"
