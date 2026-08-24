# Parcours utilisateur — Portail de Formation ADAC

Fichier diagramme : `USERFLOW.mmd` (ouvrir avec l'extension Mermaid Preview dans VS Code)

---

## Flux principaux

### Flux 1 : Connexion
Début → Page de connexion  
→ Identifiants invalides → message d'erreur → retour connexion  
→ Identifiants valides → compte activé ?  
  - Non → message d'erreur "compte non activé" → retour connexion  
  - Oui → vérification du rôle → redirection vers le tableau de bord correspondant (SUPER_ADMIN / ADMIN / STAGIAIRE)

### Flux 2 : Activation de compte
Super Admin crée un compte (isActive=false) → email envoyé automatiquement : code 6 chiffres + lien /activation valide 30 min  
→ Formulaire : code de vérification + nouveau MDP + confirmation  
  - Code expiré ou invalide → demander un nouveau code (invalide les précédents) → nouvel email  
  - MDP trop faible ou non concordants → retour formulaire  
  - Tout OK → compte activé (isActive=true) → retour page de connexion avec bandeau de confirmation

### Flux 3 : Mot de passe oublié
Page de connexion → "Mot de passe oublié / Activer mon compte" → saisir son email  
→ Email non reconnu → message d'erreur → ressaisir  
→ Email reconnu (limite : 3 envois / 15 min) → email : code 6 chiffres + lien /reinitialisation valide 30 min  
→ Formulaire : code + nouveau MDP + confirmation  
  - Code expiré ou invalide → demander un nouveau code → nouvel email  
  - Tout OK → MDP mis à jour → retour connexion avec bandeau de confirmation

---

## SUPER ADMIN

### Tableau de bord
4 tuiles : **Gestion** | **Messages** | **Notifications** | **Mon Profil**

### Flux 4 : Gérer les formations
Gestion → Formations → Liste (actives et archivées)  
→ Créer via formulaire :
  - Intitulé (obligatoire), Date début, Date fin (obligatoires)
  - Formateur : liste déroulante actifs uniquement (optionnel) → si vide : Super Admin assigné automatiquement
  - Modalité : visio / présentiel / mixte (obligatoire)
  - Documents : drag & drop (optionnel)  
→ Créer via import Excel  
→ Détail de la formation → Modifier / Archiver (lecture seule après archivage)  
→ Détail → Attribuer un formateur (si aucun disponible : Super Admin auto-assigné)  
→ Détail → Déposer des documents pour toute la formation  
→ Détail → Déposer un document ciblé pour un stagiaire spécifique  
→ Détail → Inscrire des stagiaires

### Flux 5 : Gérer les formateurs
Gestion → Formateurs → Liste (actifs et suspendus)  
→ Créer un formateur : Nom, Prénom, Email (obligatoires) → email d'activation envoyé  
→ Profil formateur → bouton "Envoyer un message" → Messagerie  
→ Profil formateur → Suspendre (isActive=false) / Réactiver

### Flux 6 : Gérer les stagiaires
Gestion → Stagiaires → Liste (actifs ET désactivés — seul le Super Admin voit les désactivés)  
→ Créer un stagiaire : Nom, Prénom, Email + Formation(s) (OBLIGATOIRE) → email d'activation envoyé  
→ Profil stagiaire → historique complet  
→ Profil stagiaire → bouton "Envoyer un message" → Messagerie  
→ Profil stagiaire → Désactiver / Réactiver

### Flux 7 : Messagerie Super Admin
Messagerie → **Message individuel** : tout utilisateur (Super Admin, formateur, stagiaire)  
Messagerie → **Message groupé** → filtrer les destinataires :
  - Par formation → tous les stagiaires de cette formation
  - Documents manquants → stagiaires avec documents manquants
  - Sélection libre → sélection manuelle

---

## FORMATEUR / ADMIN

### Tableau de bord
4 tuiles : **Gestion** | **Messages** | **Notifications** | **Mon Profil**

### Flux 8 : Consulter les formations (lecture seule)
Gestion → Formations → Liste  
  - Filtre par défaut : ses formations (peut voir toutes les formations)  
→ Détail de la formation (lecture seule)  
→ ✓ Peut ajouter des documents à la formation  
→ ✗ Ne peut pas créer / modifier / archiver une formation

### Flux 9 : Consulter les formateurs (lecture seule)
Gestion → Formateurs → Liste de tous les formateurs (lecture seule)  
→ Profil formateur (lecture seule) → bouton "Envoyer un message" → Messagerie

### Flux 10 : Consulter les stagiaires (lecture seule)
Gestion → Stagiaires → Liste des stagiaires actifs uniquement  
  - Filtre par défaut : ses stagiaires (peut chercher tous les stagiaires actifs)  
  - ✗ Ne voit pas les stagiaires désactivés  
→ Profil stagiaire (lecture seule) → bouton "Envoyer un message" → Messagerie  
→ ✗ Ne peut pas modifier / désactiver un stagiaire

### Flux 11 : Messagerie Formateur
Messagerie → **Message individuel** → rechercher un destinataire :
  - Super Admin
  - Tout formateur actif
  - Tout stagiaire actif  
Messagerie → **Message groupé** → filtrer par formation → tous les membres de cette formation

---

## STAGIAIRE

### Tableau de bord
4 tuiles : **Mes Formations** | **Messages** | **Notifications** | **Mon Profil**

### Flux 12 : Consulter ses formations
Mes Formations → liste des formations auxquelles il est inscrit (uniquement)  
→ Détail de la formation (lecture seule) → consulter et télécharger les documents

### Flux 13 : Messagerie Stagiaire
Messagerie → choisir un destinataire :
  - Super Admin
  - Formateur de ses formations (défaut)
  - Autre formateur (recherche libre)

### Flux 14 : Profil Stagiaire
Mon Profil → Changer mon mot de passe  
Mon Profil → Notifications email : activer / désactiver  
Mon Profil → Mes Documents : déposer les fichiers demandés par formation (drag & drop)

---

## Notifications (tous les rôles)

### Cloche en-tête
- Affiche uniquement les notifications **non lues** avec badge
- Cliquer une notification → navigue vers la formation ou le message → marque comme lue → disparaît de la cloche
- Bouton Supprimer disponible sur chaque notification dans la cloche

### Page Notifications (plein écran)
- Accessible depuis le tableau de bord (tuile Notifications)
- Toutes les notifications conservées, **aucune suppression possible**
- Filtres : par date / lues / non lues
- Cliquer une notification → naviguer vers l'élément concerné → marquer comme lue

---

## Inventaire des écrans

| Écran | Description | Rôles |
|---|---|---|
| Page de connexion | Email + MDP, lien activation/oubli | Tous |
| Activation de compte | Code 6 chiffres + nouveau MDP | Tous (à la création) |
| Mot de passe oublié | Email → code → nouveau MDP | Tous |
| Tableau de bord Super Admin | 4 tuiles : Gestion / Messages / Notifications / Profil | SUPER_ADMIN |
| Tableau de bord Formateur | 4 tuiles : Gestion / Messages / Notifications / Profil | ADMIN |
| Tableau de bord Stagiaire | 4 tuiles : Mes Formations / Messages / Notifications / Profil | STAGIAIRE |
| Liste des formations (SA) | Actives + archivées, créer / importer | SUPER_ADMIN |
| Formulaire de formation | Intitulé, dates, formateur, modalité, documents | SUPER_ADMIN |
| Détail de la formation | Infos, documents, stagiaires inscrits (vue filtrée selon le rôle) | Tous |
| Liste des formateurs | Actifs + suspendus (SA) / tous actifs (ADMIN, lecture seule) | SUPER_ADMIN, ADMIN |
| Liste des stagiaires | Actifs + désactivés (SA) / actifs seulement (ADMIN) | SUPER_ADMIN, ADMIN |
| Profil formateur | Infos + bouton "Envoyer un message" | SUPER_ADMIN, ADMIN |
| Profil stagiaire | Historique (SA) / lecture seule (ADMIN) + bouton message | SUPER_ADMIN, ADMIN |
| Mes Formations | Formations auxquelles le stagiaire est inscrit | STAGIAIRE |
| Messagerie | Individuel / groupé selon le rôle | Tous |
| Notifications (plein écran) | Historique complet, filtreable, non supprimable | Tous |
| Mon Profil | MDP, notifications email, mes documents (stagiaire) | Tous |

_Généré le 2026-08-19 avec /new-project_
