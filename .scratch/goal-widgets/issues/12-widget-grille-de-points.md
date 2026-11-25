# 12: Widget « grille de points » (Android + iOS)

**What to build:** Un nouveau style de widget qui affiche un point par unité de temps : points pleins = écoulés, point mis en valeur = aujourd'hui, points vides = restants. À la configuration du widget, l'utilisateur choisit l'objectif **et la période affichée** : objectif entier, mois en cours, semaine en cours. Le chiffre de jours et le titre restent visibles. Tailles moyenne et grande. Le calcul de la grille est pur, dans `:core` / `GoalCore`, et figé par `shared-cases/dotgrid.json`.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

## Règles de calcul (décidées, à appliquer telles quelles des deux côtés)
`DotGrid.compute(goal, today, period) -> DotGrid(unit, cells: [Cell(start, end, state)])`
- `period` : `whole` | `month` | `week`.
- Plage :
  - `whole` + compte à rebours : `startDate` … `targetDate` (bornes incluses).
  - `whole` + « depuis » : `startDate` … `today` (si `startDate > today` : une seule cellule `today`).
  - `month` : du 1er au dernier jour du mois calendaire contenant `today`.
  - `week` : lundi … dimanche de la semaine ISO contenant `today`.
- `unit` : `day` si la plage compte ≤ 366 jours ; sinon `week` (cellules de 7 jours à partir du début de plage, la dernière peut être plus courte) si ≤ 366 semaines ; sinon `month` (mois calendaires, la première et la dernière cellule tronquées aux bornes de la plage). `month`/`week` de période sont toujours en `day`.
- `state` de chaque cellule :
  - `outside` : cellule entièrement hors de l'objectif (avant `startDate`, ou après `targetDate` pour un compte à rebours) — n'arrive qu'en période `month`/`week` ;
  - `today` : la cellule contient `today` ;
  - `elapsed` : la cellule finit avant `today` ;
  - `remaining` : la cellule commence après `today`.
- Cas partagés : `shared-cases/dotgrid.json` (même enveloppe que `engine.json` : `{version, cases:[{name, goal, today, period, expected:{unit, count, states?, first?, last?}}]}` — `states` = chaîne compacte d'une lettre par cellule : `e`/`t`/`r`/`o` ; `first`/`last` = `{start, end}` ISO).

- [ ] Logique pure + cas partagés (au moins : compte à rebours court en `day`, long en `week`, très long en `month`, « depuis » en `whole`, `month` et `week` avec cellules `outside`, départ futur, fin de mois, année bissextile)
- [ ] Widget Android : nouveau style « Grille » (Glance), choix objectif + période dans l'écran de configuration, rafraîchi à minuit
- [ ] Widget iOS : nouveau widget « Grille » (paramètres AppIntent objectif + période), timeline quotidienne
- [ ] Couleur de l'objectif utilisée pour les points ; mode clair/sombre
