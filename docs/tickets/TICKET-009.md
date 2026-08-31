# TICKET-009 — Dockerfile backend

## Story
Infrastructure — containerisation (voir `docs/INFRASTRUCTURE.md` sections 5 et 29)

## Description
Créer le Dockerfile multi-stage du backend Spring Boot : build Maven, runtime JRE minimal, utilisateur
non-root, healthcheck sur `/actuator/health`.

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `Dockerfile` — multi-stage : builder `maven:3.9-eclipse-temurin-21` → runtime `eclipse-temurin:21-jre`
- `.dockerignore` — exclure `target/`, `.env`, `*.md`

## Acceptance criteria
- [x] `docker build ./back` produit une image sans erreur
- [x] Le container démarre et `/actuator/health` répond `{"status":"UP"}`
- [x] Le process tourne sous un utilisateur non-root
- [x] Seul le JAR final est copié dans l'image (pas les sources ni le `.m2`)
- [x] Aucun secret en dur dans l'image (`docker history` ne révèle rien)
- [x] Port 8080 exposé, arrêt gracieux (`server.shutdown=graceful`)

## Branch
`feature/devops-setup`
- [x] Create: `git checkout -b feature/devops-setup`
- [ ] Switch to existing: `git checkout feature/devops-setup`

## Write tests first (TDD)
> Pour Docker, les "tests" sont des smoke tests reproductibles plutôt que des tests unitaires.

Before finishing:
- [x] `docker build ./back` réussit
- [x] `docker run --rm -p 8080:8080 <image>` puis `curl localhost:8080/actuator/health` → 200
- [x] `docker run --rm <image> whoami` → un utilisateur ≠ root

## Pre-commit review
Once smoke tests pass, run `/review-code` on the Dockerfile.
Fix any blocking issues (secrets exposés, COPY trop larges, image root) before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `chore(devops): add multi-stage backend Dockerfile`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/devops-setup` — see TICKET-012

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] /docker — pending verification

## Depends on
- TICKET-001 — le projet Spring Boot doit être buildable

## Estimated time
1h

## Status
[ ] To do   [ ] In progress   [x] Done
