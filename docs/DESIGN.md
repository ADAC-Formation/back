# Design System — Portail de Formation ADAC

## Identité visuelle
- **Ambiance** : chaleureuse, institutionnelle et accessible — fidèle à la charte graphique ADAC
- **Référence** : charte officielle ADAC + maquette Manon (dashboard 4 tuiles)
- **Slogan logo** : "Le social autrement"
- **Dark mode** : toggle prévu dans l'en-tête

---

## Palette de couleurs

| Token | Hex | Usage |
|---|---|---|
| `primary` | `#cc3d34` | Actions principales, boutons, grande tuile Formations |
| `primary-dark` | `#a8302a` | Hover sur primary |
| `secondary` | `#d9812c` | Accents, tuile Notifications, warnings |
| `accent-pink` | `#F35E6C` | Tuile Messages, badges |
| `accent-yellow` | `#f6d628` | Tuile Profil, highlights |
| `background` | `#faf8f5` | Fond de page (blanc cassé/crème — comme le mockup) |
| `surface` | `#ffffff` | Cartes, panneaux, modales |
| `text-primary` | `#1f2629` | Texte principal (quasi-noir ADAC) |
| `text-secondary` | `#949598` | Labels, textes secondaires (gris ADAC) |
| `error` | `#cc3d34` | États d'erreur (même rouge — cohérent avec la charte) |
| `success` | `#2e7d52` | États de succès |
| `border` | `#e8e4df` | Bordures, séparateurs |

> Les 6 couleurs officielles de la charte ADAC sont toutes utilisées :
> `#cc3d34` · `#F35E6C` · `#d9812c` · `#f6d628` · `#1f2629` · `#949598`

---

## Typographie

- **Police principale** : **Manrope** (Google Fonts) — utilisée pour le site ADAC
- **Fallback** : `system-ui, sans-serif`

| Niveau | Taille | Graisse | Couleur |
|---|---|---|---|
| H1 (titre page) | 28px | 700 | `#cc3d34` |
| H2 (section) | 22px | 600 | `#cc3d34` |
| H3 (sous-section) | 18px | 600 | `#1f2629` |
| H4 (card title) | 16px | 600 | `#1f2629` |
| Body | 14px | 400 | `#1f2629` |
| Small / Label | 12px | 400 | `#949598` |

---

## Espacement (base 4px)

| Token | Valeur |
|---|---|
| `xs` | 4px |
| `sm` | 8px |
| `md` | 16px |
| `lg` | 24px |
| `xl` | 32px |
| `2xl` | 48px |
| `3xl` | 64px |

---

## Border Radius

| Token | Valeur | Usage |
|---|---|---|
| `sm` | 6px | Inputs, badges |
| `md` | 12px | Boutons, petites cartes |
| `lg` | 16px | Cartes standards |
| `xl` | 24px | Grandes tuiles dashboard |
| `full` | 9999px | Avatars, pills |

---

## Ombres

| Token | Valeur CSS |
|---|---|
| `sm` | `0 1px 3px rgba(31,38,41,0.08)` |
| `md` | `0 4px 12px rgba(31,38,41,0.10)` |
| `lg` | `0 8px 24px rgba(31,38,41,0.12)` |

---

## Composants clés

### Tuiles du tableau de bord
Inspirées directement du mockup Manon :

| Tuile | Couleur fond | Icône | Taille |
|---|---|---|---|
| Formations | `#cc3d34` | 📚 livre | Grande (occupe ~60% de la largeur) |
| Messages | `#F35E6C` | 💬 bulle | Petite (1/3 droite) |
| Notifications | `#d9812c` | 🔔 cloche | Petite (1/3 droite) |
| Profil | `#f6d628` | 👤 personne | Petite (1/3 droite) |

- Texte : blanc `#ffffff`
- Border radius : `xl` (24px)
- Ombre : `md`
- Hover : légère élévation (`lg`) + `transform: scale(1.01)`

