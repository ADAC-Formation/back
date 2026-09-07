# Rapport de passation d'un ticket backend

Utiliser ce rapport pour interrompre ou reprendre un ticket entre Claude Code et Codex. Il doit être enregistré avant le commit du ticket, après la mise à jour des critères, tests et statuts. Le hash du commit, lorsqu'il existe, est affiché dans la réponse finale et n'impose pas de modifier ce rapport.

## État à enregistrer

```md
## Ticket <ID> — <titre>

- Outil ayant préparé le rapport : Claude Code | Codex
- Branche et HEAD observés : <branche> / <hash>
- Statut de la fiche et de docs/TICKETS.md : <statut>
- Dépendances vérifiées : <résultat>
- Travail réalisé : <résumé factuel>
- Tests exécutés : <commande et résultat>
- Revue : <portée, constats BLOCKING/CRITICAL/SUGGESTION et corrections>
- Documentation mise à jour : <fichiers>
- État Git observé : <indexé, non indexé, non suivi ; séparer les changements étrangers>
- Étape suivante : <action précise ou raison d'arrêt>
- Risques, divergences ou décisions attendues : <liste>
```

## Reprise

L'agent qui reprend lit ce rapport, puis exécute l'inspection complète définie dans `docs/BACKEND_WORKFLOW.md` : branche, HEAD, index, modifications non indexées, fichiers non suivis, ticket, dépendances et pertinence des résultats de tests. Le rapport ne remplace pas ces vérifications.

Conserver la branche et les changements existants. Préserver les changements étrangers lorsqu'ils ne chevauchent pas le ticket. Demander une décision seulement si les fichiers du ticket ont été modifiés concurremment, si les changements se chevauchent, ou si l'état ne permet pas une reprise fiable. Présenter le plan de reprise et attendre sa validation avant toute action.

## Historique des passations

## Ticket 029 — Messagerie individuelle

- Outil ayant préparé le rapport : Claude Code
- Branche et HEAD observés : feature/messagerie / HEAD initial 5b37f62
- Statut de la fiche et de docs/TICKETS.md : Done (les deux, cohérents)
- Dépendances vérifiées : TICKET-005 (Done)
- Anomalie : le rapport de passation obligatoire n'avait pas été enregistré avant le commit
  5b37f62 (le workflow partagé docs/BACKEND_WORKFLOW.md / docs/AGENT_HANDOFF.md n'existait pas
  encore au moment de ce commit). 5b37f62 n'a pas été amendé ; cette entrée régularise le
  rapport après coup, dans un commit documentaire séparé.
- Travail réalisé : endpoints de messagerie individuelle (GET /api/messages, GET
  /api/messages/{conversationId}, POST /api/messages/send limité à un seul destinataire,
  PATCH /api/messages/{id}/read sur un seul message) ; MessageController, MessageService/Impl,
  NotificationService/Impl (slice minimale en avance sur TICKET-033) ; UnauthorizedException et
  BadRequestException. Revue branch-wide (4 agents) ayant trouvé et corrigé 6 CRITICAL : oracle
  d'énumération d'utilisateurs (404 vs 403 sur destinataire/conversation inconnus, désormais
  indistinguables), contenu de notification pouvant dépasser la colonne DB et notify() couplé à
  la même transaction que l'envoi (découplé via TransactionSynchronization, tronqué à 255
  caractères), N+1 sur GET /api/messages (remplacé par deux requêtes batchées), crash
  Collectors.toMap sur un message multi-destinataires, DTO dupliqué dans sendMessage,
  markAsRead non idempotent.
- Tests exécutés : `mvn test` — 216/216 GREEN (73 nouveaux tests pour ce ticket)
- Revue : review-code branch-wide (clean-code, security, backend, tests — 4 agents Opus) ; les 6
  CRITICAL ci-dessus corrigés et re-testés (GREEN confirmé après correctifs) ; SUGGESTIONS
  partiellement appliquées (élément `null` dans recipientIds → 400, corrigé ; pagination et
  rate-limiting sur l'envoi signalés mais différés, hors périmètre de ce ticket — nécessitent une
  décision de contrat avec Manon pour la pagination).
- Documentation mise à jour : docs/tech.md (§7 Messagerie, §8 sémantique entityId d'une
  notification MESSAGE), docs/ARCHI.md (design final des requêtes batchées, oracle fermé,
  découplage transactionnel), docs/TICKETS.md, docs/tickets/TICKET-029.md (note de révision sur
  l'écart avec le sketch initial, critères d'acceptation cochés) — tous inclus dans 5b37f62.
- État Git observé : commit 5b37f62 (`feat(messaging): add individual messaging endpoints with
  role-based access; update docs`) poussé sur feature/messagerie. Fichiers de configuration
  étrangers au ticket, modifiés/ajoutés en parallèle et laissés hors de ce commit et de ce
  rapport : `.claude/skills/backend-agent/SKILL.md`, `CLAUDE.md` (modifiés), `AGENTS.md`,
  `docs/ADAC_KNOWLEDGE.md`, `docs/AGENT_HANDOFF.md`, `docs/BACKEND_WORKFLOW.md`, `.agents/`
  (ajoutés, non suivis) — coordination Claude Code / Codex apparue en cours de session, sans
  chevauchement avec les fichiers de TICKET-029.
