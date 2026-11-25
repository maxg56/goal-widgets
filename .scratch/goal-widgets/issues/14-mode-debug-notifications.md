# 14: Mode debug des notifications (builds de développement uniquement)

**What to build:** Dans les builds de debug seulement (Android `BuildConfig.DEBUG`, iOS `#if DEBUG`), l'écran des réglages de notifications affiche une section « Debug » qui permet de tester les notifications sans attendre : liste des jalons programmés (date, heure, objectif, texte), bouton « Envoyer maintenant » par jalon, bouton « Programmer un jalon de test dans 10 secondes » pour un objectif choisi, et état de l'autorisation. Rien de tout cela n'existe dans un build de release.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Section « Debug » visible uniquement en debug (absente du code compilé en release : `BuildConfig.DEBUG` / `#if DEBUG`) — Android fait (code dans `src/debug`, stub vide dans `src/release`, appel gardé par `BuildConfig.DEBUG` ; vérifié : aucune classe de debug dans l'APK release)
- [ ] Liste des jalons programmés, avec l'heure d'envoi réelle — Android fait (`MilestoneNotifications.wanted`)
- [ ] « Envoyer maintenant » : passe par le même chemin d'affichage que l'alarme réelle (texte, appui → ouverture de l'objectif), sans la vérification « jour même » — Android fait (`MilestoneNotifications.show`)
- [ ] « Dans 10 secondes » : vraie alarme (Android) / vraie demande `UNTimeIntervalNotificationTrigger` (iOS) pour vérifier la livraison de bout en bout — Android fait (exacte si autorisée, sinon `setAndAllowWhileIdle`)
- [x] Android : `buildConfig = true` activé si nécessaire ; vérifier que `assembleRelease` compile sans la section (hors `lintVital*`, dont l'artefact n'est pas en cache hors ligne)
