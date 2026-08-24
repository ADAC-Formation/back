# TICKET-012 — Pipeline CI (tests + build images)

## Story
Infrastructure — intégration continue (voir `docs/INFRASTRUCTURE.md` section 14)

## Description
Mettre en place GitHub Actions pour lancer les tests backend et frontend et valider le build des deux images
Docker sur chaque Pull Request vers `dev`/`main`.

## Repo
[ ] front/   [ ] back   [x] both

## Files to create or modify
- `.github/workflows/ci.yml`

## Acceptance criteria
- [ ] Sur chaque PR vers `dev` ou `main` : tests backend (JUnit) exécutés
- [ ] Tests frontend (Vitest) exécutés
- [ ] Build des deux images Docker (validation uniquement, pas de push)
- [ ] Le pipeline échoue (rouge) si un test casse
- [ ] Le pipeline est visible dans l'onglet Actions du repo GitHub

## Branch
`feature/devops-setup`
- [ ] Create: `git checkout -b feature/devops-setup`
- [ ] Switch to existing: `git checkout feature/devops-setup`

## Write tests first (TDD)
> Pour un pipeline CI, la "preuve" est son exécution réelle sur une PR de test.

Before finishing:
- [ ] Ouvrir une PR de test avec un test volontairement cassé → pipeline rouge
- [ ] Corriger le test → pipeline vert
- [ ] Vérifier que les 4 jobs (tests back, tests front, build image back, build image front) s'exécutent

## Pre-commit review
Once the pipeline is green on a test PR, run `/review-code` on `ci.yml`.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `ci(devops): add GitHub Actions pipeline for tests and Docker build validation`

## PR (only on last ticket of this branch)
- [x] This is the last ticket on `feature/devops-setup`
- [x] Run `/review-code` on the full branch before creating the PR
- [x] Run `/create-pr` — generates title + description, pushes, opens PR to `dev`

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] /github-actions — pending verification

## Depends on
- TICKET-009 — Dockerfile backend
- TICKET-010 — Dockerfile frontend

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
