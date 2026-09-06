---
name: backend-agent
description: Traite ou reprend un seul ticket backend ADAC en appliquant le workflow partagé, avec TDD, revue, documentation et passation.
---

# Backend Agent ADAC — adaptateur Codex

Lire, dans cet ordre, `AGENTS.md`, `docs/BACKEND_WORKFLOW.md`, `docs/ADAC_KNOWLEDGE.md` et, pour une reprise, `docs/AGENT_HANDOFF.md`. Ces documents sont normatifs ; ce fichier ne les résume pas et ne les affaiblit pas.

Le numéro de ticket et les consignes sont ceux explicitement présents dans le message de l'utilisateur. S'il n'en donne pas, appliquer la sélection du premier ticket admissible de `docs/BACKEND_WORKFLOW.md`. Ne dépendre d'aucune substitution d'argument.

Présenter le plan du ticket et attendre la validation avant toute exécution. Utiliser le skill `review-code` installé et les capacités de revue disponibles dans Codex, sans exiger de modèle Claude. Signaler toute capacité de revue manquante au lieu de l'omettre.

Ne traiter qu'un ticket backend, puis produire le rapport demandé et s'arrêter.
