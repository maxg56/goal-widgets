# 03: iOS — créer un compte à rebours et le voir dans la liste

**What to build:** Même comportement que le ticket 02, sur iPhone : création d'un objectif « compte à rebours », stockage local (SwiftData, dans un App Group pour être lu plus tard par le widget), liste avec jours restants et progression. Le moteur Swift produit exactement les mêmes sorties que le moteur Kotlin sur le jeu de cas partagé.

**Blocked by:** 01 — Squelette des deux apps + jeu de cas partagé

**Status:** ready-for-agent

- [ ] Formulaire de création identique à Android (titre, date cible, date de départ)
- [ ] Stockage dans un conteneur partagé (App Group)
- [ ] La liste affiche « dans N jours » et une progression bornée 0–100 %
- [ ] Tous les cas partagés du moteur passent en XCTest
- [ ] Le moteur ne dépend d'aucune API UIKit/SwiftUI ; la date du jour est un paramètre
