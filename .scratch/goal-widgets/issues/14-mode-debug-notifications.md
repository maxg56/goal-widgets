# 14: Mode debug des notifications (builds de développement uniquement)

**What to build:** Dans les builds de debug seulement (Android `BuildConfig.DEBUG`, iOS `#if DEBUG`), l'écran des réglages de notifications affiche une section « Debug » qui permet de tester les notifications sans attendre : liste des jalons programmés (date, heure, objectif, texte), bouton « Envoyer maintenant » par jalon, bouton « Programmer un jalon de test dans 10 secondes » pour un objectif choisi, et état de l'autorisation. Rien de tout cela n'existe dans un build de release.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

**Status iOS :** done (non vérifié) — code écrit sans Xcode (machine Linux) : ni compilé (Debug ni Release) ni exécuté ; aucune notification réellement livrée.

- [ ] Section « Debug » visible uniquement en debug (absente du code compilé en release : `BuildConfig.DEBUG` / `#if DEBUG`)
- [ ] Liste des jalons programmés, avec l'heure d'envoi réelle
- [ ] « Envoyer maintenant » : passe par le même chemin d'affichage que l'alarme réelle (texte, appui → ouverture de l'objectif), sans la vérification « jour même »
- [ ] « Dans 10 secondes » : vraie alarme (Android) / vraie demande `UNTimeIntervalNotificationTrigger` (iOS) pour vérifier la livraison de bout en bout
- [x] Android : `buildConfig = true` activé si nécessaire ; vérifier que `assembleRelease` compile sans la section

## Avancement Android (branche `t12-android`)
- [x] Code de la section dans `app/src/debug` (stub vide dans `app/src/release`), appel gardé par `BuildConfig.DEBUG` ; vérifié : aucune classe de debug dans l'APK release
- [x] Jalons programmés avec l'heure d'envoi réelle (`MilestoneNotifications.wanted`, même calcul que la reprogrammation) + « Envoyer maintenant » (`MilestoneNotifications.show`, chemin d'affichage de l'alarme, sans vérification de date)
- [x] « Dans 10 secondes » : vraie alarme (`setExactAndAllowWhileIdle` si l'alarme exacte est autorisée — `SCHEDULE_EXACT_ALARM` dans le manifeste debug seulement — sinon `setAndAllowWhileIdle`, possiblement différée) → `DebugMilestoneReceiver` → `show`
- [x] État de l'autorisation (permission, notifications de l'app, importance du canal, alarmes exactes)
- [x] `assembleRelease` compile (hors `lintVital*` : artefact lint absent du cache hors ligne)

## Avancement iOS (branche `t12-ios`, non vérifié)
- [x] Section « Debug » dans `NotificationSettingsView`, fichier `NotificationDebugSection.swift` entièrement sous `#if DEBUG` (et l'appel aussi) → absente du binaire de release ; build Release non vérifié
- [x] Demandes en attente (`pendingNotificationRequests`) : heure réelle (`nextTriggerDate()`), titre, texte, identifiant ; triées par date
- [x] « Envoyer maintenant » : même contenu que la demande (texte + lien profond → ouverture de l'objectif), `trigger` nil, nouvel identifiant `debug-now-…` (l'original reste programmé) ; iOS n'a pas de vérification « jour même » à l'envoi (contenu figé à la programmation)
- [x] « Dans 10 secondes » : vraie `UNTimeIntervalNotificationTrigger(10)` pour l'objectif choisi, contenu = prochain jalon planifié via `NotificationScheduler.content` (même chemin que les vraies) ; identifiants `debug-…` jamais retirés par la reprogrammation
- [x] État de l'autorisation affiché
