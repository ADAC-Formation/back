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

## Ticket 033 — Backend, Notifications (CRUD + logique)

- Outil ayant préparé le rapport : Claude Code
- Branche et HEAD observés : feature/notifications, créée depuis dev à jour (dev ne contient à ce
  stade ni TICKET-026 (feature/documents) ni TICKET-030 (feature/messagerie), toutes deux encore
  sur leur propre branche non mergée)
- Statut de la fiche et de docs/TICKETS.md : Done (les deux, cohérents)
- Dépendances vérifiées : TICKET-005 (Done), TICKET-029 (Done)
- Écarts assumés (tech.md prime, même règle que TICKET-029/030) : endpoint cloche `/unread` (pas
  `/bell`), réponse `{count, notifications}` ; `DELETE /api/notifications/{id}` sans suffixe
  `/bell` ; `PATCH /api/notifications/read-all` ajouté (absent de la fiche, déjà dans tech.md) ;
  `notify(...)` garde sa signature réelle (TICKET-029) ; enum `NotificationType` réel. Un vrai
  choix métier tranché avec Charlotte avant codage : 404 (pas 403 comme écrit dans la fiche) pour
  l'accès à la notification d'un autre utilisateur, cohérent avec l'oracle déjà fermé ailleurs.
- Travail réalisé : `NotificationController` (nouveau), `NotificationService`/`Impl` étendus
  (`getNotifications`, `getUnread`, `markAsRead`, `markAllAsRead`, `deleteFromBell`),
  `NotificationRepository` (requêtes cloche/historique/filtre + bulk update `read-all`),
  `UnreadNotificationsResponse` (nouveau DTO). Revue branch-wide (2 agents Opus : sécurité,
  backend+clean-code) — relancée une fois après un échec initial pour rate-limit de session
  (reset atteint le lendemain). 0 BLOCKING, mais plusieurs CRITICAL corrigés : historique et
  cloche non bornés (jamais de purge réelle, `DELETE` n'est qu'un flag) → cappés à 200/50 lignes ;
  aucun log sur l'unique frontière d'autorisation de la fonctionnalité (vérification
  d'appartenance) → WARN ajouté ; tests contrôleur matchaient le principal avec `any()` au lieu de
  `eq(currentPrincipal())` (convention `WithMockAdacUser`) → corrigé, critique ici puisqu'il n'y a
  aucune autre couche d'autorisation sur ces endpoints. Vérification d'appartenance poussée dans
  la requête (`findByIdAndRecipient`) plutôt qu'un fetch + comparaison Java. `@Modifying` du bulk
  `read-all` complété avec `flushAutomatically = true`. `MethodArgumentTypeMismatchException`
  géré dans `GlobalExceptionHandler` (`?read=abc` → 400 propre).
- Tests exécutés : `mvn test` — 389/389 GREEN (33 nouveaux tests pour ce ticket, dont ceux ajoutés
  en revue)
- Revue : review-code branch-wide (sécurité, backend+clean-code — 2 agents Opus, relancés après un
  rate-limit) ; tous les CRITICAL corrigés et re-testés (GREEN confirmé). Déféré, disclosé dans la
  fiche : le `content` d'une notification vient de `nom`/`prenom` utilisateur sans restriction de
  caractères (TICKET-019) et est maintenant exposé au frontend — risque XSS stocké si le frontend
  rend ce champ en HTML brut plutôt qu'en texte ; à coordonner avec Manon, hors périmètre ici.
- Documentation mise à jour : docs/tech.md (§8 — endpoints `/unread`, `/read-all`, `DELETE` sans
  suffixe, plafonds 200/50), docs/ARCHI.md (`UnreadNotificationsResponse`), docs/TICKETS.md,
  docs/tickets/TICKET-033.md (écarts, critères cochés, section revue).
- État Git observé : tous les fichiers du ticket indexés avant commit ; aucun fichier étranger.
- Étape suivante : aucun nouveau ticket entamé. Plus de ticket backend admissible restant dans
  docs/TICKETS.md hors branches déjà ouvertes non mergées (feature/documents — TICKET-026,
  feature/messagerie — TICKET-030) et tickets infra/frontend hors périmètre de cet agent —
  TICKET-034 (Service email) dépend de TICKET-007 (Done) et TICKET-015 (Done), donc admissible ;
  à confirmer avec Charlotte.
- Risques, divergences ou décisions attendues :
  - Risque XSS stocké via `nom`/`prenom` non restreints, disclosé ci-dessus — décision produit à
    prendre avec Charlotte/Manon, pas tranchée par cette intervention.
  - Trois branches backend actives en parallèle non mergées (feature/documents, feature/messagerie,
    feature/notifications) — aucune PR ouverte sur aucune des trois à ce stade, sur décision de
    Charlotte (attend explicitement avant de pousser).

## Ticket 034 — Backend, Service email (JavaMailSender + templates)

- Outil ayant préparé le rapport : Claude Code
- Branche et HEAD observés : feature/notifications (même branche que TICKET-033 — fiche : "Switch
  to existing", pas "Create")
- Statut de la fiche et de docs/TICKETS.md : Done (les deux, cohérents)
- Dépendances vérifiées : TICKET-007 (Done), TICKET-015 (Done)
- Décision validée avec Charlotte avant codage : migration de `ActivationServiceImpl` (texte brut
  via `MailSender`/`SimpleMailMessage`) vers `EmailService` (HTML) incluse dans ce ticket, bien
  qu'absente de la liste "Files to create or modify" d'origine — `docs/ARCHI.md` l'anticipait déjà
  explicitement. `EmailService.sendActivationEmail`/`sendPasswordResetEmail` lèvent toujours
  `MailException` sur échec SMTP exactement comme l'ancien appel direct, donc toute la logique
  anti-oracle existante (rate-limit, `catch (MailException)`) n'a pas changé de comportement.
- Travail réalisé : `EmailService`/`Impl` (nouveau), `EmailTemplateBuilder` (nouveau, HTML brandé
  ADAC), migration de `ActivationServiceImpl` (~20 tests adaptés). Revue branch-wide (2 agents
  Opus : sécurité, backend+clean-code) — 0 BLOCKING, converge sur 1 CRITICAL commun : `prenom`/
  `content` interpolés dans le HTML sans échappement (latent aujourd'hui — `prenom` réglable
  SUPER_ADMIN uniquement, `sendNotificationEmail` sans appelant en prod — mais le test d'origine
  bénissait explicitement l'absence d'échappement comme "hors périmètre TICKET-033", ce qui
  aurait activement induit en erreur le futur câblage). Corrigé avec `HtmlUtils.htmlEscape`.
  Corrigés aussi : tests `EmailServiceImplTest` qui ne vérifiaient que l'appel à `send()` sans le
  contenu réel (sujet/destinataire/from/Content-Type) ; le wrap `MessagingException →
  MailPreparationException` n'avait aucun test alors qu'il conditionne l'anti-oracle de
  `ActivationServiceImpl` ; `if/else` sur `TokenType` remplacé par un `switch` exhaustif ;
  duplication du gabarit HTML supprimée ; email loggé via l'objet exception contredisant la
  politique déjà énoncée dans le fichier ; test ajouté liant le code emailé au hash stocké.
- Tests exécutés : `mvn test` — 403/403 GREEN (14 nouveaux tests pour ce ticket)
- Revue : review-code branch-wide (sécurité, backend+clean-code — 2 agents Opus) ; tous les
  CRITICAL corrigés et re-testés (GREEN confirmé). Déféré, disclosé dans la fiche : l'appel SMTP
  reste dans la transaction `@Transactional` de `resendActivation`/`forgotPassword` au lieu du
  pattern `afterCommit` déjà utilisé ailleurs (`UserServiceImpl`, `MessageServiceImpl`) —
  pré-existant, pas une régression, remaniement plus large que ce ticket.
- Documentation mise à jour : docs/ARCHI.md (migration `ActivationServiceImpl` actée),
  docs/TICKETS.md, docs/tickets/TICKET-034.md (décision, critères cochés, section revue).
- État Git observé : tous les fichiers du ticket indexés avant commit ; aucun fichier étranger.
- Étape suivante : aucun nouveau ticket entamé. Plus de ticket backend admissible restant hors
  branches déjà ouvertes non mergées (feature/documents — TICKET-026, feature/messagerie —
  TICKET-030) et tickets infra/frontend hors périmètre de cet agent. `feature/notifications`
  (TICKET-033 + TICKET-034) est la seule des trois qui pourrait être proposée en PR maintenant si
  Charlotte le souhaite — TICKET-036 (frontend) est le prochain sur cette branche.
- Risques, divergences ou décisions attendues :
  - `sendNotificationEmail` n'a toujours aucun appelant en production — décision de câblage
    (TICKET-033/036) à prendre séparément, avec l'échappement HTML désormais en place pour cette
    éventualité.
  - Refactor `afterCommit` de l'envoi SMTP différé (voir ci-dessus) — dette technique disclosée,
    pas bloquante.
