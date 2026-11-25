# 01: Squelette des deux apps + jeu de cas partagé

**What to build:** Les applications Android (Kotlin, Jetpack Compose) et iOS (Swift, SwiftUI) existent, compilent et affichent un écran « Mes objectifs » vide avec un état « Aucun objectif pour l'instant ». Un jeu de cas partagé (format de données neutre : objectif + date du jour → affichage attendu) est en place et rejoué automatiquement par les tests JVM (JUnit) et XCTest, avec un premier cas trivial qui passe des deux côtés. Les chaînes sont externalisées (français par défaut). Voir SPEC.md, sections Implementation Decisions et Testing Decisions.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] L'app Android se lance sur émulateur (Android 8+) et affiche la liste vide
- [ ] L'app iOS se lance sur simulateur (iOS 17+) et affiche la liste vide
- [ ] Un fichier unique de cas partagés est lu par les deux suites de tests
- [ ] Un cas trivial passe en JUnit et en XCTest
- [ ] Les textes de l'interface sont dans les ressources de localisation, pas en dur
- [ ] Un README explique comment lancer chaque app et chaque suite de tests
