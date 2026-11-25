# 06: Android — widget d'écran d'accueil

**What to build:** Sur Android, l'utilisateur ajoute autant de widgets (Jetpack Glance) qu'il veut sur l'écran d'accueil. À l'ajout, il choisit l'objectif affiché. Le widget existe en petite taille (chiffre + titre) et moyenne taille (chiffre + titre + progression), utilise le même moteur que l'app, se met à jour au changement de jour et après toute modification dans l'app.

**Blocked by:** 02 — Android : créer un compte à rebours

**Status:** ready-for-agent

- [ ] Écran de configuration du widget : choix de l'objectif
- [ ] Plusieurs widgets possibles, y compris deux sur le même objectif
- [ ] Tailles petite et moyenne ; respect du mode clair/sombre
- [ ] Mise à jour automatique à minuit local
- [ ] Mise à jour immédiate après modification/suppression d'un objectif dans l'app
- [ ] Objectif supprimé → « Objectif supprimé — touchez pour en choisir un »
- [ ] Appui sur le widget → ouvre l'app sur l'objectif
- [ ] Possibilité de changer l'objectif d'un widget existant
- [ ] Vérifié à la main sur au moins un lanceur Pixel et un lanceur Samsung (ou émulateurs équivalents)
