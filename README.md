# Goal Widgets

Applications natives Android (Kotlin, Jetpack Compose) et iOS (Swift, SwiftUI) pour suivre des objectifs datés
(compte à rebours / depuis une date) sur l'écran d'accueil. Spécification : [SPEC.md](SPEC.md).

## Structure

```
android/            Projet Gradle
  app/              Application Compose (UI, stockage, widgets, notifications)
  core/             Kotlin pur, sans dépendance Android : moteur + planificateur (tests JVM)
ios/
  project.yml       Définition du projet Xcode (XcodeGen)
  GoalWidgets/      Application SwiftUI
  GoalCore/         Package Swift pur : moteur + planificateur (XCTest)
shared-cases/       Jeu de cas partagé (JSON), rejoué par JUnit et XCTest
```

## Jeu de cas partagé

Le moteur et le planificateur sont écrits deux fois (Kotlin et Swift). Leur comportement est figé par les fichiers
JSON de `shared-cases/` que les deux suites de tests exécutent :

- `shared-cases/engine.json` : moteur de compte à rebours (objectif + date du jour → état d'affichage).
- `shared-cases/streaks.json` : compteurs « série » (remise à zéro, record, historique stocké, double appui du widget).

Format : `{ "version": 1, "cases": [ { "name", "goal": {...}, "today": "AAAA-MM-JJ", "expected": {...} } ] }`.
Seuls les champs présents dans `expected` sont vérifiés, ce qui permet d'ajouter de nouveaux champs sans réécrire
les cas existants. Toute modification du comportement commence par l'ajout d'un cas ici.

## Android

Prérequis : JDK 17 et SDK Android (plateforme 35), `ANDROID_HOME` défini (ou `android/local.properties` avec `sdk.dir=...`).

```bash
cd android
./gradlew test             # tests JVM, dont le jeu de cas partagé (module :core)
./gradlew assembleDebug    # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug     # installe sur un émulateur/appareil connecté (Android 8+)
```

Ou ouvrir le dossier `android/` dans Android Studio et lancer la configuration `app`.

### Builds « preview » des branches

Chaque push sur une branche autre que `main` (workflow `android-preview.yml`) lance les tests et publie
un APK en **pré-release GitHub `preview-<branche>`** (onglet *Releases* du dépôt), avec les captures des
widgets (`widget-screenshots.zip`) quand la branche les produit.

- L'APK preview s'installe **à côté** de l'app (`app.goalwidgets.preview`, « Goal Widgets (test) »,
  icône orange) : aucun risque pour les données de l'app principale.
- Pour y retrouver ses objectifs : *Exporter* dans l'app, puis *Importer* dans la preview.
- Il est minifié comme la release (R8) et ne se met pas à jour tout seul. Une pré-release n'est jamais
  « latest » : la mise à jour automatique de l'app l'ignore.

## iOS

Prérequis : macOS, Xcode 15+, [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
cd ios
xcodegen                   # génère GoalWidgets.xcodeproj à partir de project.yml
open GoalWidgets.xcodeproj # puis lancer le schéma GoalWidgets sur un simulateur iOS 17+
```

Tests (jeu de cas partagé) :

```bash
cd ios/GoalCore
swift test                 # ou ouvrir Package.swift dans Xcode et lancer les tests (⌘U)
```

## Localisation

Les textes sont externalisés, français par défaut, avec les mêmes clés sur les deux plateformes :

- Android : `android/app/src/main/res/values/strings.xml` (ajouter `values-xx/` pour d'autres langues) ;
- iOS : `ios/GoalWidgets/fr.lproj/Localizable.strings` (ajouter `xx.lproj/`).
