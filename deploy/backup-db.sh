#!/usr/bin/env bash
# Nightly logical backup of the production database (CLAUDE.md G.7).
# Install on the droplet:  crontab -e  ->  0 3 * * * /opt/callme/backup-db.sh
#
# pg_dump runs INSIDE the db container (no client tools needed on the host);
# gzipped dumps rotate after 14 days. This protects against bad deploys and fat
# fingers — for disk/droplet loss, enable DigitalOcean droplet backups too, and
# graduate to a managed database (with PITR) when revenue justifies it.
set -euo pipefail

APP_DIR=/opt/callme
BACKUP_DIR=$APP_DIR/backups
RETENTION_DAYS=14
STAMP=$(date +%Y%m%d-%H%M%S)

cd "$APP_DIR"
docker compose exec -T db pg_dump -U callme --format=custom callme \
    | gzip > "$BACKUP_DIR/callme-$STAMP.dump.gz"

find "$BACKUP_DIR" -name 'callme-*.dump.gz' -mtime +"$RETENTION_DAYS" -delete

# Restore drill (run quarterly, seriously):
#   gunzip -c backups/callme-<stamp>.dump.gz | docker compose exec -T db pg_restore -U callme -d callme --clean --if-exists
echo "Backup OK: callme-$STAMP.dump.gz ($(du -h "$BACKUP_DIR/callme-$STAMP.dump.gz" | cut -f1))"
