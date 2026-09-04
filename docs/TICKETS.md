# Tickets — Portail de Formation ADAC

Dernière mise à jour : 2026-08-24
Détail de chaque ticket → `docs/tickets/TICKET-XXX.md`

---

## Phase 1 — Fondation (applicatif + infrastructure)

| ID | Titre | Repo | Dépend de | Temps | Statut |
|---|---|---|---|---|---|
| TICKET-001 | Setup Spring Boot + pom.xml | back | — | 2h | Done |
| TICKET-002 | Provisionner le VPS + DNS | — | — | 1h | À faire |
| TICKET-003 | Entités JPA + enums | back | TICKET-001 | 3h | Done |
| TICKET-004 | Introduire Flyway pour les migrations DB | back | TICKET-003 | 2h | Done |
| TICKET-005 | Repositories + MapStruct mappers | back | TICKET-003 | 2h | Done |
| TICKET-006 | Spring Security + JWT cookie | back | TICKET-003 | 3h | Done |
| TICKET-007 | Config Swagger + Mail + Supabase | back | TICKET-001 | 1h | Done |
| TICKET-008 | Setup React + Vite + Axios | front | — | 2h | À faire |
| TICKET-009 | Dockerfile backend | back | TICKET-001 | 1h | Done |
| TICKET-010 | Coordination Dockerfile frontend (repo externe `front`) | — | — | 0.5h | À faire |
| TICKET-011 | docker-compose.yml + .env.example (production) | both | TICKET-009, TICKET-010 | 2h | Done |
| TICKET-012 | Pipeline CI backend (tests + build image) | back | TICKET-009 | 1.5h | Done |
| TICKET-013 | Activer unattended-upgrades sur le VPS | — | TICKET-002 | 0.5h | À faire |

## Phase 2 — Authentification

| ID | Titre | Repo | Dépend de | Temps | Statut |
|---|---|---|---|---|---|
| TICKET-014 | Auth — login / logout / me | back | TICKET-006 | 2h | Done |
| TICKET-015 | Auth — activation + reset MDP + scheduler | back | TICKET-005, TICKET-007 | 3h | Done |
| TICKET-016 | Frontend — Page de connexion | front | TICKET-008, TICKET-014 | 2h | À faire |
| TICKET-017 | Frontend — Page d'activation | front | TICKET-008, TICKET-015 | 2h | À faire |
| TICKET-018 | Frontend — Mot de passe oublié / reset | front | TICKET-017 | 1h | À faire |
| TICKET-045 | Auth — rate limiting login + routes publiques strictes | back | TICKET-006 | 2h | À faire |

## Phase 2 — Utilisateurs

| ID | Titre | Repo | Dépend de | Temps | Statut |
|---|---|---|---|---|---|
| TICKET-019 | Backend — CRUD Formateurs + Stagiaires | back | TICKET-005, TICKET-015 | 3h | À faire |
| TICKET-020 | Backend — GET /users/me + PATCH /users/me | back | TICKET-019 | 1h | À faire |
| TICKET-021 | Frontend — Gestion des comptes | front | TICKET-019 | 3h | À faire |

## Phase 2 — Catégories

| ID | Titre | Repo | Dépend de | Temps | Statut |
|---|---|---|---|---|---|
| TICKET-046 | Backend — Entité Category + migration + seed | back | TICKET-004 | 2h | À faire |
| TICKET-047 | Backend — CRUD Catégories (create, edit, list, activate/deactivate) | back | TICKET-046, TICKET-005 | 2h | À faire |
| TICKET-048 | Frontend — Sélecteur de catégorie + création à la volée | front | TICKET-047, TICKET-025 | 2h | À faire |
| TICKET-049 | Frontend — Gestion des catégories (liste, éditer, activer/désactiver) | front | TICKET-047 | 2h | À faire |

## Phase 2 — Formations

| ID | Titre | Repo | Dépend de | Temps | Statut |
|---|---|---|---|---|---|
| TICKET-022 | Backend — CRUD Formations + archive + formateur | back | TICKET-005, TICKET-019, TICKET-046, TICKET-047 | 3h | À faire |
| TICKET-023 | Backend — Import Excel + Inscriptions | back | TICKET-022 | 3h | À faire |
| TICKET-024 | Frontend — Liste et détail des formations | front | TICKET-022 | 2h | À faire |
| TICKET-025 | Frontend — Formulaire création + import Excel | front | TICKET-023 | 3h | À faire |

## Phase 2 — Documents

| ID | Titre | Repo | Dépend de | Temps | Statut |
|---|---|---|---|---|---|
| TICKET-026 | Backend — Upload Supabase + download | back | TICKET-022, TICKET-023 | 3h | À faire |
| TICKET-027 | Frontend — Upload + téléchargement docs formation | front | TICKET-026 | 2h | À faire |
| TICKET-028 | Frontend — Upload docs stagiaire (profil) | front | TICKET-027 | 1h | À faire |

## Phase 2 — Messagerie

