# 06: Android — widget d'écran d'accueil

**What to build:** Sur Android, l'utilisateur ajoute autant de widgets (Jetpack Glance) qu'il veut sur l'écran d'accueil. À l'ajout, il choisit l'objectif affiché. Le widget existe en petite taille (chiffre + titre) et moyenne taille (chiffre + titre + progression), utilise le même moteur que l'app, se met à jour au changement de jour et après toute modification dans l'app.

**Blocked by:** 02 — Android : créer un compte à rebours

**Status:** done

- [x] Écran de configuration du widget : choix de l'objectif
- [x] Plusieurs widgets possibles, y compris deux sur le même objectif (état Glance par instance)
- [x] Tailles petite et moyenne ; respect du mode clair/sombre (`SizeMode.Responsive` + `GlanceTheme`) — rendu non vérifié à l'écran
- [x] Mise à jour automatique à minuit local (alarme `setWindow` + redémarrage/heure/fuseau ; `updatePeriodMillis` 6 h en filet) — déclenchement réel non vérifié
- [x] Mise à jour immédiate après modification/suppression d'un objectif dans l'app (`GoalListViewModel(onGoalsChanged)` ; ajout branché, modification/suppression à brancher par t04 sur le même rappel)
- [x] Objectif supprimé → « Objectif supprimé — touchez pour en choisir un »
- [x] Appui sur le widget → ouvre l'app sur l'objectif (liste défilée jusqu'à l'objectif ; pas encore d'écran détail)
- [x] Possibilité de changer l'objectif d'un widget existant (`widgetFeatures="reconfigurable"`, Android 12+ ; appui sur un widget supprimé/non configuré)
- [ ] Vérifié à la main sur au moins un lanceur Pixel et un lanceur Samsung (ou émulateurs équivalents) — non fait : pas d'émulateur ni d'appareil
