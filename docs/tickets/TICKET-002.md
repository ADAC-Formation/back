# TICKET-002 — Provisionner le VPS + DNS

## Story

Infrastructure — hébergement (voir `docs/INFRASTRUCTURE.md` section 9-10)

## Description

Provisionner le VPS EU (Hetzner CX22 ou Scaleway DEV1-S — 2 vCPU / 4 Go RAM) qui hébergera les 3 containers,
et pointer le sous-domaine `portail.adac.asso.fr` vers son IP.

## Repo

Aucun — tâche opérationnelle, pas de code dans le repo.

## Actions à réaliser

- Créer le VPS (région UE) chez Hetzner ou Scaleway
- Générer une paire de clés SSH dédiée, désactiver l'authentification par mot de passe
- Configurer le firewall : ouvrir uniquement 22 (SSH), 80, 443 — tout le reste fermé
- Créer l'enregistrement DNS A/AAAA `portail.adac.asso.fr` → IP du VPS (chez le gestionnaire DNS de l'ADAC)

## Acceptance criteria

- [ ] Connexion SSH possible uniquement par clé (mot de passe désactivé)
- [ ] `dig portail.adac.asso.fr` résout vers l'IP du VPS
- [ ] Seuls les ports 22/80/443 sont ouverts (vérifié avec `nmap` depuis l'extérieur)
- [ ] OS à jour (`apt update && apt upgrade` exécuté une première fois)

## Branch

Aucune — pas de commit associé à cette tâche.

## Checklist opérationnelle (remplace TDD — pas de code ici)

- [ ] VPS accessible en SSH par clé
- [ ] DNS propagé et vérifié (`dig`/`nslookup`)
- [ ] Firewall vérifié depuis une machine externe

## Skills to invoke

> Auto-populated by step-09. Do not edit manually.

- [ ] /docker — pending verification (utile pour la suite de la configuration du VPS)

## Depends on

- —

## Estimated time

1h

## Status

[ ] To do [ ] In progress [ ] Done
