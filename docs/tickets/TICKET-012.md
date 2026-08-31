# TICKET-012 — Pipeline CI backend (tests + build image)

## Story
Infrastructure — intégration continue (voir `docs/INFRASTRUCTURE.md` section 14)

## Description
Mettre en place GitHub Actions pour lancer les tests backend (JUnit) et valider le build de l'image
Docker backend sur chaque Pull Request vers `main`.

**Recentré sur le repo `back` uniquement** (2026-08-31) : le scope initial couvrait aussi le frontend
(tests Vitest + build image), mais Manon ne travaille pas en TDD — imposer un pipeline qui échoue au
moindre test front manquant/cassé serait soit bloquant en permanence, soit ignoré, ce qui casse
l'intérêt même d'un garde-fou automatique. Une CI frontend légère (build Docker uniquement, sans
exiger de tests) reste une option à proposer à Manon séparément, plus tard, sans la lui imposer.

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `.github/workflows/ci.yml`

## Acceptance criteria
- [ ] Sur chaque PR vers `main` : tests backend (JUnit, `mvn test`) exécutés
- [ ] Une base PostgreSQL réelle est disponible pour les tests (pas de mock/H2 — les tests actuels se
      connectent à un vrai Postgres, voir `FlywayMigrationTest`, `DefaultProfileActivationTest`, etc.)
- [ ] Build de l'image Docker backend (validation uniquement, pas de push vers un registre)
- [ ] Le pipeline échoue (rouge) si un test casse
- [ ] Le pipeline est visible dans l'onglet Actions du repo GitHub
- [ ] Aucun secret réel dans le workflow — `JWT_SECRET`/`DB_PASSWORD`/etc. utilisés pour les tests
      sont des valeurs de test en dur dans le YAML (pas de vrais secrets nécessaires pour un run CI,
      qui ne parle jamais à Mailtrap/Supabase pour de vrai)

## Branch
`feature/devops-setup`
- [ ] Create: `git checkout -b feature/devops-setup`
- [x] Switch to existing: `git checkout feature/devops-setup`

## Write tests first (TDD)
> Pour un pipeline CI, la "preuve" est son exécution réelle sur une PR de test.

Before finishing:
- [ ] Ouvrir une PR de test avec un test volontairement cassé → pipeline rouge
- [ ] Corriger le test → pipeline vert
- [ ] Vérifier que les 2 jobs (tests, build image) s'exécutent

## Pre-commit review
Once the pipeline is green on a test PR, run `/review-code` on `ci.yml`.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `ci(devops): add GitHub Actions pipeline for backend tests and Docker build validation`

## PR (only on last ticket of this branch)
- [ ] This is NOT necessarily the last ticket on `feature/devops-setup` — check TICKET-009/011 status

## Skills to invoke
- [ ] /github-actions — pending verification

## Depends on
- TICKET-009 — Dockerfile backend

## Estimated time
1.5h

## Status
[ ] To do   [ ] In progress   [ ] Done
