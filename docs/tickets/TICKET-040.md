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
- [ ] **Non vérifiable aujourd'hui** : exécution manuelle de `backup.sh` → un nouveau dump apparaît
      dans le bucket `adac-backups` — nécessite de vraies credentials Supabase et un `db` en
      cours d'exécution via `docker compose` ; le script est écrit et sa logique de purge (filtre
      par date) a été testée isolément avec `jq` (voir revue), mais pas exécuté de bout en bout
- [ ] **Non vérifiable aujourd'hui** : le cron s'exécute quotidiennement — nécessite le VPS
      (TICKET-002) ; la ligne crontab exacte est documentée en en-tête de `infra/backup.sh`
- [x] Les dumps de plus de 14 jours sont purgés automatiquement — logique de filtre par date
      (`jq` + `created_at`) écrite et testée isolément (fixture ancien/récent → seul l'ancien
      est retenu pour suppression)
- [x] `docs/RESTORE.md` documente la commande exacte de restauration (test + production)
- [ ] **Non vérifiable aujourd'hui** : test de restauration réel sur un environnement de test —
      nécessite un dump réel produit par le script en conditions réelles (voir ci-dessus)

## Branch
`feature/devops-production`
- [ ] Create: `git checkout -b feature/devops-production`
- [x] Switch to existing: `git checkout feature/devops-production` (créée par TICKET-039, même branche)

## Write tests first (TDD)
> Pour un script de backup, la "preuve" est une restauration réelle, pas un test unitaire.

Before finishing:
- [ ] `./backup.sh` exécuté manuellement → dump présent dans Supabase Storage — **déféré, pas de credentials/VPS réels ici**
- [ ] Restauration du dump sur une DB de test → les données correspondent à l'original — **déféré, dépend du point précédent**
- [x] Vérifier que la purge des dumps > 14 jours fonctionne (simuler une date ancienne) — testé
      isolément avec une fixture `jq` (un dump daté 2020, un daté 2099 → seul le premier retenu)

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
[ ] To do   [x] In progress   [ ] Done
> Script + doc écrits et revus ; le test de restauration réel (le critère le plus important de ce
> ticket, voir sa propre description) reste à faire une fois le VPS et de vraies credentials
> Supabase disponibles — kept "In progress", pas "Done".
