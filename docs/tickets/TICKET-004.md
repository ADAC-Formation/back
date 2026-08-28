# TICKET-004 — Introduire Flyway pour les migrations DB

## Story
Foundation — modèle de données

## Description
Remplacer la dépendance à `ddl-auto` par Flyway dès la première entité, pour versionner le schéma PostgreSQL
et éviter d'avoir à reconstituer une migration rétroactive une fois le projet avancé.

> **Note (review TICKET-001)** : les tests tournent aujourd'hui contre un PostgreSQL local réel
> (`localhost:5432`, `adac_user`/`adac_password`, créé à la main pour ce ticket) — non hermétique, échoue
> sur un checkout propre / la machine de Manon / en CI.
>
> **Décision (2026-08-28, ce ticket)** : toujours pas de Testcontainers — Docker n'est pas disponible dans
> l'environnement d'exécution de l'agent, donc impossible de vérifier que ça marche si on l'ajoutait
> maintenant. On garde Postgres local, Flyway applique `V1__init_schema.sql` dessus comme en prod. À
> revisiter au TICKET-012 (pipeline CI), où Docker sera de toute façon disponible.

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `pom.xml` — ajouter `flyway-core` + `flyway-database-postgresql`
- `application.properties` — `spring.flyway.enabled=true`, `spring.jpa.hibernate.ddl-auto=validate`
- `src/main/resources/db/migration/V1__init_schema.sql` — schéma initial correspondant aux entités JPA

## Acceptance criteria
- [ ] L'application démarre avec `ddl-auto=validate` (jamais `update` en production)
- [ ] `V1__init_schema.sql` crée un schéma identique à celui généré précédemment par Hibernate
- [ ] La table `flyway_schema_history` existe après le premier démarrage
- [ ] `mvn test` passe sans régression

## Branch
`feature/setup`
- [ ] Create: `git checkout -b feature/setup`
- [ ] Switch to existing: `git checkout feature/setup`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@SpringBootTest`) : le contexte démarre avec Flyway activé et `ddl-auto=validate`
- [ ] Test 2 : la table `flyway_schema_history` contient bien la migration `V1`

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `chore(db): introduce Flyway for versioned schema migrations`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/setup` — see TICKET-008

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] /postgresql-jpa — pending verification

## Depends on
- TICKET-003 — les entités JPA doivent exister pour écrire la migration initiale

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [x] Done
