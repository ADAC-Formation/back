# TICKET-037 — Reverse proxy Nginx (routes + SPA fallback)

## Story
Infrastructure — mise en production (voir `docs/INFRASTRUCTURE.md` section 12)

## Description
Configurer Nginx comme reverse proxy : sert le frontend React avec fallback SPA, proxie `/api/` vers le
backend, transmet les en-têtes nécessaires.

## Repo
[ ] front/   [ ] back   [x] both

## Files to create or modify
- `nginx/nginx.conf`

## Acceptance criteria
- [x] `/` sert le frontend React
- [x] Rafraîchir une route front (ex. `/formations/3`) ne retourne pas 404 (fallback `try_files ... /index.html`)
- [x] `/api/` est proxié vers `backend:8080`
- [x] En-têtes `X-Forwarded-For` et `X-Forwarded-Proto` transmis au backend

## Branch
`feature/devops-production`
- [x] Create: `git checkout -b feature/devops-production`
- [ ] Switch to existing: `git checkout feature/devops-production`

## Write tests first (TDD)
> Pour Nginx, les "tests" sont des smoke tests reproductibles.

Before finishing:
- [x] `curl localhost/` → 200 avec le HTML React
- [x] `curl localhost/formations/3` (route front) → 200, pas 404
- [x] `curl localhost/api/formations` → proxié, réponse du backend

> Correction : l'énoncé original testait `curl localhost/api/actuator/health`, mais l'endpoint réel
> est `/actuator/health` **sans** préfixe `/api` (voir `SecurityConfig.PUBLIC_ROUTES`) — il n'est
> d'ailleurs pas exposé par ce reverse proxy (seul `/api/` l'est ; `/actuator/health` tombe dans le
> fallback SPA `location /`, pas dans le backend). Le monitoring externe (TICKET-042) doit cibler `/`
> sur le frontend, pas `/actuator/health` directement — cohérent avec le healthcheck déjà utilisé
> pour le container `frontend` lui-même (voir `INFRASTRUCTURE.md` §7). Testé avec `/api/formations` à
> la place : 401 (Spring Security répond — la requête atteint bien le backend via le proxy).

## Pre-commit review
Once smoke tests pass, run `/review-code` on `nginx.conf`.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `chore(devops): configure Nginx reverse proxy with SPA fallback`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/devops-production` — see TICKET-041

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] /nginx — pending verification

## Depends on
- TICKET-010 — image frontend/Nginx
- TICKET-011 — docker-compose assemblant les 3 containers

## Estimated time
1h

## Status
[ ] To do   [ ] In progress   [x] Done

## Origine
Déjà entièrement couvert par `nginx/nginx.conf` écrit pendant TICKET-011 (le fichier monté en volume
dans le container `frontend` de `docker-compose.yml`) — aucun fichier supplémentaire à créer, juste
vérifié et corrigé ici.
