# TICKET-046 — Backend — Entité Category + migration + seed

## Story
[US-017] — Gérer les catégories de formation

## Description
Ajouter la notion de catégorie de formation : nouvelle table `categories` (nom, couleur, actif/inactif —
pas de suppression) et FK `category_id` NOT NULL sur `formations`. Seed des 6 catégories initiales
fournies par la chargée de formation.

`formations` existe déjà (V1, TICKET-004) et peut déjà contenir des lignes en dev. La migration doit donc :
1. Créer `categories` + insérer les 6 lignes de seed
2. Ajouter `formations.category_id` en **nullable**
3. Backfiller les lignes existantes de `formations` sur la première catégorie seedée (id le plus bas)
4. Passer la colonne en `NOT NULL` + ajouter la FK

Couleurs de seed : provisoires (random), à remplacer plus tard par les couleurs officielles envoyées
par le front — ne pas bloquer dessus.

Catégories à seeder (nom exact) :
1. Estime de soi en travail social
2. Méthodologie d'intervention sociale
3. Difficultés budgétaires, surendettement
4. Mieux-être au travail
5. Spécial BCP
6. Formation en intra

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `src/main/resources/db/migration/V2__add_categories.sql` — table `categories` + seed + colonne
  `formations.category_id` (nullable → backfill → NOT NULL + FK), index sur `formations(category_id)`
- `entity/Category.java` — `id`, `nom`, `couleur`, `isActive` (`is_active`, `@Builder.Default = true`),
  `createdAt`, `updatedAt` — même style que `Formation.java` (Lombok `@Getter/@Setter/@Builder`)
- `entity/Formation.java` — ajouter `@ManyToOne(fetch = LAZY) @JoinColumn(name = "category_id", nullable = false) private Category category;`
- `repository/CategoryRepository.java` — `existsByNomIgnoreCase(String)`, `findAllByIsActiveTrue()`

## Acceptance criteria
- [x] `mvn test` exécute `V2__add_categories.sql` sans erreur sur une base déjà migrée en V1
- [x] Après migration, `categories` contient exactement les 6 lignes seedées, chacune avec `couleur`
      au format `#RRGGBB` et `is_active = true`
- [x] `formations.category_id` est `NOT NULL` avec une contrainte FK vers `categories.id`
- [x] `FlywayMigrationTest` (existant) passe toujours — Hibernate reste en mode `validate` uniquement
- [x] `CategoryRepository.existsByNomIgnoreCase("estime de soi en travail social")` → `true`
      (insensible à la casse)
- [x] `CategoryRepository.findAllByIsActiveTrue()` ne retourne pas une catégorie désactivée manuellement en base

> **Ajouté en review (2026-09-04)** : `nom` n'a plus de contrainte `UNIQUE` colonne simple — remplacée
> par un index unique d'expression `UNIQUE INDEX uk_categories_nom_upper ON categories (UPPER(nom))`,
> pour que l'unicité soit réellement insensible à la casse au niveau DB (le pré-check applicatif
> `existsByNomIgnoreCase` seul est un TOCTOU entre deux POST concurrents). Contrainte `CHECK` ajoutée
> sur `couleur` (format `#RRGGBB`) — jusque-là seule une regex DTO (TICKET-047, pas encore écrite)
> l'aurait garanti. Voir `V2__add_categories.sql`.

## Branch
`feature/categories`
- [x] Create: `git checkout -b feature/categories`
- [ ] Switch to existing: `git checkout feature/categories`

## Write tests first (TDD)
Before writing any implementation code:
- [x] Test 1 (`FlywayMigrationTest`, étendre le test existant ou en ajouter un) : après migration,
      `flyway.info().current().getVersion().getVersion()` == `"2"`
- [x] Test 2 (`@DataJpaTest`, `CategoryRepositoryTest`) : les 6 catégories seedées sont présentes,
      avec les noms exacts
- [x] Test 3 (`@DataJpaTest`) : `existsByNomIgnoreCase` insensible à la casse
- [x] Test 4 (`@DataJpaTest`) : `findAllByIsActiveTrue()` exclut une catégorie désactivée
- [x] Test 5 (`@DataJpaTest`, `FormationRepositoryTest` existant) : sauvegarder une `Formation` sans
      `category` → `DataIntegrityViolationException` (colonne NOT NULL)

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(categories): add Category entity, migration and seed data; update docs`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/categories` — see TICKET-047

## Skills to invoke
- [x] /java-springboot
- [x] /spring-boot-test-patterns
- [x] /jpa-patterns
- [x] /postgresql-jpa

## Depends on
- TICKET-004 — Flyway configuré, `V1__init_schema.sql` déjà appliquée

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [x] Done
