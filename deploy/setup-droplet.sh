#!/usr/bin/env bash
# One-time hardening + provisioning for a fresh Ubuntu 24.04 DigitalOcean droplet
# (CLAUDE.md G.7). Run ONCE as root:
#
#   scp deploy/setup-droplet.sh root@<droplet-ip>:/root/ && ssh root@<droplet-ip> 'bash setup-droplet.sh'
#
# After it finishes, root SSH and password logins are DISABLED — all access goes
# through the `deploy` user with the same authorized key root was created with.
set -euo pipefail

DEPLOY_USER=deploy
APP_DIR=/opt/callme

echo "==> [1/8] System packages + unattended security upgrades"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get upgrade -yq
apt-get install -yq ufw fail2ban unattended-upgrades ca-certificates curl gnupg
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> [2/8] Deploy user (sudo, docker; SSH key inherited from root)"
if ! id "$DEPLOY_USER" &>/dev/null; then
    adduser --disabled-password --gecos "" "$DEPLOY_USER"
    usermod -aG sudo "$DEPLOY_USER"
fi
# Full passwordless sudo — a deliberate, honest choice, not an oversight:
# (1) the user has NO password (--disabled-password above), so any sudo rule that
#     would prompt can never succeed — a narrower whitelist here once locked the
#     operator out of `sudo ufw status` entirely;
# (2) deploy must be in the `docker` group for the CD pipeline, and docker-group
#     membership is already root-equivalent (mount / into a container) — a narrow
#     sudoers list would be security theater on this box. The real boundary is
#     key-only SSH + no root login + fail2ban + UFW; sudo still logs every command
#     with the invoking identity (OWASP A09).
echo "$DEPLOY_USER ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/deploy
chmod 440 /etc/sudoers.d/deploy
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" /home/$DEPLOY_USER/.ssh
cp /root/.ssh/authorized_keys /home/$DEPLOY_USER/.ssh/authorized_keys
chown "$DEPLOY_USER:$DEPLOY_USER" /home/$DEPLOY_USER/.ssh/authorized_keys
chmod 600 /home/$DEPLOY_USER/.ssh/authorized_keys

echo "==> [3/8] SSH hardening (no root login, no passwords)"
install -d /etc/ssh/sshd_config.d
cat > /etc/ssh/sshd_config.d/99-hardening.conf <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
X11Forwarding no
MaxAuthTries 3
EOF
systemctl reload ssh

echo "==> [4/8] Firewall — SSH (rate-limited), HTTP, HTTPS; everything else closed"
ufw default deny incoming
ufw default allow outgoing
ufw limit 22/tcp    # built-in brute-force throttle
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp   # HTTP/3
ufw --force enable

echo "==> [5/8] fail2ban (sshd jail with defaults)"
systemctl enable --now fail2ban

echo "==> [6/8] Docker Engine + Compose plugin (official repo)"
if ! command -v docker &>/dev/null; then
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update -q
    apt-get install -yq docker-ce docker-ce-cli containerd.io docker-compose-plugin
fi
usermod -aG docker "$DEPLOY_USER"
# Default log rotation for any container missing explicit logging config.
cat > /etc/docker/daemon.json <<'EOF'
{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
EOF
systemctl restart docker

echo "==> [7/8] 2G swap (JVM + Postgres on a small droplet need the headroom)"
if ! swapon --show | grep -q .; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
    sysctl -w vm.swappiness=10
    echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
fi

echo "==> [8/8] App directory"
install -d -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR" "$APP_DIR/backups"

cat <<EOF

Droplet ready. Next (as $DEPLOY_USER — root SSH is now disabled):
  1. Point your domain's A record at this droplet's IP.
  2. docker login ghcr.io -u <github-user>   (PAT with read:packages, done once)
  3. Create $APP_DIR/.env from deploy/.env.example, chmod 600.
  4. Add the backup cron:  crontab -e  ->  0 3 * * * $APP_DIR/backup-db.sh
  5. Configure GitHub secrets (DROPLET_HOST/USER/SSH key) and push to master —
     the CD pipeline ships compose + Caddyfile and starts everything.
Also enable the DigitalOcean CLOUD firewall (same 22/80/443 rules) as the outer layer.
EOF
