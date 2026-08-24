# START HERE — Portail ADAC

## Reprendre le travail (chaque jour)

1. Ouvrir le terminal dans ce dossier (`ADAC_PROJECT/`)
2. Lancer Claude Code : `claude`
3. Taper `/backend-agent`

L'agent trouve tout seul le prochain ticket backend non terminé, écrit les tests, implémente, committe, marque le ticket Done, et s'arrête avec un rapport.
Il reprend là où il s'est arrêté grâce aux statuts dans `docs/TICKETS.md`.

---

## Ce qui a été supprimé

- `front/` → Manon a son propre repo (voir ci-dessous)
- `front/CLAUDE.md` → idem

Le frontend est géré par **Manon** dans un repo séparé (`org-adac/front`).
Le contrat API qu'elle utilise est dans `docs/tech.md` de ce repo.

---

## Quand Manon aura créé l'organisation GitHub

Elle crée deux repos : `org-adac/back` (toi) et `org-adac/front` (elle).

Dès que ton repo `org-adac/back` est créé, dans ce dossier :

```bash
git init
git add .
git commit -m "chore: initial project setup"
git remote add origin https://github.com/org-adac/back.git
git push -u origin main
```

Ensuite, le workflow normal c'est feature branches → PR → `dev` → `main`.

---

## Ce que tu partages avec Manon

Un seul fichier : **`docs/tech.md`**
C'est le contrat API complet — tous les endpoints, les DTOs, les codes HTTP.
Elle n'a besoin de rien d'autre de ta part pour coder le front.

---

## Structure du projet

```
ADAC_PROJECT/
├── back/               ← ton code Spring Boot (Charlotte)
├── docs/               ← toute la documentation partagée
│   ├── tech.md         ← contrat API → à partager avec Manon
│   ├── TICKETS.md      ← index des tickets (mis à jour par l'agent)
│   ├── tickets/        ← 32 tickets détaillés
│   └── ...             ← ARCHI, DB_MODEL, STACK, DESIGN...
├── nginx/              ← config reverse proxy
├── .claude/
│   └── skills/
│       └── backend-agent.md  ← l'agent backend autonome
├── docker-compose.yml
├── .env.example
└── CLAUDE.md           ← contexte projet pour Claude
```
