# TICKET-030 — Backend — Messagerie groupée + filtres

## Story
[US-014] — Messagerie groupée

## Description
Implémenter l'envoi de messages groupés avec filtres de destinataires. Le SUPER_ADMIN peut filtrer par formation, documents manquants ou sélection libre. Le ADMIN peut filtrer par formation uniquement.

Contrat API (`docs/tech.md`) :
- `POST /api/messages/send` — envoi groupé via `filter` (pas d'endpoint `POST /messages/group`
  séparé — décision : `docs/tech.md` documentait déjà la réutilisation de l'endpoint d'envoi
  individuel, avec `SendMessageRequest.Filter`/`MessageFilterType` pré-posés par TICKET-005 ;
  la fiche ci-dessous, écrite avant ce contrat, décrivait un endpoint dédié — non retenu)
- `GET /api/messages/group/preview` — prévisualiser les destinataires avant envoi (ajouté au
  contrat à cette occasion, absent de `tech.md` à l'origine)

> **Écarts assumés avec cette fiche** (décidés avec Charlotte) :
> 1. Pas de `POST /api/messages/group` : réutilise `POST /api/messages/send` (`filter` au lieu de
>    `recipientIds`), conformément à `docs/tech.md` §7 déjà en place.
>    `filterType` s'appelle `MessageFilterType.MANUAL`, pas `FREE_SELECT` (déjà posé par TICKET-005).
> 2. "Documents manquants" (`MISSING_DOCS`) n'a aucune définition dans le modèle de données (pas de
>    notion de "document requis" par formation) — défini comme : stagiaire dont AUCUNE inscription
>    n'a de document ciblé, toutes formations confondues. Voir `docs/STORIES.md` US-014.

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/MessageController.java` — étendre `sendMessage` (branche `filter`) + ajouter `GET /messages/group/preview`
- `service/MessageService.java` (extend) + `MessageServiceImpl.java` — logique de filtrage, fan-out vers chaque destinataire
- `repository/InscriptionRepository.java` — `findStagiairesWithNoDocuments()` (filtre `MISSING_DOCS`)
- `dto/request/SendMessageRequest.java` — `Filter.userIds` ajouté (au lieu d'un DTO séparé, voir écart ci-dessus)

## Acceptance criteria
- [x] `POST /api/messages/send` (filter) SUPER_ADMIN, filtre FORMATION → un message groupé (1 `Message` +
      N `MessageRecipient`, pas N `Message` séparés — voir `docs/DB_MODEL.md`) créé pour chaque inscrit
- [x] `POST /api/messages/send` (filter) SUPER_ADMIN, filtre MISSING_DOCS → destinataires = stagiaires sans documents ciblés
- [x] `POST /api/messages/send` (filter) SUPER_ADMIN, filtre MANUAL → destinataires = `userIds` fournis
- [x] `POST /api/messages/send` (filter) ADMIN, filtre FORMATION → destinataires = inscrits à sa formation uniquement
- [x] `POST /api/messages/send` (filter) ADMIN, filtre MISSING_DOCS ou MANUAL → 403
- [x] `GET /api/messages/group/preview?filterType=FORMATION&formationId=1` → liste des destinataires sans envoyer
- [x] Chaque destinataire reçoit une notification individuelle

### Ajouté en revue (branch-wide, 2 agents : sécurité, backend+clean-code)
- [x] Filtre FORMATION : ADMIN non-propriétaire → 404 (pas 403), en délégant à
      `FormationService.findVisibleFormationOrThrow` plutôt qu'une copie divergente de la règle
      (rouvrait l'oracle d'énumération de formations qu'une revue précédente avait fermé)
- [x] Résolution des destinataires groupés repassée par `canMessage` (matrice TICKET-029) — sans
      ça, un ADMIN pouvait joindre/découvrir (preview) un stagiaire désactivé via FORMATION alors
      que l'envoi individuel au même destinataire renvoie 403
- [x] `userIds` dupliqués dans `MANUAL` ne provoquent plus un faux 404 (dédupliqué avant `findAllById`)
- [x] Test ajouté prouvant que `MessageResponse.recipients` contient bien tous les destinataires résolus
- [x] `GET /group/preview` : `@Validated` + `List<@NotNull Long> userIds`, pour que la garde
      "élément null" (déjà sur `recipientIds`/`Filter.userIds` côté JSON) s'applique aussi aux
      query params, qui construisent le `Filter` à la main hors du cycle `@Valid`
- [x] Gestion `MissingServletRequestParameterException`/`MethodArgumentTypeMismatchException`/
      `ConstraintViolationException` (jakarta) ajoutée à `GlobalExceptionHandler` — sinon 500 par défaut
- [ ] **Déféré** : fan-out des notifications = N transactions séparées (une par destinataire) au lieu
      d'un batch — acceptable à l'échelle actuelle (formations de 10-30 inscrits), potentiellement
      coûteux pour `MISSING_DOCS` à l'échelle de tout l'organisme ; nécessiterait une méthode batch
      sur `NotificationService`, hors périmètre de ce ticket (voir TICKET-033)

## Branch
`feature/messagerie`
- [ ] Create: `git checkout -b feature/messagerie`
- [x] Switch to existing: `git checkout feature/messagerie`

## Write tests first (TDD)
Before writing any implementation code:
- [x] Test 1 (`@WebMvcTest`): `POST /api/messages/send` (filter FORMATION) SUPER_ADMIN → 201
- [x] Test 2 : ADMIN filtre MANUAL → 403
- [x] Test 3 (`@ExtendWith(MockitoExtension)`): `sendMessage` (filter FORMATION) avec 3 inscrits → 1 message groupé
      (1 `Message` + 3 `MessageRecipient`, voir écart ci-dessus), 3 notifications envoyées
- [x] Test 4 : `previewGroupRecipients` MISSING_DOCS → retourne uniquement les stagiaires sans documents

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(messaging): add group messaging with formation and document filters`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/messagerie` — see TICKET-032

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code
- [x] /jpa-patterns → génère les entités JPA, les repositories et les @Query custom

## Depends on
- TICKET-029 — `MessageService` de base et `MessageRecipient`
- TICKET-022 — `FormationRepository` pour les filtres par formation

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [x] Done
