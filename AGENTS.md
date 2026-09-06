# Règles communes ADAC — Claude Code et Codex

## Langue

- Les documents du projet, les instructions de skills et d'agents, ainsi que les commentaires sont en français.
- Les messages de commit restent en anglais et respectent Conventional Commits.
- Les noms de fichiers, clés de configuration, commandes, identifiants de code et valeurs attendues par les outils ne sont pas traduits.

## Sources communes à lire

Le workflow backend est défini dans [docs/BACKEND_WORKFLOW.md](docs/BACKEND_WORKFLOW.md). Les connaissances techniques partagées sont dans [docs/ADAC_KNOWLEDGE.md](docs/ADAC_KNOWLEDGE.md). Une reprise utilise aussi [docs/AGENT_HANDOFF.md](docs/AGENT_HANDOFF.md).

Avant un ticket backend, lire les documents du ticket et, selon le sujet, `docs/TICKETS.md`, `docs/STACK.md`, `docs/ARCHI.md`, `docs/tech.md`, `docs/DB_MODEL.mmd` et les documents de conception associés. Les adaptateurs Claude Code et Codex doivent tous deux charger le workflow commun.

## Collaboration et Git

Pour chaque ticket, présenter un plan concret et attendre la validation de Charlotte avant d'exécuter le travail. Le plan indique explicitement les opérations Git prévues : création ou changement de branche, commit, et le cas échéant push et pull request. La validation couvre les opérations annoncées ; ne pas demander à nouveau l'autorisation à chaque étape.

Préserver tout travail étranger au ticket. Ne jamais utiliser une indexation globale : contrôler les fichiers et le contenu exact de l'index avant chaque commit.

## Portée

Un agent backend traite un seul ticket backend, puis s'arrête. Il ne commence ni ticket frontend ni ticket infrastructure/DevOps. Les règles détaillées de sélection, reprise, TDD, revue, documentation, commit et passage de relais sont normatives dans `docs/BACKEND_WORKFLOW.md`.
