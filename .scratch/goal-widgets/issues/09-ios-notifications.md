# 09: iOS — notifications de jalons

**What to build:** Même comportement que le ticket 08, sur iPhone : planificateur Swift validé par les mêmes cas partagés, notifications locales programmées sur un horizon glissant qui respecte la limite iOS de 64 notifications en attente, et reprogrammation à l'ouverture de l'app, après modification, au changement de fuseau et via une tâche de fond.

**Blocked by:** 05 — iOS : objectifs « depuis », modifier, supprimer

**Status:** done (non vérifié)

> Non vérifié : écrit sans Xcode (machine Linux) — ni compilé ni testé. Les cases cochées le sont sur lecture du code. Le planificateur a été rejoué par un portage Python jetable contre les 22 cas de `shared-cases/planner.json` (0 écart ; attendus écrits à la main d'abord), ce qui ne dit rien de la compilation Swift. Livraison effective des notifications, tâche de fond et appui : à vérifier sur appareil.

- [x] Interrupteur par objectif et heure d'envoi globale — `GoalRecord.notificationsEnabled` (défaut false, migration légère) + interrupteur dans `GoalFormView` ; heure dans `NotificationSettingsView` (cloche de la liste, `UserDefaults`, défaut 9 h)
- [x] Demande d'autorisation au moment pertinent — à l'activation de l'interrupteur (`NotificationPermission.requestIfNeeded`) ; refus → lien vers les Réglages
- [x] Jamais plus de 64 notifications en attente, objectifs multiples compris (les plus proches d'abord) — `MilestonePlanner.closest(limit: 64)` (testé dans `MilestoneSelectionTests`, non exécuté) sur un horizon de 365 jours ; les demandes en attente hors sélection sont retirées
- [x] Reprogrammation à l'ouverture, après modification, changement de fuseau et en tâche de fond — `scenePhase == .active`, `ModelContext.didSave`, `NSSystemTimeZoneDidChange`, changement d'heure, `.backgroundTask(.appRefresh)` (identifiant déclaré dans `project.yml`) ; exécution non vérifiée
- [x] Appui sur la notification → ouvre l'objectif — `NotificationRouter` (délégué posé par `AppDelegate`) → `GoalDeepLink` → même feuille que le widget ; non vérifié sur appareil
- [ ] Tous les cas partagés du planificateur passent en XCTest — harnais `PlannerSharedCasesTests` écrit, non exécuté (pas de Xcode) ; seul le portage Python passe les 22 cas
