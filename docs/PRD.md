# PRD — Portail de Formation ADAC

## Résumé
Portail de gestion des formations pour l'ADAC (association proposant des formations professionnelles en travail social et promotion de la santé mentale). Il remplace les échanges individuels par email par une plateforme centralisée où la chargée de formation publie les formations, dépose les documents et communique avec les stagiaires — en conformité avec les critères Qualiopi.

## Problème
La chargée de formation envoie actuellement les documents individuellement par email à chaque stagiaire, ce qui est chronophage et difficile à tracer. La certification Qualiopi exige une conformité rigoureuse (traçabilité des documents, historique des échanges, etc.). Un portail centralisé élimine ces frictions et garantit la conformité.

## Utilisateurs cibles
- **Super Admin — Chargée de Formation** : contrôle total de la plateforme (formations, comptes, documents, messages)
- **Admin — Formateurs** : accès à leurs formations et stagiaires, messagerie groupée
- **Utilisateur — Stagiaires** : accès en lecture à leurs formations et documents, messagerie avec l'admin et les formateurs

## Fonctionnalités V1 (indispensables)

### Authentification et comptes
- [ ] Connexion par email + mot de passe pour tous les rôles
- [ ] Création de comptes uniquement par le Super Admin (pas d'auto-inscription)
- [ ] Activation de compte par email : à la création, le stagiaire reçoit un lien → code de vérification (valide 30 min) + saisie du nouveau mot de passe
- [ ] Mot de passe oublié : même flux (email → code → nouveau mot de passe)
- [ ] Validation de la force du mot de passe côté client et serveur
- [ ] Gestion des rôles : SUPER_ADMIN / ADMIN / STAGIAIRE

### Gestion des catégories (Super Admin uniquement)
- [ ] Créer une catégorie : nom (obligatoire, unique) + couleur au format `#RRGGBB` (obligatoire)
- [ ] Modifier le nom et/ou la couleur d'une catégorie existante (ex : faute de frappe)
- [ ] Activer / désactiver une catégorie — **pas de suppression possible**
- [ ] Une catégorie désactivée disparaît du sélecteur de création de formation, mais reste inchangée
      sur les formations qui la référencent déjà (y compris archivées)
- [ ] Catégories initiales à créer en base : Estime de soi en travail social, Méthodologie
      d'intervention sociale, Difficultés budgétaires / surendettement, Mieux-être au travail,
      Spécial BCP, Formation en intra

### Gestion des formations (Super Admin uniquement)
- [ ] Créer une formation via formulaire :
  - Intitulé (obligatoire)
  - Description (optionnel)
  - Date de début / Date de fin (obligatoires)
  - Catégorie (obligatoire) : bouton "Catégorie" → sélection parmi les catégories actives ; bouton
    "Créer nouvelle catégorie" à côté pour en ajouter une à la volée (nom + couleur) sans quitter le formulaire
  - Formateur : liste déroulante des formateurs actifs uniquement (optionnel — si vide : Super Admin assigné automatiquement)
  - Modalité : visio / présentiel / mixte (obligatoire)
  - Documents : drag & drop (optionnel)
- [ ] Créer une formation via import Excel
- [ ] Modifier et archiver une formation (pas de suppression — archivage = lecture seule)
- [ ] Inscrire des stagiaires à une formation
- [ ] Filtrer la liste des formations par catégorie (Super Admin et Formateur)
- [ ] Formateur : peut voir toutes les formations en lecture seule (filtre par défaut : ses formations), peut ajouter des documents, ne peut pas créer / modifier / archiver

### Gestion des comptes (Super Admin uniquement)
- [ ] Créer un formateur : Nom, Prénom, Email (obligatoires)
- [ ] Créer un stagiaire : Nom, Prénom, Email (obligatoires) + Formation (obligatoire — liste déroulante, au moins une)
- [ ] Suspendre / réactiver un formateur
- [ ] Désactiver / réactiver un stagiaire
- [ ] Seul le Super Admin voit les comptes désactivés
- [ ] Bouton "Envoyer un message" sur chaque profil formateur et stagiaire

### Gestion des documents
- [ ] Super Admin : déposer des documents au niveau formation (visibles par tous les inscrits)
- [ ] Super Admin : déposer un document ciblé pour un stagiaire spécifique
- [ ] Formateur : peut ajouter des documents à ses formations
- [ ] Stagiaire : consulte et télécharge ses documents depuis le détail de la formation
- [ ] Stagiaire : dépose ses propres documents (requis par formation) en drag & drop depuis son profil

### Messagerie
- [ ] Stagiaire → Super Admin ou tout formateur (filtre par défaut : formateur de ses formations)
- [ ] Formateur — message individuel : Super Admin, tout formateur actif, tout stagiaire actif (recherche libre)
- [ ] Formateur — message groupé : filtrer par formation → envoyer à tous les membres de cette formation
- [ ] Super Admin — message individuel : tout utilisateur
- [ ] Super Admin — message groupé : filtrer par formation / documents manquants / sélection libre
- [ ] Accès à la messagerie possible depuis la liste stagiaires/formateurs via bouton "Envoyer un message"

### Notifications
- [ ] Cloche en-tête : affiche uniquement les notifications non lues, badge avec le nombre
- [ ] Cliquer une notification → navigue vers l'élément concerné + marque comme lue → disparaît de la cloche
- [ ] Supprimer une notification depuis la cloche (suppression de la vue cloche uniquement)
- [ ] Page Notifications plein écran : toutes les notifications conservées, non supprimables, filtrables par date / lues / non lues
- [ ] Notification par email pour chaque nouvelle notification (toggle on/off par utilisateur dans son profil)

### RGPD
- [ ] Historique des stagiaires conservé 1 an (à confirmer avec le service juridique)

## Hors périmètre (V1)
- Authentification Google / SSO
- Auto-inscription des stagiaires
- Paiement en ligne
- Signature électronique
- Agenda / calendrier des sessions
- Formulaires intégrés (prévu en V2)
- Intégration NAS (site hébergé indépendamment du serveur interne de l'ADAC)

## Entités principales
- **Utilisateur** : tous les rôles (SUPER_ADMIN, ADMIN, STAGIAIRE) — email, mot de passe, rôle, profil
- **Catégorie** : nom, couleur, statut actif/inactif — classe les formations, pas de suppression
- **Formation** : titre, description, dates, modalité, catégorie, statut
- **Inscription** : lien entre un Stagiaire et une Formation
- **Document** : fichier, lié à une Formation ou à une Inscription spécifique (niveau stagiaire)
- **Message** : expéditeur, destinataire(s), contenu, fil de discussion
- **Notification** : type, destinataire, statut lu/non lu, entité liée
- **TokenActivation** : code à 6 chiffres, expiration 30 min, lié à un Utilisateur (limite : 3 envois / 15 min)

## Authentification
Email + mot de passe (JWT). Comptes créés exclusivement par le SUPER_ADMIN. Activation obligatoire par email avant la première connexion.

## Indicateurs de succès
- Zéro document envoyé par email individuel pour les stagiaires inscrits
- La chargée de formation économise au moins 2h/semaine sur les tâches administratives
- Tous les échanges documentaires requis par Qualiopi tracés dans le portail
- Les formateurs peuvent communiquer avec leurs stagiaires sans passer par la coordinatrice

## Contraintes
- Backend bien structuré sous 2 semaines (priorité)
- Application web (responsive)
- Conformité RGPD (politique de conservation des données, droit à l'effacement)
- Hébergé indépendamment du NAS interne de l'ADAC
- Interface entièrement en français

_Généré le 2026-08-19 avec /new-project_
