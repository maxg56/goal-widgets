# 08: Android — notifications de jalons

**What to build:** Sur Android, l'app envoie des notifications aux jalons d'un objectif : mois-anniversaires et anniversaires annuels pour « depuis » ; J-30, J-7, J-1, jour J et rappels mensuels pour un compte à rebours. Les notifications sont calculées par le planificateur de jalons pur (objectif + date du jour + préférences + horizon → liste de notifications), puis programmées par une couche mince qui reprogramme tout l'horizon à chaque changement, au démarrage, au redémarrage, au changement de fuseau et via une tâche quotidienne.

**Blocked by:** 04 — Android : objectifs « depuis », modifier, supprimer

**Status:** done

- [x] Interrupteur de notifications par objectif (formulaire, désactivé par défaut ; `GoalEntity.notificationsEnabled`, migration Room 1 → 2)
- [x] Réglage global de l'heure d'envoi (cloche de la liste, défaut 9 h)
- [x] Demande d'autorisation (Android 13+) au moment où l'utilisateur active une notification
- [x] Mois-anniversaire d'un 29/30/31 → dernier jour des mois plus courts
- [x] Un seul message par objectif et par jour (le jalon le plus fort l'emporte)
- [x] Identifiants stables : reprogrammer ne crée pas de doublons (URI du `PendingIntent` et étiquette de notification = id du jalon)
- [x] Reprogrammation après redémarrage, changement d'heure/de fuseau, mise à jour de l'app, modification, au démarrage, et quotidiennement (WorkManager)
- [x] Appui sur la notification → ouvre l'objectif (`MainActivity.EXTRA_GOAL_ID`, comme le widget)
- [x] Cas partagés du planificateur : tous les jalons ci-dessus, horizon, objectif désactivé, objectif passé
- [x] Message d'aide si l'optimisation de batterie bloque les notifications (Xiaomi/Huawei)

**Non vérifié sur appareil** (pas d'émulateur) : affichage réel des notifications, demande d'autorisation, déclenchement des alarmes (Doze, surcouches Xiaomi/Huawei), réception de BOOT_COMPLETED/TIMEZONE_CHANGED, tâche WorkManager, migration Room sur une base existante, sélecteur d'heure. Vérifié : `./gradlew test assembleDebug` (22 cas partagés du planificateur + 3 tests de sélection).
