# TICKET-042 — Configurer uptime monitoring

## Story
Infrastructure — observabilité (voir `docs/INFRASTRUCTURE.md` section 20)

## Description
Configurer une vérification de disponibilité externe et gratuite, avec alerte email à Charlotte en cas de
panne — suffisant pour la criticité "heures ouvrées" du projet.

## Repo
Aucun — tâche opérationnelle (compte externe UptimeRobot ou équivalent), pas de code dans le repo.

## Actions à réaliser
- Créer un compte UptimeRobot (ou équivalent gratuit)
- Configurer un check HTTP sur `https://portail.adac.asso.fr/actuator/health`, intervalle 5 min
- Configurer l'alerte email vers l'adresse de Charlotte

## Acceptance criteria
- [ ] Check actif toutes les 5 minutes
- [ ] Une coupure simulée (arrêt du backend) déclenche une alerte email dans les 5-10 minutes
- [ ] Le check repasse au vert automatiquement une fois le service rétabli

## Branch
Aucune — pas de commit associé à cette tâche.

## Checklist opérationnelle (remplace TDD — pas de code ici)
- [ ] Check configuré et actif
- [ ] Test réel : couper le backend → alerte reçue

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] (aucun skill de code nécessaire)

## Depends on
- TICKET-041 — l'application doit être déployée pour avoir une URL à surveiller

## Estimated time
0.5h

## Status
[ ] To do   [ ] In progress   [ ] Done
