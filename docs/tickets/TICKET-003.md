# TICKET-003 — Entités JPA + enums

## Story
Foundation — modèle de données

## Description
Créer les 8 entités JPA correspondant au schéma PostgreSQL (voir `docs/DB_MODEL.mmd`) et tous les enums associés. Appliquer les 4 ajustements validés en review architecture (voir memory `adac-archi-adjustments`).

## Repo
[ ] front/   [x] back/   [ ] both

## Files to create or modify
- `back/src/main/java/com/adac/portail/entity/User.java` — avec `boolean emailNotificationsEnabled = true`
- `back/src/main/java/com/adac/portail/entity/Formation.java` — avec `FormationStatus status` (pas de `boolean archived`)
- `back/src/main/java/com/adac/portail/entity/Inscription.java`
- `back/src/main/java/com/adac/portail/entity/Document.java` — deux FK nullable (`formation` XOR `inscription`)
- `back/src/main/java/com/adac/portail/entity/Message.java`
- `back/src/main/java/com/adac/portail/entity/MessageRecipient.java`
- `back/src/main/java/com/adac/portail/entity/Notification.java` — avec `boolean deletedFromBell`
- `back/src/main/java/com/adac/portail/entity/ActivationToken.java` — avec `TokenType type` et `LocalDateTime usedAt` (nullable)
- `back/src/main/java/com/adac/portail/entity/enums/Role.java` — `SUPER_ADMIN`, `ADMIN`, `STAGIAIRE`
- `back/src/main/java/com/adac/portail/entity/enums/FormationStatus.java` — `ACTIVE`, `ARCHIVED`
- `back/src/main/java/com/adac/portail/entity/enums/Modalite.java` — `VISIO`, `PRESENTIEL`, `MIXTE`
- `back/src/main/java/com/adac/portail/entity/enums/TokenType.java` — `ACCOUNT_ACTIVATION`, `PASSWORD_RESET`
- `back/src/main/java/com/adac/portail/entity/enums/NotificationType.java` — `MESSAGE`, `DOCUMENT`, `FORMATION`
- `back/src/main/resources/db/migration/V1__init_schema.sql` — si Flyway configuré, sinon `schema.sql`

## Acceptance criteria
- [ ] Toutes les entités compilent sans erreur
- [ ] `Document` a deux FK nullable (`formation_id`, `inscription_id`) — exactement une non-null (contrainte CHECK en SQL)
- [ ] `ActivationToken.usedAt` est `LocalDateTime` nullable (pas un boolean)
- [ ] `Formation.status` est `@Enumerated(EnumType.STRING)` de type `FormationStatus`
- [ ] `User.emailNotificationsEnabled` est initialisé à `true`
- [ ] Les entités correspondent au schéma `docs/DB_MODEL.mmd` (colonnes, types, FK)

## Branch
`feature/setup`
- [ ] Create: `git checkout -b feature/setup`
- [x] Switch to existing: `git checkout feature/setup`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@DataJpaTest`): sauvegarder et retrouver un `User` — vérifier `emailNotificationsEnabled = true` par défaut
- [ ] Test 2 (`@DataJpaTest`): sauvegarder une `Formation` avec `status = ACTIVE` — retrouver par status
- [ ] Test 3 (`@DataJpaTest`): sauvegarder un `Document` lié à une formation — vérifier que `inscription` est null

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(entities): add JPA entities and enums for all domain models`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/setup` — see TICKET-008

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code
- [x] /jpa-patterns → génère les entités JPA, les repositories et les @Query custom

## Depends on
- TICKET-001 — Spring Boot project must exist before adding entities

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [ ] Done
