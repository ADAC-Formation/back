# Workflow backend ADAC partagé

Ce document est la source de vérité du workflow utilisé par les adaptateurs `backend-agent` de Claude Code et de Codex. Il traite **un seul ticket backend**, puis s'arrête.

## 1. Inspection avant toute action

Avant de sélectionner, reprendre ou modifier un ticket, inspecter :

1. la branche courante et `HEAD` ;
2. les modifications indexées, non indexées et les fichiers non suivis ;
3. le ticket visé, son statut et ses dépendances ;
4. les résultats de tests disponibles, leur date et leur pertinence par rapport au code actuel.

Lire ensuite les documents pertinents : `docs/TICKETS.md`, la fiche du ticket, `docs/STACK.md`, `docs/ARCHI.md`, `docs/tech.md`, `docs/DB_MODEL.mmd`, `docs/ADAC_KNOWLEDGE.md` et les documents de conception du domaine. Claude Code lit aussi `CLAUDE.md`, qui reste son point d'entrée et son supplément propre à Claude.

Ne pas prendre un ancien rapport GREEN, un statut `Done` ou un rapport de passation pour une preuve suffisante : vérifier l'état réel du code et des tests. Pour corriger un défaut déjà implémenté, créer d'abord un test qui démontre le défaut ; ne pas fabriquer artificiellement un RED pour du code qui existe déjà.

Lors d'une reprise, conserver la branche et le travail existants. Ne pas repartir automatiquement de `dev`. Les modifications étrangères au ticket n'empêchent pas le travail si elles peuvent être préservées sans conflit. Arrêter et demander une décision seulement en cas de chevauchement avec les fichiers du ticket, de modification concurrente de ces fichiers, ou d'incertitude réelle.

## 2. Choix d'un ticket admissible

Lire le numéro et les consignes éventuellement fournis dans le message de l'utilisateur. Si aucun ticket précis n'est demandé, sélectionner le premier ticket actuellement admissible dans `docs/TICKETS.md` ; ne jamais utiliser une liste historique codée en dur.

Un ticket est admissible s'il appartient au backend, que son statut réel dans l'index est à réaliser ou en cours (notamment `À faire` ou `In progress`) et que toutes ses dépendances sont terminées. Exclure les tickets frontend et infrastructure/DevOps, même s'ils apparaissent dans l'index. Vérifier les dépendances dans les fiches, pas seulement leur position dans la liste.

Ne pas déduire d'une divergence entre le code et un document que les exigences métier peuvent changer. Si une contradiction touche les critères d'acceptation, le contrat API, la sécurité ou la règle métier, la signaler et demander une décision.

## 3. Plan et autorisation

Avant l'exécution, présenter le ticket choisi, les dépendances vérifiées, les fichiers probablement concernés, les tests RED et GREEN prévus, les documents à mettre à jour et les opérations Git prévues. Attendre la validation de Charlotte.

La validation autorise les opérations explicitement décrites dans ce plan. Si un commit, un push ou une pull request ne figure pas dans le plan validé, le laisser en attente et l'indiquer dans le rapport. Ne pas changer de branche, committer, pousser ou ouvrir une PR sans cette autorisation.

## 4. Branche et compétences techniques

Pour un nouveau travail validé, partir de `dev`, jamais de `main` : mettre `dev` à jour en avance rapide lorsque l'arbre de travail le permet, puis créer ou utiliser la branche du ticket. Pour une reprise, garder la branche existante.

Avant d'écrire du code, charger les skills techniques utiles au ticket, au minimum `java-springboot` et `spring-boot-test-patterns` pour un ticket Spring Boot, puis `jpa-patterns`, `postgresql-jpa`, ou d'autres skills uniquement si le sujet le demande. Lire `docs/ADAC_KNOWLEDGE.md` avant toute implémentation afin d'appliquer les ajustements ADAC pertinents.

## 5. TDD obligatoire

