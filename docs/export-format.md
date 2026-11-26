# Format d'export des objectifs — `goal-widgets-export`, version 1

Fichier produit par « Exporter les objectifs » et lu par « Importer des objectifs », sur Android
(ticket 10) comme sur iOS (ticket 11). Un fichier exporté sur une plateforme s'importe tel quel
sur l'autre.

- Implémentation de référence : `android/core/src/main/kotlin/app/goalwidgets/core/GoalExport.kt`
  (`GoalExport.encode` / `GoalExport.decode`, `GoalImport.plan`).
- Cas partagés (à rejouer par JUnit **et** XCTest) : `shared-cases/export/cases.json` + les
  fichiers d'exemple du même dossier.

## Fichier

- Encodage **UTF-8**, JSON (RFC 8259), sans BOM. Extension `.json`, type MIME `application/json`.
- Nom suggéré à l'export : `goal-widgets-AAAA-MM-JJ.json` (date du jour locale).
- L'écrivain indente librement ; le lecteur ne dépend ni de l'indentation ni de l'ordre des clés.

## Structure

```json
{
  "format": "goal-widgets-export",
  "version": 1,
  "exportedAt": "2026-11-25T08:30:00.000Z",
  "goals": [
    {
      "id": "3f2b8c1e-4d5a-4e6f-9a7b-1c2d3e4f5a6b",
      "title": "Examen",
      "kind": "countdown",
      "startDate": "2026-09-01",
      "targetDate": "2027-06-15",
      "color": "blue",
      "emoji": "🎓",
      "notificationsEnabled": true,
      "createdAt": "2026-09-01T07:12:45.123Z"
    }
  ]
}
```

### Racine

| Clé | Type | Obligatoire | Sens |
|---|---|---|---|
| `format` | chaîne | oui | Toujours `"goal-widgets-export"`. Sinon : erreur `notAnExport`. |
| `version` | entier JSON (pas une chaîne) | oui | `1` pour ce document. Absent / non entier / < 1 : `notAnExport`. > version lue par l'app : `unsupportedVersion`. |
| `exportedAt` | instant (voir plus bas) | non | Informatif, jamais relu. |
| `goals` | tableau d'objets | oui | Absent ou pas un tableau : `notAnExport`. Peut être vide. |

### Objectif (`goals[i]`)

| Clé | Type | Obligatoire | Écriture | Lecture |
|---|---|---|---|---|
| `id` | chaîne UUID `8-4-4-4-12` hexadécimal | oui | **minuscules** | comparé **sans casse**, normalisé en minuscules. Pas un UUID : `invalidGoal`/`id`. |
| `title` | chaîne | oui | titre tel que stocké | espaces de bord retirés ; vide après nettoyage : `invalidGoal`/`title`. |
| `kind` | `"countdown"` \| `"since"` | oui | | autre valeur : `invalidGoal`/`kind`. |
| `startDate` | date `AAAA-MM-JJ` | oui | | date calendaire locale, sans heure ni fuseau ; date inexistante (ex. `2026-02-30`) ou autre forme : `invalidGoal`/`startDate`. |
| `targetDate` | date `AAAA-MM-JJ` ou `null` | `countdown` : oui | `null` pour `since` | `countdown` sans cible valide : `invalidGoal`/`targetDate`. `since` : **ignorée** (mise à `null`). Aucune règle d'ordre départ/cible n'est imposée à l'import (le moteur gère départ ≥ cible). |
| `color` | chaîne ou `null` | non | `null` = couleur par défaut | valeurs connues : `red`, `orange`, `yellow`, `green`, `teal`, `blue`, `indigo`, `purple`, `pink`, `gray`. Valeur inconnue ou absente → `null` (pas une erreur : compatibilité avec une future palette). |
| `emoji` | chaîne ou `null` | non | emoji libre (peut être composé : `👨‍👩‍👧`, `✈️`) | espaces de bord retirés ; vide → `null`. Pas de validation « est un emoji ». |
| `notificationsEnabled` | booléen ou `null` | non | `true`/`false` quand l'app le stocke (ticket 08/09), sinon `null` | `null`/absent = « non précisé » : l'app importatrice applique sa valeur par défaut. Autre type : `invalidGoal`/`notificationsEnabled`. |
| `createdAt` | instant | oui | | sert au tri « par création ». Absent/invalide : `invalidGoal`/`createdAt`. |

