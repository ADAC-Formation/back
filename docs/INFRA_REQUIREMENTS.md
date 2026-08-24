# Infrastructure Requirements — Portail de Formation ADAC

## 1. Project Summary

Portail de gestion des formations pour une association (ADAC), remplaçant des échanges email individuels.
Conformité Qualiopi (traçabilité documentaire). Petit projet associatif, backend livré en 2 semaines (priorité).

## 2. Application Components

- Frontend : React + Vite (SPA, servie statiquement)
- Backend : Java Spring Boot 3 (API REST)
- Database : PostgreSQL 16
- Background jobs : `TokenCleanupScheduler` (cron interne au backend, 3h du matin — pas de worker séparé)
- External services : Supabase Storage (documents), Brevo (email prod) / Mailtrap (email dev)

## 3. Expected Usage

- Utilisateurs au lancement : < 100
- Utilisateurs à 6-12 mois : < 300
- Utilisateurs simultanés : faible (dizaines maximum)
- Trafic : faible, pas de pic connu
- Fichiers : documents PDF/formation via Supabase Storage — volumétrie faible (free tier 1 Go jugé suffisant au départ)

## 4. Availability Requirements

- Usage principalement en heures ouvrées
- Interruption ponctuelle (maintenance planifiée) acceptable
- Pas d'exigence zero-downtime
- Pas d'exigence de haute disponibilité / multi-région

## 5. Environments

- Local (dev) — déjà en place (profil `dev`, Postgres local, Mailtrap)
- Production — déjà en place (profil `prod`, Brevo)
- Staging — non requis pour le lancement
- Preview / PR environments — non requis

## 6. Team & Operations

- Équipe : 2 personnes (Charlotte — backend, Manon — frontend)
- Pas d'ingénieur DevOps dédié
- **Projet réalisé bénévolement** (portfolio/expérience) pour une association — aucun contrat de maintenance,
  aucun suivi régulier garanti. Charlotte interviendra ponctuellement, pas en continu.
- Conséquence directe sur l'architecture : l'infrastructure doit rester opérationnelle avec un minimum
  d'intervention manuelle (pas de script de backup à surveiller à la main, pas de patch OS à appliquer soi-même
  si évitable, pas d'astreinte implicite).
- **Priorité explicite : fiabilité / faible maintenance > minimisation stricte du coût.**

## 7. Budget

- L'association doit payer le moins possible — c'est un OBNL, mais ce n'est **pas le critère n°1**.
- Un léger surcoût est acceptable s'il réduit nettement la charge de maintenance (ex. sauvegardes gérées
  automatiquement par la plateforme plutôt qu'un cron à surveiller, mises à jour de sécurité automatiques).
- Cible indicative précédente : ≤ 15 €/mois — reste l'ordre de grandeur souhaité, mais step-05d doit comparer
  explicitement une option "tout self-hosted, coût minimal" et une option "légèrement plus chère, maintenance
  quasi nulle" avant de trancher, plutôt que d'optimiser uniquement le prix.
- Facturation prévisible préférée (prix fixe plutôt qu'à l'usage).

## 8. Data & Compliance

- Données personnelles : oui (stagiaires, formateurs)
- Conformité RGPD requise
- Région : UE obligatoire (déjà acté : Hetzner/Scaleway pour le VPS, Supabase EU pour le storage, Brevo pour l'email)
- Rétention : historique stagiaires conservé 1 an (non confirmé juridiquement — voir Unknowns)
- Backups : requis (aucune sauvegarde définie à ce jour — voir Unknowns section 13/14 pour la fréquence/rétention exacte)
- RPO/RTO : non chiffrés — à définir en step-05d en cohérence avec le budget et la criticité "heures ouvrées"

## 9. Networking & Access

- Application publique (accessible aux stagiaires/formateurs/admin via navigateur)
- Domaine : `adac.asso.fr` déjà possédé par l'ADAC — sous-domaine `portail.adac.asso.fr` déjà anticipé dans la config CORS existante
- Hébergement explicitement **indépendant du NAS interne de l'ADAC** (contrainte PRD)
- Pas de contrainte VPN / allowlist IP mentionnée

## 10. Deployment Requirements

- Fréquence : non définie à ce stade (petite équipe, probablement peu fréquente au départ)
- Automatisation : souhaitable mais non bloquante pour le lancement
- Rollback : pas d'exigence formalisée — à couvrir a minima par un tag d'image versionné
- Fenêtres de maintenance : acceptables (cf. disponibilité heures ouvrées)

## 11. Security Requirements

- TLS obligatoire (l'app est publique et traite des données personnelles)
- Secrets : ne doivent jamais être commités (déjà la convention `.env` / `.env.example`)
- Accès production : limité à Charlotte
- Scanning dépendances/images : non requis pour le lancement, à réévaluer si l'équipe grandit

## 12. Observability Requirements

- Logs applicatifs suffisants pour diagnostiquer un incident
- Pas d'exigence de métriques avancées ni de tracing
- Une vérification de disponibilité basique (uptime check) est suffisante au lancement

## 13. Known Constraints

- Budget modeste mais pas le critère dominant → une option légèrement plus chère et nettement moins
  maintenue manuellement est préférable à l'option la moins chère si elle demande un suivi régulier
- Aucune maintenance continue garantie (projet bénévole) → l'infra doit être aussi "autonome" que possible :
  sauvegardes automatiques, mises à jour de sécurité automatisées quand c'est réaliste au budget
- Équipe de 2 personnes sans DevOps dédié → exclut Kubernetes, multi-service, observabilité lourde
- Hébergement doit rester en dehors du NAS interne de l'ADAC

## 14. Unknowns / Open Questions

- Durée exacte de rétention RGPD (1 an mentionné dans le PRD, à confirmer avec le service juridique de l'ADAC)
- Fréquence et rétention précises des sauvegardes (proposition par défaut en step-05d : quotidienne, rétention 7-14 jours — à valider)
- Fréquence de déploiement cible une fois en production

## 15. Validation Checklist

- [x] Usage, disponibilité, budget clarifiés avec Charlotte (2026-08-24)
- [x] Aucune technologie d'infrastructure choisie dans ce document
- [ ] Validé par Charlotte avant de passer à INFRASTRUCTURE.md

_Généré le 2026-08-24 — révision rétroactive du projet ADAC via step-05c_