| ID | Titre | Repo | Dépend de | Temps | Statut |
|---|---|---|---|---|---|
| TICKET-029 | Backend — Messagerie individuelle | back | TICKET-005 | 3h | À faire |
| TICKET-030 | Backend — Messagerie groupée + filtres | back | TICKET-029, TICKET-022 | 2h | À faire |
| TICKET-031 | Frontend — Interface messagerie | front | TICKET-029 | 3h | À faire |
| TICKET-032 | Frontend — Messagerie groupée | front | TICKET-030, TICKET-031 | 2h | À faire |

## Phase 2 — Notifications

| ID | Titre | Repo | Dépend de | Temps | Statut |
|---|---|---|---|---|---|
| TICKET-033 | Backend — Notifications (CRUD + logique) | back | TICKET-005, TICKET-029 | 2h | À faire |
| TICKET-034 | Backend — Service email (JavaMailSender + templates) | back | TICKET-007, TICKET-015 | 3h | À faire |
| TICKET-035 | Frontend — Cloche + page notifications | front | TICKET-033 | 2h | À faire |
| TICKET-036 | Frontend — Toggle notification email | front | TICKET-035, TICKET-020 | 1h | À faire |

## Phase 3 — Mise en production (infrastructure)

| ID | Titre | Repo | Dépend de | Temps | Statut |
|---|---|---|---|---|---|
| TICKET-037 | Reverse proxy Nginx (routes + SPA fallback) | both | TICKET-010, TICKET-011 | 1h | Done |
| TICKET-038 | Configurer TLS (Let's Encrypt / Certbot) | both | TICKET-002, TICKET-037 | 1.5h | À faire |
| TICKET-039 | Retirer l'exposition publique des ports db/backend | both | TICKET-011 | 0.5h | À faire |
| TICKET-040 | Script de sauvegarde automatique + runbook de restauration | both | TICKET-011 | 2h | À faire |
| TICKET-041 | Pipeline CD (déploiement manuel déclenché) | both | TICKET-012, TICKET-002, TICKET-038 | 2h | À faire |
| TICKET-042 | Configurer uptime monitoring | — | TICKET-041 | 0.5h | À faire |

## Phase 3 — Tableaux de bord

| ID | Titre | Repo | Dépend de | Temps | Statut |
|---|---|---|---|---|---|
| TICKET-043 | Frontend — Dashboard 4 tuiles (tous rôles) | front | TICKET-016 | 2h | À faire |
| TICKET-044 | Frontend — Page profil (MDP + infos) | front | TICKET-020, TICKET-036 | 1h | À faire |

---

## Récapitulatif

- **Total tickets** : 49
- **Backend** : 18 | **Frontend (dans ce repo)** : 16 | **Les deux (both)** : 11 | **Aucun / coordination
  (tâche opérationnelle ou cross-repo)** : 4
- **Temps estimé total** : ~83.5h
- **Branches Git** : feature/setup · feature/devops-setup · feature/auth · feature/users · feature/categories
  · feature/formations · feature/documents · feature/messagerie · feature/notifications ·
  feature/devops-production · feature/dashboard
- **Tâches opérationnelles sans branche** (pas de code) : TICKET-002, TICKET-010 (coordination cross-repo),
  TICKET-013, TICKET-042
- **Repo séparé** : le code frontend vit dans `ADAC-Formation/front` (Manon) — ce repo (`back`) ne contient
  ni son code ni ses tickets d'implémentation, seulement `docs/tech.md` comme contrat partagé

## Note sur cette révision (2026-08-31)

Ajout du besoin client "catégories de formation" : `TICKET-046` à `TICKET-049` créés (entité `Category`,
CRUD, sélecteur front, page de gestion). `TICKET-022` mis à jour (dépend désormais aussi de TICKET-046/047 —
`categoryId` obligatoire à la création d'une formation). Nouvelle branche `feature/categories`. Voir
`docs/tickets/TICKET-046.md` à `TICKET-049.md`, et `US-017` dans `STORIES.md`.

## Note sur cette révision (2026-08-28)

`TICKET-045` ajouté suite à la review de code de TICKET-006 : deux failles identifiées par les
agents de review (brute-force non throttlé sur `/api/auth/login`, `permitAll` trop large sur
`/api/auth/**`) et volontairement reportées hors de TICKET-006 — voir `docs/tickets/TICKET-045.md`
§ Origine.

## Note sur cette révision (2026-08-24)

L'ancien `TICKET-032` ("DevOps — Dockerfile + docker-compose + nginx") a été supprimé et remplacé par 13
tickets granulaires (TICKET-002, 004, 009-013, 037-042), générés à partir de `docs/INFRASTRUCTURE.md`
(step-05d) — il ne couvrait ni TLS, ni backups, ni CI/CD, ni monitoring, ni la fermeture des ports publics.
Tous les tickets existants ont été renumérotés en conséquence ; leur contenu (description, critères
d'acceptation, fichiers) n'a pas changé, seul l'ID a été mis à jour.

_Généré le 2026-08-21, révisé le 2026-08-24 avec /new-project (steps 05c/05d rétroactifs)_
