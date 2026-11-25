# 14: Mode debug des notifications (builds de développement uniquement)

**What to build:** Dans les builds de debug seulement (Android `BuildConfig.DEBUG`, iOS `#if DEBUG`), l'écran des réglages de notifications affiche une section « Debug » qui permet de tester les notifications sans attendre : liste des jalons programmés (date, heure, objectif, texte), bouton « Envoyer maintenant » par jalon, bouton « Programmer un jalon de test dans 10 secondes » pour un objectif choisi, et état de l'autorisation. Rien de tout cela n'existe dans un build de release.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

**Status iOS :** done (non vérifié) — code écrit sans Xcode (machine Linux) : ni compilé (Debug ni Release) ni exécuté ; aucune notification réellement livrée.

- [ ] Section « Debug » visible uniquement en debug (absente du code compilé en release : `BuildConfig.DEBUG` / `#if DEBUG`)
- [ ] Liste des jalons programmés, avec l'heure d'envoi réelle
- [ ] « Envoyer maintenant » : passe par le même chemin d'affichage que l'alarme réelle (texte, appui → ouverture de l'objectif), sans la vérification « jour même »
- [ ] « Dans 10 secondes » : vraie alarme (Android) / vraie demande `UNTimeIntervalNotificationTrigger` (iOS) pour vérifier la livraison de bout en bout
- [ ] Android : `buildConfig = true` activé si nécessaire ; vérifier que `assembleRelease` compile sans la section

## Avancement iOS (branche `t12-ios`, non vérifié)
- [x] Section « Debug » dans `NotificationSettingsView`, fichier `NotificationDebugSection.swift` entièrement sous `#if DEBUG` (et l'appel aussi) → absente du binaire de release ; build Release non vérifié
- [x] Demandes en attente (`pendingNotificationRequests`) : heure réelle (`nextTriggerDate()`), titre, texte, identifiant ; triées par date
- [x] « Envoyer maintenant » : même contenu que la demande (texte + lien profond → ouverture de l'objectif), `trigger` nil, nouvel identifiant `debug-now-…` (l'original reste programmé) ; iOS n'a pas de vérification « jour même » à l'envoi (contenu figé à la programmation)
- [x] « Dans 10 secondes » : vraie `UNTimeIntervalNotificationTrigger(10)` pour l'objectif choisi, contenu = prochain jalon planifié via `NotificationScheduler.content` (même chemin que les vraies) ; identifiants `debug-…` jamais retirés par la reprogrammation
- [x] État de l'autorisation affiché