**Champs facultatifs (réglages Android, ajoutés en v1)** — écrits seulement s'ils diffèrent de la valeur
par défaut ; une valeur absente ou invalide vaut la valeur par défaut (jamais une erreur) ; un lecteur
qui ne les connaît pas les ignore (iOS aujourd'hui) :

| Clé | Type | Défaut | Sens |
|---|---|---|---|
| `chartUnit` | `"auto"` \| `"day"` \| `"week"` \| `"month"` | absent = réglage général | unité des graphiques propre à l'objectif |
| `streak` | booléen | `false` | compteur « série » (remise à zéro) |
| `streakHistory` | chaîne `AAAA-MM-JJ/AAAA-MM-JJ;…` | vide | séries terminées, ordre chronologique (pas dans un objectif partagé) |
| `repeatYearly` | booléen | `false` | compte à rebours « chaque année » (ignoré pour `since`) |
| `archived` | booléen | `false` | objectif archivé (pas dans un objectif partagé) |

Un élément de `goals` qui n'est pas un objet JSON : `invalidGoal` avec `field` = `"goal"`.

**Instant** : RFC 3339 / ISO 8601 avec décalage obligatoire. Écriture : **UTC, suffixe `Z`, exactement 3
décimales** (`2026-09-01T07:12:45.123Z`). Lecture : `Z` ou décalage (`+02:00`), fractions de
seconde facultatives (0 à 9 chiffres). iOS : `ISO8601DateFormatter` avec
`[.withInternetDateTime, .withFractionalSeconds]` pour écrire, et essai avec puis sans
`.withFractionalSeconds` pour lire.

**Clés inconnues** (racine ou objectif) : ignorées. Une version 1 peut donc gagner des champs
facultatifs sans changer de numéro ; un changement incompatible passe en `version: 2`.

## Lecture : tout ou rien

Le fichier est validé **en entier avant toute écriture**. À la première erreur, rien n'est importé et
l'app affiche un message clair (aucune donnée existante n'est modifiée). Ordre des vérifications :

1. JSON illisible → `notJson`.
2. Racine pas un objet, `format` ≠ `goal-widgets-export`, `version` absente/invalide, `goals` absent
   ou pas un tableau → `notAnExport`.
3. `version` > version supportée → `unsupportedVersion` (avec `version`).
4. Objectifs dans l'ordre du fichier : premier champ invalide → `invalidGoal` (avec `index` à partir
   de 0 et `field`). Ordre des champs vérifiés : `id`, `title`, `kind`, `startDate`, `targetDate`,
   `notificationsEnabled`, `createdAt`.
5. Deux objectifs du fichier avec le même `id` (sans casse) → `duplicateId` (avec `id` en minuscules).

| Code | Message Android (clé) |
|---|---|
| `notJson`, `notAnExport` | `import_error_not_export` |
| `unsupportedVersion` | `import_error_unsupported_version` (`%1$d` = version) |
| `invalidGoal` | `import_error_invalid_goal` (`%1$d` = n° de l'objectif **à partir de 1**, `%2$s` = champ) |
| `duplicateId` | `import_error_duplicate_id` |
| (fichier illisible / > 5 Mo) | `import_error_unreadable` |

## Fusion : import idempotent, sans doublon

- Un objectif du fichier dont l'`id` existe déjà localement (comparaison sans casse) est **ignoré** :
  la version locale est conservée telle quelle (pas d'écrasement, pas de fusion champ à champ).
- Les autres sont **ajoutés** avec leur `id` (UUID conservé), leur `createdAt`, leur couleur et emoji.
- Importer deux fois le même fichier n'ajoute donc rien la seconde fois. Résultat affiché :
  « N objectifs importés · M déjà présents ».
- Android : la lecture des `uid` existants et l'insertion se font dans une seule transaction Room
  (`GoalDao.importGoals`). Les objectifs ajoutés reçoivent un nouvel `id` local (entier) ; les widgets
  ne sont pas concernés.

## Correspondance stockage

| Champ | Android (Room `GoalEntity`, schéma v2) | iOS (SwiftData `GoalRecord`) |
|---|---|---|
| `id` | `uid` (TEXT, index unique, UUID minuscule) | `id: UUID` → `uuidString.lowercased()` à l'écriture, `UUID(uuidString:)` à la lecture |
| `startDate`/`targetDate` | `startEpochDay`/`targetEpochDay` | `startDateISO`/`targetDateISO` (déjà `AAAA-MM-JJ`) |
| `color` | `color` (TEXT nullable, `GoalColor.wire`) | à ajouter (t11) |
| `emoji` | `emoji` (TEXT nullable) | à ajouter (t11) |
| `createdAt` | `createdAtMillis` | `createdAt: Date` |
| `notificationsEnabled` | (ticket 08) — écrit `null` tant qu'il n'existe pas | (ticket 09) |

## Tri de la liste (même règle des deux côtés)

Cas partagés : `shared-cases/sort.json`. Choix mémorisé dans les préférences (`goal_order` =
`closestDate` | `created`, défaut `closestDate`).

- `closestDate` : (1) comptes à rebours dont la cible est aujourd'hui ou plus tard, cible croissante ;
  (2) comptes à rebours atteints, cible décroissante ; (3) objectifs « depuis », départ décroissant.
- `created` : `createdAt` décroissant (le plus récent d'abord).
- Égalités : `createdAt` décroissant, puis `id` croissant (comparaison de chaînes).
