# 11: iOS — couleur, emoji, tri, export/import

**What to build:** Même comportement que le ticket 10, sur iPhone, en utilisant exactement le format d'export défini par le ticket 10 : un fichier exporté depuis Android s'importe sur iPhone et inversement.

**Blocked by:** 05 — iOS : objectifs « depuis », modifier, supprimer ; 10 — Android : couleur, emoji, tri, export/import

**Status:** done (non vérifié) — code iOS écrit sans Xcode (machine Linux) : ni compilé ni exécuté. Logique (`GoalExport`, `GoalImport`, `GoalOrder`) rejouée contre `shared-cases/export/cases.json` et `shared-cases/sort.json` par XCTest (non lancé) et vérifiée par une copie Python jetable de l'algorithme (20/20 cas).

- [x] Couleur et emoji par objectif, visibles dans la liste et les widgets (écrit, rendu non vérifié)
- [x] Tri par date la plus proche et par création (`GoalOrder`, cas partagés `sort.json`)
- [x] Export/import via la feuille de partage / l'app Fichiers (`fileExporter`, `ShareLink`, `fileImporter` ; non exécuté)
- [x] Un fichier exporté sur Android s'importe sur iOS, et inversement — couvert par les cas partagés `shared-cases/export` (mêmes fichiers rejoués par JUnit et XCTest) ; aucun aller-retour réel entre appareils effectué
- [x] Fichier invalide → message d'erreur clair, aucune donnée perdue (lecture tout ou rien avant toute écriture ; messages = clés Android)
