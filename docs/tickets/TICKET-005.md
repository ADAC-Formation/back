# TICKET-005 — Repositories + MapStruct mappers

## Story
Foundation — couche accès données et mapping

## Description
Créer les repositories JPA (avec requêtes custom si nécessaire), les DTOs (request + response), et les mappers MapStruct pour toutes les entités. Ces composants sont utilisés par tous les services.

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
**Repositories :**
- `repository/UserRepository.java` — `findByEmail`, `findAllByRole`
- `repository/FormationRepository.java` — `findAllByStatus`, `findAllByFormateur`
- `repository/InscriptionRepository.java` — `findAllByFormation`, `findAllByStagiaire` (corrigé pendant
  l'implémentation — le champ de l'entité s'appelle `stagiaire`, pas `user` ; `findAllByUser` ne compile pas)
- `repository/DocumentRepository.java` — `findAllByFormation`, `findAllByInscription`
- `repository/MessageRepository.java` — requêtes conversations par participant
- `repository/MessageRecipientRepository.java`
- `repository/NotificationRepository.java` — `findAllByRecipientAndDeletedFromBellFalse`,
  `findAllByRecipient` (corrigé — champ `recipient`, pas `user`)
- `repository/ActivationTokenRepository.java` — `findAllByUsedAtIsNotNullOrExpiresAtBefore(OffsetDateTime now)`
  (`OffsetDateTime`, pas `LocalDateTime` — voir review TICKET-003) ; `findByTokenAndType` remplacé par
  `findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc` — l'entité n'a pas de champ `token` en clair
  (`code` a été renommé `codeHash` en review TICKET-003), donc pas de lookup direct par token possible ;
  TICKET-015 récupère le token actif le plus récent pour l'utilisateur puis compare le hash en service

**DTOs request :**
- `dto/request/CreateUserRequest.java` — + `List<Long> formationIds` (nullable, ignoré pour
  POST /users/formateurs) ajouté en review : le body stagiaire de `tech.md` l'inclut, sans lui
  Jackson l'ignore silencieusement et un stagiaire est créé sans aucune formation
- `dto/request/UpdateProfileRequest.java`
- `dto/request/CreateFormationRequest.java` — + `@AssertTrue` `dateFin >= dateDebut` en review :
  sinon la contrainte CHECK en DB renvoie 500 au lieu du 400 attendu par `tech.md`
- `dto/request/SendMessageRequest.java` — **fusionné avec `SendGroupMessageRequest.java`
  en review** : un seul endpoint `POST /messages/send` ne peut pas bind sur deux types
  `@RequestBody` différents ; une seule classe couvre les deux formes (individuel via
  `recipientIds`, groupé via `filter`), la règle "exactement un des deux" est vérifiée en service
  (TICKET-030)
- ~~`dto/request/SendGroupMessageRequest.java`~~ — supprimé, fusionné ci-dessus

Validation ajoutée en review sur `nom`/`prenom`/`email`/`intitule` (`@Size(max=255)`, colonnes
`VARCHAR(255)`) et `content` (`@Size(max=5000)`) — sans ça une valeur trop longue passait la
validation et échouait en 500 à l'insertion plutôt qu'en 400.

**DTOs response :**
- `dto/response/UserResponse.java` — champ `isActive` renommé `active` + `@JsonProperty("isActive")`
  (review, voir note JSON ci-dessous)
- `dto/response/FormationResponse.java`
- `dto/response/InscriptionResponse.java`
- `dto/response/DocumentResponse.java`
- `dto/response/MessageResponse.java` — `isGroup` renommé `group` + `@JsonProperty("isGroup")` ;
  `recipients` défaut `List.of()` (jamais `null`, voir `tech.md` "listes vides")
- `dto/response/ConversationResponse.java`
- `dto/response/NotificationResponse.java` — `isRead` renommé `read` + `@JsonProperty("isRead")`

> **Bug bloquant corrigé en review** : un champ Java nommé `isActive`/`isGroup`/`isRead` sérialise
> en JSON sous la clé `active`/`group`/`read` (Jackson retire le préfixe `is` du getter `isXxx()`),
> cassant silencieusement le contrat `tech.md` côté front. Fix : renommer le champ sans le `is` et
> épingler la clé JSON avec `@JsonProperty`. Couvert par
> `src/test/java/com/adac/portail/dto/BooleanFieldJsonContractTest.java`.

**Mappers :**
- `mapper/UserMapper.java`
- `mapper/FormationMapper.java`
- `mapper/MessageMapper.java`
- `mapper/NotificationMapper.java`

## Acceptance criteria
- [ ] Tous les repositories étendent `JpaRepository<Entity, Long>`
- [ ] Les requêtes JPQL custom sont annotées `@Query` avec des noms explicites
- [ ] `ActivationTokenRepository` expose `findAllByUsedAtIsNotNullOrExpiresAtBefore(LocalDateTime now)` (pour le scheduler)
- [ ] Les mappers MapStruct sont des interfaces annotées `@Mapper(componentModel = "spring")`
- [ ] Les DTOs utilisent `@NotBlank`, `@Email`, `@NotNull` selon le contrat API (`docs/tech.md`)

## Branch
`feature/setup`
- [ ] Create: `git checkout -b feature/setup`
- [x] Switch to existing: `git checkout feature/setup`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@DataJpaTest`): `UserRepository.findByEmail` — retourne l'utilisateur si email connu, empty sinon
- [ ] Test 2 (`@DataJpaTest`): `NotificationRepository.findAllByUserAndDeletedFromBellFalse` — ne retourne pas les notifs avec `deletedFromBell = true`
- [ ] Test 3 (`@DataJpaTest`): `ActivationTokenRepository.findAllByUsedAtIsNotNullOrExpiresAtBefore` — retourne les tokens expirés ET utilisés

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(data): add repositories, DTOs and MapStruct mappers`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/setup` — see TICKET-008

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code
- [x] /jpa-patterns → génère les entités JPA, les repositories et les @Query custom

## Depends on
- TICKET-003 — les entités doivent exister avant de créer les repositories

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [x] Done
