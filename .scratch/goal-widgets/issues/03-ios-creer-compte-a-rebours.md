# 03: iOS — créer un compte à rebours et le voir dans la liste

**What to build:** Même comportement que le ticket 02, sur iPhone : création d'un objectif « compte à rebours », stockage local (SwiftData, dans un App Group pour être lu plus tard par le widget), liste avec jours restants et progression. Le moteur Swift produit exactement les mêmes sorties que le moteur Kotlin sur le jeu de cas partagé.

**Blocked by:** 01 — Squelette des deux apps + jeu de cas partagé

**Status:** done (non vérifié)

> Non vérifié : écrit sans Xcode (machine Linux) — ni compilé ni testé. Les cases cochées le sont sur lecture du code.

- [x] Formulaire de création identique à Android (titre, date cible, date de départ) — rendu non vérifié (pas de Xcode)
- [x] Stockage dans un conteneur partagé (App Group) — entitlement + `ModelConfiguration(groupContainer:)` ; exécution non vérifiée (pas de Xcode)
- [ ] La liste affiche « dans N jours » et une progression bornée 0–100 % — code écrit (bornage testé dans `CountdownEngineTests`), non vérifié (pas de Xcode)
- [ ] Tous les cas partagés du moteur passent en XCTest — harnais prêt, non vérifié (pas de Xcode) ; à rejouer après l'ajout des cas par t02
- [x] Le moteur ne dépend d'aucune API UIKit/SwiftUI ; la date du jour est un paramètre (GoalCore n'importe que Foundation)
