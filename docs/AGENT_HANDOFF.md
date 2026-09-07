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

## Ticket 039/040 — Fermeture des ports publics + sauvegarde automatique/restauration

- Outil ayant préparé le rapport : Claude Code
- Branche et HEAD observés : feature/devops-production, créée depuis dev à jour
- Statut de la fiche et de docs/TICKETS.md : In progress (les deux, cohérents) — pas Done, les
  deux tickets ont des critères d'acceptation nécessitant un VPS réel (TICKET-002, toujours à
  faire) ; voir la note "Status" de chaque fiche pour le détail
- Dépendances vérifiées : TICKET-011 (Done, docker-compose de production)
- Travail réalisé :
  - TICKET-039 : constat que `docker-compose.yml` (TICKET-011) n'exposait déjà aucun `ports:`
    public sur `db`/`backend` — rien à corriger côté fichier, ticket documenté comme vérification
    plutôt que correctif
  - TICKET-040 : `infra/backup.sh` (nouveau — pg_dump via `docker compose exec`, upload Supabase
    Storage, purge > 14 jours) et `docs/RESTORE.md` (nouveau — procédures de restauration test et
    production) ; `.env.example` complété (`SUPABASE_BACKUP_BUCKET`)
- Tests exécutés : `bash -n infra/backup.sh` (syntaxe OK) ; logique de purge (filtre par date +
  pattern de nom) testée isolément avec une fixture `jq` (dumps anciens/récents, noms
  correspondant/ne correspondant pas au format — seul le dump attendu est retenu). Pas de
  `mvn test` — aucun fichier Java modifié par ces deux tickets. Exécution réelle du script et
  restauration réelle **non faites** — nécessitent de vraies credentials Supabase et un VPS
  (voir critères non vérifiables dans les deux fiches).
- Revue : review-code single-agent (script petit, hors périmètre Java) sur `infra/backup.sh` +
  `docs/RESTORE.md` + `.env.example`. 4 BLOCKING corrigés : (1) purge sans allowlist de nom
  pouvant supprimer le bucket documents en cas de mauvaise config — ajout d'un filtre par pattern
  de nom + garde-fou comparant les deux noms de bucket ; (2) clé Supabase passée en argv `curl -H`
  (fuite via `/proc/<pid>/cmdline`) — déplacée dans un fichier de config curl `mktemp`/0600 nettoyé
  par le `trap` ; (3) restauration prod sans `ON_ERROR_STOP`/reset de schéma pouvant laisser une
  base à moitié restaurée en sortant en code 0 — ajout de `DROP SCHEMA`/`CREATE SCHEMA` et
  `-v ON_ERROR_STOP=1` sur les deux procédures (test et prod) ; (4) container de restauration-test
  bindé sur `-p 5433:5432` (toutes interfaces) avec un mot de passe en dur dans le repo — passé à
  `127.0.0.1` uniquement + mot de passe généré via `openssl rand -hex 16`. 7 CRITICAL également
  corrigés : statut HTTP de chaque appel curl vérifié explicitement (list/upload/delete) ; fichiers
  temporaires prévisibles remplacés par `mktemp` + nettoyage étendu à INT/TERM ; timeouts curl
  (`--connect-timeout`/`--max-time`) + `flock` documenté dans la ligne cron ; ligne cron corrigée
  pour ne plus rediriger stderr (laisse passer le mail d'échec cron/MAILTO) ; `source .env`
  remplacé par une extraction `sed` ciblée (pas d'exécution shell du fichier) ; exigence de bucket
  privé documentée en tête de RESTORE.md, limite de chiffrement client disclosed en TODO dans
  backup.sh (hors périmètre — nécessite une gestion de clé). 5 SUGGESTIONS : intégrité `gzip -t`
  ajoutée, jq réécrit en un seul passage vers un tableau JSON (évite `jq -R` et l'injection de
  guillemets), exit code distinct (2) pour un échec de purge isolé du succès du dump. Non
  appliquées (disclosed, effort disproportionné pour ce ticket) : upload en streaming
  (`-T`/PUT) plutôt que `--data-binary`, pagination au-delà de 1000 objets listés.
- Documentation mise à jour : docs/ARCHI.md (arborescence : `infra/backup.sh`, `docs/RESTORE.md`),
  docs/tickets/TICKET-039.md, docs/tickets/TICKET-040.md (constats, critères cochés/non
  vérifiables, statut), docs/TICKETS.md (statuts In progress), docs/AGENT_HANDOFF.md (ce rapport)
- État Git observé : à commiter sur feature/devops-production (aucun changement étranger observé)
- Étape suivante : commit TICKET-039+040 sur feature/devops-production (pas de PR — les deux
  fiches indiquent "This is NOT the last ticket... see TICKET-041"), puis, à la demande explicite
  de Charlotte : vérifier que feature/documents, feature/messagerie et feature/notifications sont
  bien mergées dans dev (elles ne le sont pas encore à ce stade), les merger, lancer `mvn test`
  complet sur dev, puis ouvrir une PR dev → main pour vérifier le passage de la CI GitHub Actions.
- Risques, divergences ou décisions attendues :
  - Les critères d'acceptation les plus significatifs de ces deux tickets (exécution réelle du
    script, vraie restauration testée, vérification réseau externe des ports fermés) restent non
    vérifiables sans VPS (TICKET-002) — statut "In progress" assumé, pas "Done", jusqu'à ce que
    TICKET-002 soit fait.
  - Chiffrement applicatif des dumps (au-delà du chiffrement au repos Supabase) volontairement
    hors périmètre — nécessiterait une décision de gestion de clé avec Charlotte, signalée en TODO
    dans `infra/backup.sh`.
