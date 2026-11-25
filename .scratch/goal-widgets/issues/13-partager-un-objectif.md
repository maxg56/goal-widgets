# 13: Partager un objectif avec une autre personne (Android + iOS)

**What to build:** Depuis un objectif, « Partager » ouvre la feuille de partage du système avec un fichier `<titre>.goalwidgets.json` (WhatsApp, SMS, mail, AirDrop, Nearby Share…). La personne qui le reçoit touche le fichier → « Ouvrir avec Goal Widgets » → un aperçu de l'objectif (titre, dates, jours restants) → « Ajouter ». Chacun a ensuite sa propre copie (pas de synchronisation, pas de compte). Fonctionne Android ↔ iOS.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

**Status iOS :** done (non vérifié) — code écrit sans Xcode (machine Linux) : ni compilé ni exécuté ; aucun échange réel de fichier entre appareils.

## Format (décidé)
- Le fichier est un export `goal-widgets-export` **v1** (voir `docs/export-format.md`) contenant **un seul** objectif ; `notificationsEnabled` est écrit à `null` (le destinataire décide).
- Extension `.goalwidgets.json`, type MIME `application/json`. Android : filtre d'intention `ACTION_VIEW`/`ACTION_SEND` sur `application/json` (+ `application/octet-stream` et `text/plain` pour les messageries qui perdent le type). iOS : type de document exporté `app.goalwidgets.goal` conforme à `public.json`, extension `goalwidgets`, + `onOpenURL` pour les fichiers.
- Import : même logique que l'import existant (`GoalImport.plan`) — un objectif déjà présent (même `id`) n'est pas dupliqué ; l'aperçu l'indique (« Déjà dans tes objectifs »). Un fichier contenant plusieurs objectifs (export complet) ouvre le même écran d'aperçu en liste.

- [ ] Action « Partager » dans l'écran de l'objectif (Android + iOS)
- [ ] Ouverture du fichier reçu → écran d'aperçu → « Ajouter » / « Annuler »
- [ ] Doublon détecté et signalé ; fichier invalide → message clair, rien n'est écrit
- [ ] Test pur : un fichier de partage produit par `encode` d'un seul objectif est relu à l'identique (aller-retour) ; ajouter le cas `single-goal-share` à `shared-cases/export/`

## Avancement Android (branche `t12-android`)
- [x] « Partager » dans l'écran de modification (FileProvider `cache/shared/` + ACTION_SEND, `GoalShare.fileName`)
- [x] `SharedGoalActivity` (VIEW `content:` et SEND, `application/json` + `application/octet-stream` + `text/plain`) → aperçu (titre, dates, jours restants ; liste pour un export complet) → Ajouter / Annuler ; après ajout : `GoalWidgetsApplication.onGoalsChanged()` (widgets + notifications)
- [x] Doublon signalé (« Déjà dans tes objectifs », Ajouter désactivé si rien de neuf) ; fichier invalide → message de l'import, rien n'est écrit
- [x] `GoalShareTest` (aller-retour, `notificationsEnabled` null, nom de fichier) + 2 cas `single-goal-share` dans `shared-cases/export/cases.json` (fichier `valid-single-goal-share.goalwidgets.json`)

## Avancement iOS (branche `t12-ios`, non vérifié)
- [x] « Partager » dans l'écran de l'objectif (`GoalFormView` en modification, `ShareLink` + `SharedGoalFile: Transferable`, fichier `GoalShare.fileName(title:)`)
- [x] Type de document `app.goalwidgets.goal` (conforme à `public.json`, extension `goalwidgets`) + `public.json` en `Alternate`, `LSSupportsOpeningDocumentsInPlace = NO` (`project.yml`) ; `onOpenURL` (fichier) → `SharedGoalPreviewView` → Ajouter / Annuler
- [x] Doublon signalé (« Déjà dans tes objectifs », « Ajouter » désactivé s'il n'y a rien à ajouter) ; fichier invalide → message d'erreur de l'import, rien n'est écrit ; après ajout : widgets rechargés + notifications reprogrammées
- [x] Test pur aller-retour (`GoalShareTests`) ; le cas partagé `single-goal-share` est ajouté côté Android, `ExportSharedCasesTests` le rejoue sans modification
