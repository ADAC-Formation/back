# TICKET-010 — Coordination Dockerfile frontend (repo externe)

## Story
Infrastructure — containerisation (voir `docs/INFRASTRUCTURE.md` sections 4 et 29)

## Description
Le Dockerfile frontend ne vit **pas dans ce repo** — il doit être créé par Manon dans `ADAC-Formation/front`.
Ce ticket est une coordination cross-repo : partager la spec avec Manon et vérifier que son repo publie bien
une image utilisable par `docker-compose.yml` de ce repo (`TICKET-011`).

Spec à transmettre à Manon (déjà dans `docs/INFRASTRUCTURE.md` section 4 et 29) :
- `front/Dockerfile` — multi-stage : builder `node:20-alpine` (`npm run build`) → runtime `nginx:alpine`
- `front/.dockerignore` — exclure `node_modules/`, `.env`, `dist/`
- Son propre CI pousse l'image vers `ghcr.io/adac-formation/front:<tag>` (même registry que le backend)

## Repo
Aucun — coordination, pas de code dans ce repo.

## Acceptance criteria
- [ ] La spec ci-dessus a été transmise à Manon (lien vers `docs/INFRASTRUCTURE.md`)
- [ ] Une image existe sur `ghcr.io/adac-formation/front` (au moins un tag, ex. `latest` ou `dev`)
- [ ] `docker pull ghcr.io/adac-formation/front:<tag>` fonctionne depuis cette machine
- [ ] Le container démarre et sert le build React sur le port 80

## Branch
Aucune — pas de commit associé à cette tâche dans ce repo.

## Checklist opérationnelle (remplace TDD — coordination, pas de code ici)
- [ ] Spec partagée avec Manon
- [ ] Image confirmée disponible sur ghcr.io

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] (aucun — coordination, pas de code dans ce repo)

## Depends on
- —

## Estimated time
0.5h

## Status
[ ] To do   [ ] In progress   [ ] Done
