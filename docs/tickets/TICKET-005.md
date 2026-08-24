# TICKET-005 — Repositories + MapStruct mappers

## Story
Foundation — couche accès données et mapping

## Description
Créer les repositories JPA (avec requêtes custom si nécessaire), les DTOs (request + response), et les mappers MapStruct pour toutes les entités. Ces composants sont utilisés par tous les services.

## Repo
[ ] front/   [x] back/   [ ] both

## Files to create or modify
**Repositories :**
- `repository/UserRepository.java` — `findByEmail`, `findAllByRole`
- `repository/FormationRepository.java` — `findAllByStatus`, `findAllByFormateur`
- `repository/InscriptionRepository.java` — `findAllByFormation`, `findAllByUser`
- `repository/DocumentRepository.java` — `findAllByFormation`, `findAllByInscription`
- `repository/MessageRepository.java` — requêtes conversations par participant
- `repository/MessageRecipientRepository.java`
- `repository/NotificationRepository.java` — `findAllByUserAndDeletedFromBellFalse`, `findAllByUser`
- `repository/ActivationTokenRepository.java` — `findByTokenAndType`, `findAllByUsedAtIsNotNullOrExpiresAtBefore`

**DTOs request :**
- `dto/request/CreateUserRequest.java`
- `dto/request/UpdateProfileRequest.java`
- `dto/request/CreateFormationRequest.java`
- `dto/request/SendMessageRequest.java`
- `dto/request/SendGroupMessageRequest.java`

**DTOs response :**
- `dto/response/UserResponse.java`
- `dto/response/FormationResponse.java`
- `dto/response/InscriptionResponse.java`
- `dto/response/DocumentResponse.java`
- `dto/response/MessageResponse.java`
- `dto/response/ConversationResponse.java`
- `dto/response/NotificationResponse.java`

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
[ ] To do   [ ] In progress   [ ] Done
