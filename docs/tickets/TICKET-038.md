# TICKET-038 — Configurer TLS (Let's Encrypt / Certbot)

## Story
Infrastructure — mise en production (voir `docs/INFRASTRUCTURE.md` section 11)

## Description
Ajouter la terminaison TLS via Let's Encrypt/Certbot — corrige l'incohérence précédente où le port 443 était
exposé sans configuration TLS réelle (Nginx n'avait qu'un bloc `listen 80;`).

## Repo
[ ] front/   [ ] back/   [x] both

## Files to create or modify
- `nginx/nginx.conf` — ajouter le bloc `listen 443 ssl;` + redirection `listen 80;` → HTTPS
- `nginx/certbot/` — volume pour les certificats
- `docker-compose.yml` — service ou job Certbot pour l'émission/le renouvellement

## Acceptance criteria
- [ ] `https://portail.adac.asso.fr` répond avec un certificat valide
- [ ] `http://portail.adac.asso.fr` redirige (301) vers `https://`
- [ ] Renouvellement automatique configuré (cron Certbot)
- [ ] `certbot renew --dry-run` réussit

## Branch
`feature/devops-production`
- [ ] Create: `git checkout -b feature/devops-production`
- [ ] Switch to existing: `git checkout feature/devops-production`

## Write tests first (TDD)
> Pour TLS, les "tests" sont des smoke tests reproductibles.

Before finishing:
- [ ] `curl -I https://portail.adac.asso.fr` → certificat valide, non expiré
- [ ] `curl -I http://portail.adac.asso.fr` → 301 vers HTTPS
- [ ] `certbot renew --dry-run` → succès

## Pre-commit review
Once smoke tests pass, run `/review-code` on the Nginx/Certbot config.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `chore(devops): configure Let's Encrypt TLS with auto-renewal`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/devops-production` — see TICKET-041

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [ ] /nginx — pending verification

## Depends on
- TICKET-002 — DNS doit pointer vers le VPS (validation Let's Encrypt par domaine)
- TICKET-037 — reverse proxy en place

## Estimated time
1.5h

## Status
[ ] To do   [ ] In progress   [ ] Done
