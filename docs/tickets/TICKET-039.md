# TICKET-039 — Retirer l'exposition publique des ports db/backend

## Story
Infrastructure — sécurité (voir `docs/INFRASTRUCTURE.md` section 7 et 24)

## Description
Corriger l'exposition actuelle des ports 5432 (PostgreSQL) et 8080 (backend) sur l'hôte : en production, seul
Nginx (80/443) doit être un point d'entrée public.

## Repo
[ ] front/   [ ] back   [x] both

## Files to create or modify
- `docker-compose.yml` — retirer les mappings `ports:` publics de `db` et `backend`

## Acceptance criteria
- [ ] Depuis l'extérieur du VPS, une tentative de connexion sur le port 5432 échoue (timeout/refused)
- [ ] Depuis l'extérieur du VPS, une tentative de connexion sur le port 8080 échoue (timeout/refused)
- [ ] `backend` peut toujours atteindre `db` via le réseau Docker interne (`app_network`)
- [ ] `frontend`/Nginx peut toujours atteindre `backend` via le réseau interne
- [ ] L'application reste pleinement fonctionnelle via `https://portail.adac.asso.fr`

## Branch
`feature/devops-production`
- [ ] Create: `git checkout -b feature/devops-production`
- [ ] Switch to existing: `git checkout feature/devops-production`

## Write tests first (TDD)
> Pour cette tâche, les "tests" sont des vérifications réseau reproductibles.

Before finishing:
- [ ] Depuis une machine externe : `nc -zv <ip_vps> 5432` → refused/timeout
- [ ] Depuis une machine externe : `nc -zv <ip_vps> 8080` → refused/timeout
- [ ] `curl https://portail.adac.asso.fr/api/actuator/health` → 200 (l'app fonctionne toujours)

## Pre-commit review
Once verified, run `/review-code` on `docker-compose.yml`.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `fix(devops): remove public exposure of db and backend ports`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/devops-production` — see TICKET-041

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] /docker — pending verification

## Depends on
- TICKET-011 — docker-compose de production

## Estimated time
0.5h

## Status
[ ] To do   [ ] In progress   [ ] Done
