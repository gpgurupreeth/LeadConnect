#!/usr/bin/env bash
# Install Docker Engine inside the existing WSL 2 Ubuntu distribution.
#
# This follows Docker's official Ubuntu installation instructions:
# https://docs.docker.com/engine/install/ubuntu/
#
# Docker Desktop is intentionally not used: it requires Windows administrator
# rights/UAC and may require a reboot. This script installs only the Linux
# Docker Engine, CLI, containerd, Buildx, and Compose plugin in WSL.
#
# Run from Windows PowerShell:
#   wsl -u root -e bash /mnt/c/.../backend-proxy/waha/install-docker-engine-wsl.sh
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -y ca-certificates curl

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

. /etc/os-release
cat > /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable --now docker
systemctl --no-pager --full status docker | head -n 20 || true

docker --version
docker compose version
