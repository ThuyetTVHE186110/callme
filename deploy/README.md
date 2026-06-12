# Triển khai production — DigitalOcean Droplet (CLAUDE.md G.7)

```
Internet → DO Cloud Firewall (22/80/443) → Droplet Ubuntu 24.04
  caddy  — TLS tự động (Let's Encrypt), HSTS, chặn /actuator/**
    → app — Spring Boot (profile prod), chỉ tồn tại trong Docker network
    → db  — postgres:16, volume, không publish port

GHCR  ←  GitHub Actions (cd.yml): test → build image (tag = git SHA) → SSH deploy
```

## 1. Chuẩn bị một lần

### Droplet
- Tạo droplet **Ubuntu 24.04 LTS, tối thiểu 2GB RAM** (JVM + Postgres + Caddy;
  1GB chỉ đủ nếu chấp nhận swap gánh phần thiếu), kèm SSH key của bạn.
- Bật **DigitalOcean Cloud Firewall** với inbound: 22, 80, 443 — lớp ngoài cùng,
  độc lập với UFW bên trong.
- Trỏ **A record** của domain API (vd. `api.callme.vn`) vào IP droplet *trước*
  lần deploy đầu — Caddy cần DNS đúng để xin chứng chỉ Let's Encrypt.

### Hardening (chạy một lần, với quyền root)
```bash
scp deploy/setup-droplet.sh root@<ip>:/root/
ssh root@<ip> 'bash setup-droplet.sh'
```
Script tạo user `deploy` (thừa kế SSH key), tắt đăng nhập root + password,
bật UFW (22 rate-limited/80/443) + fail2ban + unattended-upgrades, cài Docker,
tạo swap 2G, log rotation, và thư mục `/opt/callme`. **Sau script này chỉ còn
vào được bằng `ssh deploy@<ip>`.**

### Trên droplet (user `deploy`)
```bash
# 1. Đăng nhập GHCR một lần (PAT chỉ cần scope read:packages)
docker login ghcr.io -u <github-user>

# 2. Secrets runtime
cd /opt/callme
# tạo .env theo deploy/.env.example trong repo, rồi:
chmod 600 .env

# 3. Backup hằng đêm 03:00
crontab -e   # thêm:  0 3 * * * /opt/callme/backup-db.sh >> /opt/callme/backups/backup.log 2>&1
```

### GitHub repository secrets (Settings → Secrets and variables → Actions)
| Secret | Giá trị |
|---|---|
| `DROPLET_HOST` | IP droplet |
| `DROPLET_USER` | `deploy` |
| `DROPLET_SSH_PRIVATE_KEY` | private key tương ứng authorized key trên droplet (khuyến nghị: tạo keypair riêng cho CI, append public key vào `~deploy/.ssh/authorized_keys`) |

Muốn có bước **duyệt tay trước khi deploy**: Settings → Environments → `production`
→ Required reviewers (job `deploy` đã khai báo `environment: production`).

## 2. Vòng đời deploy

- **PR → master**: `ci.yml` chạy full test trên Postgres thật — gate của merge.
- **Push/merge vào master**: `cd.yml` chạy test lại → build image (tag `latest` +
  **git SHA**) → push GHCR → scp `docker-compose.yml`/`Caddyfile` (repo là nguồn
  sự thật, droplet không drift) → ghi `APP_TAG=<sha>` vào `.env` → `compose up -d`
  → **chờ container healthy, không healthy thì pipeline đỏ** kèm 200 dòng log cuối.
- Spring có `server.shutdown: graceful` — request đang chạy (complete trip, thanh
  toán...) được 20s để xong trước khi container cũ dừng.

## 3. Rollback

Mỗi deploy ghi SHA vào `.env` — rollback là trỏ về SHA cũ (xem lịch sử ở tab
Actions hoặc `docker images`):
```bash
ssh deploy@<ip>
cd /opt/callme
sed -i 's|^APP_TAG=.*|APP_TAG=<sha-cũ>|' .env
docker compose up -d
```
Lưu ý: rollback **code** không tự rollback **migration** — Flyway không undo.
Migration của project đều backward-compatible (thêm cột/bảng, không xóa), giữ
nguyên tắc đó để rollback luôn an toàn.

## 4. Vận hành thường nhật

```bash
docker compose ps                      # trạng thái + health
docker compose logs -f app             # log JSON theo correlationId
docker compose exec db psql -U callme  # vào DB
/opt/callme/backup-db.sh               # backup tay trước thao tác rủi ro
```
Restore: lệnh mẫu nằm cuối `backup-db.sh`. **Tập restore mỗi quý** — backup chưa
từng restore thử là backup chưa tồn tại.

## 5. Đường nâng cấp (khi có doanh thu/quy mô)

| Tín hiệu | Nâng cấp |
|---|---|
| DB là tài sản sống còn | DO **Managed PostgreSQL** (PITR, failover) — đổi `DB_URL`, bỏ service `db` |
| Cần >1 instance app | Droplet thứ 2 + DO Load Balancer; sweep đã có advisory lock, **nhưng** rate-limit in-process cần chuyển Redis (đã ghi chú trong `RateLimitFilter`) |
| Metrics/alert | Prometheus + Grafana scrape `/actuator/prometheus` qua mạng nội bộ (VPC), tuyệt đối không mở public |
| Ảnh hồ sơ tài xế, dung lượng | DO Spaces (S3-compatible) |
