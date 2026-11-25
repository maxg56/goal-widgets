# 13: Partager un objectif avec une autre personne (Android + iOS)

**What to build:** Depuis un objectif, « Partager » ouvre la feuille de partage du système avec un fichier `<titre>.goalwidgets.json` (WhatsApp, SMS, mail, AirDrop, Nearby Share…). La personne qui le reçoit touche le fichier → « Ouvrir avec Goal Widgets » → un aperçu de l'objectif (titre, dates, jours restants) → « Ajouter ». Chacun a ensuite sa propre copie (pas de synchronisation, pas de compte). Fonctionne Android ↔ iOS.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

## Format (décidé)
- Le fichier est un export `goal-widgets-export` **v1** (voir `docs/export-format.md`) contenant **un seul** objectif ; `notificationsEnabled` est écrit à `null` (le destinataire décide).
- Extension `.goalwidgets.json`, type MIME `application/json`. Android : filtre d'intention `ACTION_VIEW`/`ACTION_SEND` sur `application/json` (+ `application/octet-stream` et `text/plain` pour les messageries qui perdent le type). iOS : type de document exporté `app.goalwidgets.goal` conforme à `public.json`, extension `goalwidgets`, + `onOpenURL` pour les fichiers.
- Import : même logique que l'import existant (`GoalImport.plan`) — un objectif déjà présent (même `id`) n'est pas dupliqué ; l'aperçu l'indique (« Déjà dans tes objectifs »). Un fichier contenant plusieurs objectifs (export complet) ouvre le même écran d'aperçu en liste.

- [ ] Action « Partager » dans l'écran de l'objectif (Android + iOS) — Android fait (icône dans l'écran de modification, FileProvider + ACTION_SEND)
- [ ] Ouverture du fichier reçu → écran d'aperçu → « Ajouter » / « Annuler » — Android fait (`SharedGoalActivity`, VIEW/SEND)
- [ ] Doublon détecté et signalé ; fichier invalide → message clair, rien n'est écrit — Android fait
- [ ] Test pur : un fichier de partage produit par `encode` d'un seul objectif est relu à l'identique (aller-retour) ; ajouter le cas `single-goal-share` à `shared-cases/export/` — Android fait (`GoalShareTest`, 2 cas `single-goal-share`)
