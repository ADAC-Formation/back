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

> **Constat avant codage** : `docker-compose.yml` (créé par TICKET-011) n'a jamais exposé de
> `ports:` public sur `db` ni `backend` — le commentaire `# No ports: — not reachable from outside
> the app_network (see INFRASTRUCTURE.md §7)` y figure déjà. Rien à corriger côté fichier ; ce
> ticket documente/vérifie un état déjà correct plutôt que de corriger une régression.

## Acceptance criteria
- [x] `docker-compose.yml` : ni `db` ni `backend` ne publient de `ports:` vers l'hôte (seul
      `frontend` publie 80/443) — vérifié par lecture du fichier
- [x] `backend` peut toujours atteindre `db` via le réseau Docker interne (`app_network`) — les
      deux services sont sur `app_network`, `DB_URL` pointe sur `db:5432` (nom de service, pas
      `localhost`)
- [x] `frontend`/Nginx peut toujours atteindre `backend` via le réseau interne — même réseau
- [ ] **Non vérifiable aujourd'hui** : `nc -zv <ip_vps> 5432/8080` depuis une machine externe, et
      `curl https://portail.adac.asso.fr/...` — nécessitent le VPS réel (TICKET-002, toujours à
      faire) ; à exécuter une fois le VPS provisionné

## Branch
`feature/devops-production`
- [x] Create: `git checkout -b feature/devops-production`
- [ ] Switch to existing: `git checkout feature/devops-production`

## Write tests first (TDD)
> Pour cette tâche, les "tests" sont des vérifications réseau reproductibles.

Before finishing:
- [ ] Depuis une machine externe : `nc -zv <ip_vps> 5432` → refused/timeout — **déféré, nécessite le VPS (TICKET-002)**
- [ ] Depuis une machine externe : `nc -zv <ip_vps> 8080` → refused/timeout — **déféré, nécessite le VPS**
- [ ] `curl https://portail.adac.asso.fr/api/actuator/health` → 200 (l'app fonctionne toujours) — **déféré, nécessite le VPS**

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
[ ] To do   [x] In progress   [ ] Done
> Code-level objective already met (see note above) — kept "In progress" rather than "Done"
> because the ticket's own acceptance criteria require a live VPS (TICKET-002) to actually verify.
