# TICKET-013 — Activer unattended-upgrades sur le VPS

## Story
Infrastructure — maintenance automatisée (voir `docs/INFRA_REQUIREMENTS.md` section 6 — projet bénévole, pas
de suivi continu garanti)

## Description
Activer les mises à jour de sécurité automatiques du système sur le VPS, pour que les patchs OS ne dépendent
pas d'une intervention manuelle régulière de Charlotte.

## Repo
Aucun — tâche opérationnelle, pas de code dans le repo.

## Actions à réaliser
- Installer et configurer `unattended-upgrades` sur le VPS (Debian/Ubuntu)
- Limiter aux mises à jour de sécurité (pas de montée de version majeure automatique)
- Programmer un redémarrage automatique si nécessaire, à une heure creuse (nuit)

## Acceptance criteria
- [ ] `unattended-upgrades` installé et activé
- [ ] `unattended-upgrade --dry-run --debug` confirme la configuration active
- [ ] Redémarrage automatique programmé en heure creuse si un patch l'exige

## Branch
Aucune — pas de commit associé à cette tâche.

## Checklist opérationnelle (remplace TDD — pas de code ici)
- [ ] Configuration vérifiée avec `--dry-run`
- [ ] Un patch de sécurité simulé est appliqué sans intervention manuelle

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] /docker — pending verification (contexte VPS/serveur)

## Depends on
- TICKET-002 — le VPS doit être provisionné

## Estimated time
0.5h

## Status
[ ] To do   [ ] In progress   [ ] Done
