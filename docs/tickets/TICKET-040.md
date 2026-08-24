# TICKET-040 — Script de sauvegarde automatique + runbook de restauration

## Story
Infrastructure — continuité de service (voir `docs/INFRASTRUCTURE.md` section 22)

## Description
Automatiser la sauvegarde quotidienne de PostgreSQL vers Supabase Storage, et documenter la procédure de
restauration. Une sauvegarde sans restauration testée n'est pas considérée comme fiable.

## Repo
[ ] front/   [ ] back   [x] both

## Files to create or modify
- `infra/backup.sh` — `pg_dump` + upload vers le bucket Supabase Storage `adac-backups` + purge > 14 jours
- Cron VPS déclenchant `backup.sh` quotidiennement
- `docs/RESTORE.md` — procédure exacte de restauration (`psql < backup.sql`)

## Acceptance criteria
- [ ] Exécution manuelle de `backup.sh` → un nouveau dump apparaît dans le bucket `adac-backups`
- [ ] Le cron s'exécute quotidiennement sans intervention manuelle
- [ ] Les dumps de plus de 14 jours sont purgés automatiquement
- [ ] `docs/RESTORE.md` documente la commande exacte de restauration
- [ ] Un test de restauration réel a été effectué une fois sur un environnement de test (pas seulement en théorie)

## Branch
`feature/devops-production`
- [ ] Create: `git checkout -b feature/devops-production`
- [ ] Switch to existing: `git checkout feature/devops-production`

## Write tests first (TDD)
> Pour un script de backup, la "preuve" est une restauration réelle, pas un test unitaire.

Before finishing:
- [ ] `./backup.sh` exécuté manuellement → dump présent dans Supabase Storage
- [ ] Restauration du dump sur une DB de test → les données correspondent à l'original
- [ ] Vérifier que la purge des dumps > 14 jours fonctionne (simuler une date ancienne)

## Pre-commit review
Once the restore test succeeds, run `/review-code` on `backup.sh`.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `chore(devops): add automated daily backup script and restore runbook`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/devops-production` — see TICKET-041

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] /docker — pending verification

## Depends on
- TICKET-011 — docker-compose (container `db` doit tourner)

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
