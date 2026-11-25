# 10: Android — couleur, emoji, tri, export/import

**What to build:** Sur Android, l'utilisateur choisit une couleur et un emoji pour chaque objectif (repris dans la liste et les widgets), trie la liste (date la plus proche / création), et peut exporter tous ses objectifs dans un fichier puis les réimporter (ex. sur un nouveau téléphone). Ce ticket fixe le format d'export, documenté pour qu'iOS l'utilise à l'identique.

**Blocked by:** 04 — Android : objectifs « depuis », modifier, supprimer

**Status:** ready-for-agent

- [ ] Choix de couleur et d'emoji ; visibles dans la liste et les widgets
- [ ] Tri par date la plus proche et par création
- [ ] Export de tous les objectifs dans un fichier via le partage système
- [ ] Import d'un fichier : ajout des objectifs, sans doublons si on importe deux fois
- [ ] Format d'export versionné et documenté
- [ ] Fichier invalide → message d'erreur clair, aucune donnée perdue