### Bouton principal
- Fond : `#cc3d34` | Texte : `#ffffff`
- Border radius : `md` (12px)
- Padding : 12px 24px
- Hover : `#a8302a`

### Bouton secondaire
- Fond : transparent | Bordure : `#cc3d34` | Texte : `#cc3d34`
- Hover : fond `#fef2f1`

### Input / Champ de formulaire
- Bordure : `#e8e4df` | Focus : `#cc3d34` (ring 2px)
- Border radius : `sm` (6px)
- Background : `#ffffff`

### Carte standard
- Fond : `#ffffff` | Bordure : `#e8e4df`
- Border radius : `lg` (16px) | Ombre : `sm`

### Navigation (en-tête)
- Type : **topbar** fixe
- Fond : `#ffffff` | Bordure bas : `#e8e4df`
- Logo ADAC centré ou à gauche selon le rôle
- Icônes droite : cloche (notifications) · lune (dark mode) · déconnexion

### Badge de notification
- Fond : `#cc3d34` | Texte : `#ffffff`
- Border radius : `full` | Taille : 18px min

---

## Écrans principaux (wireframes textuels)

### Page de connexion
```
┌─────────────────────────────────────────┐
│           [Logo ADAC]                   │
│      "Bienvenue sur votre portail"      │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ Email                             │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ Mot de passe                  👁  │  │
│  └───────────────────────────────────┘  │
│                                         │
│  [    Commencer la session    ]         │
│                                         │
│  Mot de passe oublié / Activer mon      │
│  compte ?                               │
│                                         │
│  Pas de compte ? Contactez votre        │
│  administrateur.                        │
└─────────────────────────────────────────┘
```

### Tableau de bord (tous rôles)
```
┌─────────────────────────────────────────────────────┐
│  [Logo ADAC]                        🔔  🌙  ⚙  →│  │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────────────┐  ┌───────────────────┐   │
│  │ 📚                   │  │ 💬  Messages       │   │
│  │                      │  └───────────────────┘   │
│  │                      │  ┌───────────────────┐   │
│  │  Formations          │  │ 🔔  Notifications  │   │
│  │  Accéder à mes cours │  └───────────────────┘   │
│  └──────────────────────┘  ┌───────────────────┐   │
│                            │ 👤  Profil         │   │
│                            └───────────────────┘   │
└─────────────────────────────────────────────────────┘
  [rouge #cc3d34]            [rose] [orange] [jaune]
```

### Liste / Détail de formation
```
┌─────────────────────────────────────────────────────┐
│  ← Retour          Formations                       │
├─────────────────────────────────────────────────────┤
│  [Filtre : Mes formations ▾]    [+ Créer]           │
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │ 🗂 Prévention en santé mentale              │   │
│  │ 12 jan → 14 jan 2026 · Présentiel · Paris   │   │
│  │ Formateur : Marie Dupont        [Voir →]    │   │
│  └─────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────┐   │
│  │ 🗂 Intervention sociale d'urgence           │   │
│  │ 3 fév → 5 fév 2026 · Visio                 │   │
│  │ Formateur : —                   [Voir →]    │   │
│  └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### Messagerie
```
┌─────────────────────────────────────────────────────┐
│  Messages              [✉ Nouveau message]          │
├──────────────┬──────────────────────────────────────┤
│ Conversations│                                      │
│              │  Charlotte Doulcet                   │
│ > Charlotte  │  ─────────────────────────────────   │
│   Dupont     │  [Bonjour, pouvez-vous confirmer...] │
│              │                          [moi] 14:32 │
│   Martin     │                                      │
│   Legrand    │  [Bien sûr, voici les détails...]    │
│              │  Charlotte 14:35                     │
│              │                                      │
│              │  ┌──────────────────────────────┐    │
│              │  │ Écrire un message...       ➤ │    │
│              │  └──────────────────────────────┘    │
└──────────────┴──────────────────────────────────────┘
```

---

_Design validé le 2026-08-21 — basé sur la charte graphique officielle ADAC et la maquette de Manon_
