# TICKET-041 — Pipeline CD (déploiement manuel déclenché)

## Story
Infrastructure — déploiement (voir `docs/INFRASTRUCTURE.md` section 15)

## Description
Mettre en place le déploiement continu, déclenché manuellement (pas automatique à chaque merge) — cohérent
avec le fait que personne n'est d'astreinte sur ce projet bénévole.

## Repo
[ ] front/   [ ] back/   [x] both

## Files to create or modify
- `.github/workflows/deploy.yml` — déclenchement `workflow_dispatch`

## Acceptance criteria
- [ ] Déclenchement manuel (bouton GitHub Actions) → build des images, push vers GitHub Container Registry
      avec un tag versionné (jamais uniquement `latest`)
- [ ] Connexion SSH au VPS, `docker compose pull && up -d`
- [ ] Vérification `/actuator/health` après déploiement
- [ ] Rollback automatique vers le tag précédent si le health check échoue
- [ ] Le déploiement ne se déclenche jamais automatiquement sur un simple merge

## Branch
`feature/devops-production`
- [ ] Create: `git checkout -b feature/devops-production`
- [ ] Switch to existing: `git checkout feature/devops-production`

## Write tests first (TDD)
> Pour un pipeline CD, la "preuve" est un déploiement réel + une simulation d'échec.

Before finishing:
- [ ] Déclenchement manuel → déploiement réussi, visible sur `https://portail.adac.asso.fr`
- [ ] Simulation d'un health check en échec → rollback automatique vers le tag précédent

## Pre-commit review
Once verified, run `/review-code` on the full branch before creating the PR.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `ci(devops): add manually-triggered CD pipeline with health check and rollback`

## PR (only on last ticket of this branch)
- [x] This is the last ticket on `feature/devops-production`
- [x] Run `/review-code` on the full branch before creating the PR
- [x] Run `/create-pr` — generates title + description, pushes, opens PR to `dev`

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] /github-actions — pending verification

## Depends on
- TICKET-012 — pipeline CI
- TICKET-002 — VPS provisionné
- TICKET-038 — TLS configuré

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
