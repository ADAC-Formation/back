# TICKET-010 — Dockerfile frontend

## Story
Infrastructure — containerisation (voir `docs/INFRASTRUCTURE.md` sections 4 et 29)

## Description
Créer le Dockerfile multi-stage du frontend React : build Vite, servi en runtime par Nginx.

## Repo
[x] front/   [ ] back/   [ ] both

## Files to create or modify
- `front/Dockerfile` — multi-stage : builder `node:20-alpine` (`npm run build`) → runtime `nginx:alpine`
- `front/.dockerignore` — exclure `node_modules/`, `.env`, `dist/`

## Acceptance criteria
- [ ] `docker build ./front` produit une image sans erreur
- [ ] Le container démarre et sert le build React sur le port 80
- [ ] `curl localhost` (une fois exposé) → 200 avec le HTML de l'app React
- [ ] `VITE_API_URL` correctement injectée au moment du build

## Branch
`feature/devops-setup`
- [ ] Create: `git checkout -b feature/devops-setup`
- [ ] Switch to existing: `git checkout feature/devops-setup`

## Write tests first (TDD)
> Pour Docker, les "tests" sont des smoke tests reproductibles plutôt que des tests unitaires.

Before finishing:
- [ ] `docker build ./front` réussit
- [ ] `docker run --rm -p 8081:80 <image>` puis `curl localhost:8081` → 200 avec contenu HTML React

## Pre-commit review
Once smoke tests pass, run `/review-code` on the Dockerfile.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `chore(devops): add multi-stage frontend Dockerfile`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/devops-setup` — see TICKET-012

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] /docker — pending verification

## Depends on
- TICKET-008 — le projet React + Vite doit être buildable

## Estimated time
1h

## Status
[ ] To do   [ ] In progress   [ ] Done
