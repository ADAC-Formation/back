---
name: backend-agent
description: Traite ou reprend un seul ticket backend ADAC selon le workflow partagé, avec TDD, revue, documentation et passation.
---

# Backend Agent ADAC — adaptateur Claude Code

Lire, dans cet ordre, `CLAUDE.md`, `AGENTS.md`, `docs/BACKEND_WORKFLOW.md`, `docs/ADAC_KNOWLEDGE.md` et, pour une reprise, `docs/AGENT_HANDOFF.md`. Ces documents conservent les règles du backend-agent historique et sont normatifs.

Le numéro de ticket et les consignes viennent du message de l'utilisateur. En l'absence de numéro explicite, appliquer la sélection du premier ticket admissible définie dans `docs/BACKEND_WORKFLOW.md`.

Présenter le plan et attendre la validation de Charlotte avant toute exécution. Utiliser le workflow installé `review-code` pour la revue obligatoire. Traiter un seul ticket backend, produire le rapport final, puis s'arrêter.
