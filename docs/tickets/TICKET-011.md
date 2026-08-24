# TICKET-011 — docker-compose.yml + .env.example (production)

## Story
Infrastructure — orchestration (voir `docs/INFRASTRUCTURE.md` section 7)

## Description
Assembler les 3 containers (db, backend, frontend) en Docker Compose pour la production, avec de vrais
healthchecks — `depends_on` seul ne garantit pas que PostgreSQL accepte les connexions.

`frontend` est en **`image:` pull** (`ghcr.io/adac-formation/front:${FRONT_IMAGE_TAG:-latest}`), pas en
`build:` — ce repo ne contient pas le code frontend (voir `TICKET-010`). Tant que Manon n'a pas publié
d'image réelle, utiliser un tag `dev`/`latest` provisoire pour développer et tester le reste du compose.

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
- [ ] `frontend` utilise `image: ghcr.io/adac-formation/front:${FRONT_IMAGE_TAG:-latest}` (pas de `build:` local)

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
- TICKET-010 — coordination cross-repo (une image frontend doit être disponible sur ghcr.io, même un tag
  provisoire, pour tester le compose complet)

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
