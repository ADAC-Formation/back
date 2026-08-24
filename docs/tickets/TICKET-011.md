# TICKET-011 — docker-compose.yml + .env.example (production)

## Story
Infrastructure — orchestration (voir `docs/INFRASTRUCTURE.md` section 7)

## Description
Assembler les 3 containers (db, backend, frontend) en Docker Compose pour la production, avec de vrais
healthchecks — `depends_on` seul ne garantit pas que PostgreSQL accepte les connexions.

## Repo
[ ] front/   [ ] back/   [x] both

## Files to create or modify
- `docker-compose.yml` (racine) — services `db`, `backend`, `frontend`, réseau `app_network`, volume `postgres_data`
- `.env.example` (racine) — toutes les variables nécessaires, sans valeurs réelles

## Acceptance criteria
- [ ] `docker compose config` ne retourne aucune erreur de syntaxe
- [ ] `docker compose up --build` démarre les 3 containers en état `healthy`
- [ ] `backend` attend réellement que `db` soit prête via `condition: service_healthy` (pas `depends_on` seul)
- [ ] Redémarrage d'un container sans perte des données (volume `postgres_data` persistant)
- [ ] `db` et `backend` ne publient aucun port vers l'hôte (seul `frontend` le fait)
- [ ] `restart: unless-stopped` sur les 3 services
- [ ] Tous les secrets viennent de `${...}`, jamais en dur

## Branch
`feature/devops-setup`
- [ ] Create: `git checkout -b feature/devops-setup`
- [ ] Switch to existing: `git checkout feature/devops-setup`

## Write tests first (TDD)
> Pour Docker Compose, les "tests" sont des smoke tests reproductibles.

Before finishing:
- [ ] `docker compose config` sans erreur
- [ ] `docker compose up --build` → les 3 containers passent `healthy`
- [ ] `docker compose restart db` → l'application reste fonctionnelle et les données sont intactes

## Pre-commit review
Once smoke tests pass, run `/review-code` on `docker-compose.yml`.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `chore(devops): add production docker-compose with healthchecks`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/devops-setup` — see TICKET-012

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] /docker — pending verification

## Depends on
- TICKET-009 — Dockerfile backend
- TICKET-010 — Dockerfile frontend

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
