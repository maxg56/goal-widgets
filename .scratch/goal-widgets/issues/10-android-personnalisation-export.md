# 10: Android — couleur, emoji, tri, export/import

**What to build:** Sur Android, l'utilisateur choisit une couleur et un emoji pour chaque objectif (repris dans la liste et les widgets), trie la liste (date la plus proche / création), et peut exporter tous ses objectifs dans un fichier puis les réimporter (ex. sur un nouveau téléphone). Ce ticket fixe le format d'export, documenté pour qu'iOS l'utilise à l'identique.

**Blocked by:** 04 — Android : objectifs « depuis », modifier, supprimer

**Status:** done (t10) — vérifié par `./gradlew test assembleDebug` ; rendu à l'écran (formulaire, liste, widget, sélecteurs SAF) non vérifié faute d'appareil/émulateur. Format : `docs/export-format.md`.

- [x] Choix de couleur et d'emoji ; visibles dans la liste et les widgets
- [x] Tri par date la plus proche et par création
- [x] Export de tous les objectifs dans un fichier via le partage système
- [x] Import d'un fichier : ajout des objectifs, sans doublons si on importe deux fois
- [x] Format d'export versionné et documenté
- [x] Fichier invalide → message d'erreur clair, aucune donnée perdue