1. Lire `docs/STACK.md` pour identifier le framework de test.
2. Écrire le test ciblé avant l'implémentation.
3. Exécuter le test et confirmer le RED.
4. Écrire l'implémentation minimale.
5. Exécuter le test et confirmer le GREEN.
6. Refactoriser seulement si nécessaire, puis relancer les tests concernés.

Les tests doivent compiler et échouer pour la raison attendue avant l'implémentation. S'ils passent immédiatement, vérifier qu'ils exercent réellement le comportement demandé et les réécrire si nécessaire ; ne pas considérer un GREEN immédiat comme une preuve du cycle TDD.

Après trois tentatives infructueuses sur le même problème, s'arrêter. Fournir l'erreur complète, les tentatives faites et des solutions proposées ; ne pas poursuivre par tâtonnement.

## 6. Documentation et exigences

Mettre à jour la documentation concernée par le ticket : contrat dans `docs/tech.md`, architecture dans `docs/ARCHI.md`, modèle dans `docs/DB_MODEL.mmd`, variables dans les documents concernés et fiche du ticket. Une divergence de détail technique démontrée comme obsolète peut justifier une mise à jour documentaire si elle ne change ni les critères d'acceptation ni une exigence métier. Dans les autres cas, signaler la contradiction et attendre une décision.

## 7. Revue obligatoire

Exécuter le workflow installé `review-code` avant de terminer le ticket. Conserver sa structure : cadrage du diff, revues spécialisées lorsqu'elles sont disponibles, consolidation des constats et application des critères de sévérité `BLOCKING`, `CRITICAL` et `SUGGESTION`.

La revue couvre, selon les fichiers touchés :

- Java et architecture des packages ;
- SQL, migrations, JPA et contraintes de données ;
- sécurité, authentification, autorisation, secrets, CORS et cookies ;
- contrat et erreurs API ;
- tests ;
- documentation et cohérence avec le contrat.

Utiliser les agents ou outils spécialisés disponibles dans l'environnement de l'outil courant, sans exiger un modèle Claude. Si une référence, un agent spécialisé ou une capacité nécessaire au workflow `review-code` est indisponible, le signaler explicitement : la vérification ne devient pas facultative et le ticket ne peut pas être déclaré terminé comme si elle avait eu lieu.

Corriger tous les problèmes `BLOCKING` et `CRITICAL`. Après chaque correction issue de revue, relancer les tests pertinents puis la suite Maven (`mvn test`) avant de déclarer le ticket terminé. Les `SUGGESTION` sont rapportées avec leur justification si elles ne sont pas appliquées.

## 8. Statuts, passation et commit

Ne marquer `Done` que lorsque les critères d'acceptation et toutes les vérifications requises sont satisfaits. Avant le commit, dans cet ordre :

1. mettre à jour les critères et les tests dans la fiche du ticket ;
2. mettre à jour son statut dans la fiche et dans `docs/TICKETS.md` ;
3. enregistrer le rapport de passation dans `docs/AGENT_HANDOFF.md` ;
4. indexer uniquement les fichiers du ticket ;
5. contrôler le diff indexé et vérifier que les statuts et le rapport y figurent.

Le message de commit est en anglais, au format Conventional Commits. Après un commit réellement créé, afficher son hash dans la réponse finale ; ne pas modifier le rapport uniquement pour y ajouter ce hash. Si le commit attend une autorisation, le dire clairement et ne pas inventer de hash.

Si le ticket prévoit une revue de branche et une PR, effectuer après le commit une revue de l'ensemble de la branche, corriger les constats bloquants ou critiques, puis proposer le push et la PR vers `dev` dans le plan validé.

## 9. CI et rapport final

La CI actuelle se déclenche pour les PR vers `main`, alors que le workflow prévoit des PR vers `dev`. Ne pas présenter une PR vers `dev` comme validée par la CI tant que cet écart n'est pas corrigé. Ce signalement ne modifie pas la CI.

Le rapport final indique les tests et leur résultat, la revue et ses constats, la documentation et les statuts mis à jour, les opérations Git effectuées ou en attente, et le prochain ticket admissible sans le commencer. Ensuite, s'arrêter.