- Étape suivante : aucun nouveau ticket entamé. Reprise à décider par Charlotte (candidat
  identifié mais non démarré : TICKET-030, lui-même dépendant de TICKET-022, bloqué tant que la
  PR catégories #4 n'est pas mergée dans dev).
- Risques, divergences ou décisions attendues :
  - Écart assumé entre le sketch initial de TICKET-029.md (URLs avec préfixe `/conversations`,
    marquage "lu" par conversation entière) et le contrat réel implémenté (docs/tech.md, marquage
    par message unique) — décision validée avec Charlotte en session, documentée dans la note de
    révision du fichier ticket.
  - Les fichiers de coordination Codex listés ci-dessus restent non commités dans ce dépôt local ;
    aucune décision prise sur leur sort par cette intervention.

## Ticket 030 — Backend, Messagerie groupée + filtres

- Outil ayant préparé le rapport : Claude Code
- Branche et HEAD observés : feature/messagerie (recréée depuis dev à jour — l'ancienne
  feature/messagerie, TICKET-029, avait déjà été mergée dans dev via PR #6 entre-temps)
- Statut de la fiche et de docs/TICKETS.md : Done (les deux, cohérents)
- Dépendances vérifiées : TICKET-029 (Done), TICKET-022 (Done)
- Écarts assumés (validés avec Charlotte avant codage) : pas de `POST /api/messages/group` dédié
  — réutilise `POST /api/messages/send` (`filter`) conformément à `docs/tech.md`, déjà pré-posé par
  TICKET-005 (`SendMessageRequest.Filter`, `MessageFilterType`) ; "documents manquants"
  (`MISSING_DOCS`) défini comme "stagiaire sans aucun document ciblé sur aucune inscription",
  faute de notion de "document requis" dans le schéma — voir docs/STORIES.md US-014.
- Travail réalisé : `MessageServiceImpl.sendMessage` étendu (branche `filter`, 3 types :
  FORMATION/MISSING_DOCS/MANUAL), `GET /api/messages/group/preview` ajouté,
  `InscriptionRepository.findStagiairesWithNoDocuments()`. Revue branch-wide (2 agents Opus :
  sécurité, backend+clean-code) ayant trouvé et corrigé 2 BLOCKING identiques indépendamment :
  (1) le filtre FORMATION réimplémentait la règle de visibilité de formation au lieu de réutiliser
  `FormationService.findVisibleFormationOrThrow`, et en la copiant renvoyait 403 au lieu de 404
  pour un ADMIN non-propriétaire — rouvrant l'oracle d'énumération de formations qu'une revue
  précédente (TICKET-019/022) avait fermé ; (2) la résolution des destinataires groupés ne
  repassait jamais par `canMessage` (matrice TICKET-029), laissant un ADMIN atteindre/découvrir
  (preview) un stagiaire désactivé que l'envoi individuel au même destinataire rejette en 403.
  Corrigés en délégant à `findVisibleFormationOrThrow` (STAGIAIRE rejeté avant tout lookup) et en
  filtrant la liste résolue par `canMessage` avant de la retourner. Corrigés aussi : userIds
  dupliqués dans MANUAL (faux 404), aucun test ne prouvait que `MessageResponse.recipients`
  contenait bien tous les destinataires, `GET /group/preview` contournait la validation Bean
  (`@Validated` + `List<@NotNull Long>` ajoutés), pas de handler pour paramètre manquant/invalide
  sur le nouvel endpoint GET (ajoutés à GlobalExceptionHandler).
- Tests exécutés : `mvn test` — 388/388 GREEN (37 nouveaux tests pour ce ticket)
- Revue : review-code branch-wide (sécurité, backend+clean-code — 2 agents Opus) ; les 2 BLOCKING
  et 4 CRITICAL/SUGGESTION notables ci-dessus corrigés et re-testés (GREEN confirmé). Déféré,
  disclosé dans la fiche : fan-out des notifications en N transactions séparées au lieu d'un batch
  — acceptable à l'échelle actuelle, nécessiterait une méthode batch sur NotificationService
  (TICKET-033), hors périmètre ici.
- Documentation mise à jour : docs/tech.md (§7 — endpoint preview, règle 404 vs 403 FORMATION,
  définition MISSING_DOCS), docs/STORIES.md (US-014, note de révision), docs/TICKETS.md,
  docs/tickets/TICKET-030.md (écarts, critères cochés, section revue).
- État Git observé : tous les fichiers du ticket indexés avant commit ; aucun fichier étranger.
  Note technique de session : la DB Postgres locale (persistante, pas recréée par run) portait
  encore la migration V5/`storage_path` d'une session précédente sur feature/documents (non
  mergée) — nettoyée manuellement (colonne + ligne flyway_schema_history) pour retrouver l'état
  attendu de cette branche (V1-V4) ; un `mvn clean` a aussi été nécessaire (target/classes gardait
  le fichier V5 d'un build précédent). Aucun impact sur le code committé.
- Étape suivante : aucun nouveau ticket entamé. Prochain ticket backend admissible dans
  docs/TICKETS.md : TICKET-033 (Notifications CRUD, deps TICKET-005/TICKET-029 toutes Done).
- Risques, divergences ou décisions attendues :
  - Fan-out de notifications non batché (voir ci-dessus) — dette technique disclosée, pas
    d'action requise tant que le volume reste modeste.
  - `feature/documents` (TICKET-026) reste une branche séparée non mergée à ce stade — le ticket
    026 lui-même a son propre rapport de passation sur cette branche.

## Ticket 026 — Backend, Upload Supabase + download

- Outil ayant préparé le rapport : Claude Code
- Branche et HEAD observés : feature/documents / créée depuis dev à jour (post-merge PR #6,
  feature/messagerie — workflow partagé + TICKET-029)
- Statut de la fiche et de docs/TICKETS.md : Done (les deux, cohérents)
- Dépendances vérifiées : TICKET-022 (Done), TICKET-023 (Done)
- Travail réalisé : endpoints `POST/GET/DELETE /api/documents`, `GET /api/documents/{id}/download` ;
  `DocumentController`, `DocumentService`/`Impl`, `StorageService`/`Impl` (HTTP Supabase — écart
  assumé vs la fiche, voir sa note de révision), `FileValidator` (extension + signature magic-bytes
  + taille), `StorageException` (502). Migration V5 (`documents.storage_path`, nullable puis
  backfill puis NOT NULL — DB dev persistante, pas recréée par run). Revue branch-wide (3 agents
  Opus : sécurité, backend, clean-code) ayant trouvé et corrigé : injection de paramètre
  `Content-Disposition` via le nom de fichier brut (BLOCKING), `mimeType` client non validé,
  absence de vérification de contenu (extension seule), `DELETE` ne supprimait jamais l'objet
  Supabase (fuite RGPD), absence de timeout sur le `RestTemplate` + appel externe dans la
  transaction DB, upload non atomique (objet orphelin si l'écriture DB échoue après un upload
  réussi), double encodage d'URL (`exchange(String,...)` ré-encodait une URL déjà encodée —
  `fileUrl` pouvait pointer sur un objet inexistant), N+1 sur `uploadedBy` en liste, incohérence
  entre la règle DELETE codée et `docs/tech.md` (corrigé côté doc, la fiche du ticket faisait déjà
  foi), couverture de test manquante sur le chemin d'autorisation par inscription, auto-notification
  d'un stagiaire déposant sur sa propre inscription, oracle 403 vs 404 pour un ADMIN non-propriétaire
  en lecture (aligné sur la convention `FormationServiceImpl`).
- Tests exécutés : `mvn test` — 425/425 GREEN (68 nouveaux tests pour ce ticket)
- Revue : review-code branch-wide (sécurité, backend, clean-code — 3 agents Opus) ; tous les
  BLOCKING/CRITICAL listés ci-dessus corrigés et re-testés (GREEN confirmé après correctifs).
  Deux points explicitement différés (documentés dans la fiche § "Ajouté en revue") : (1)
  `downloadDocument` garde l'appel Supabase dans sa transaction en lecture seule — le séparer
  proprement demanderait soit un fetch-graph plus profond, soit un second bean pour contourner
  l'auto-invocation Spring, jugé hors budget de ce ticket ; le timeout ajouté borne le risque au
  lieu de l'éliminer ; (2) pas de rate-limiting/quota sur l'upload ni de streaming sur le download
  (mêmes raisons que la pagination différée en TICKET-029 — nécessitent une décision produit).
- Documentation mise à jour : docs/tech.md (§6, règle DELETE), docs/ARCHI.md (StorageException,
  liste des exceptions fusionnée avec dev), docs/DB_MODEL.md/.mmd (storage_path), docs/TICKETS.md,
  docs/tickets/TICKET-026.md (écart SupabaseConfig/StorageService, critères cochés, section revue).
- État Git observé : tous les fichiers du ticket indexés avant commit ; aucun fichier étranger.
- Étape suivante : aucun nouveau ticket entamé. Prochain ticket backend admissible dans
  docs/TICKETS.md : TICKET-030 (Messagerie groupée + filtres, dépendances TICKET-029/022 toutes
  Done) — à confirmer avec Charlotte avant de démarrer.
- Risques, divergences ou décisions attendues :
  - `downloadDocument` et l'absence de rate-limiting/streaming restent des dettes techniques
    disclosées (voir ci-dessus) — pas d'action requise immédiate, à garder en tête si le volume de
    documents ou le trafic de téléchargement grossit.
