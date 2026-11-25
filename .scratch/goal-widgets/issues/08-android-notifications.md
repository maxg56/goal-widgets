# 08: Android — notifications de jalons

**What to build:** Sur Android, l'app envoie des notifications aux jalons d'un objectif : mois-anniversaires et anniversaires annuels pour « depuis » ; J-30, J-7, J-1, jour J et rappels mensuels pour un compte à rebours. Les notifications sont calculées par le planificateur de jalons pur (objectif + date du jour + préférences + horizon → liste de notifications), puis programmées par une couche mince qui reprogramme tout l'horizon à chaque changement, au démarrage, au redémarrage, au changement de fuseau et via une tâche quotidienne.

**Blocked by:** 04 — Android : objectifs « depuis », modifier, supprimer

**Status:** ready-for-agent

- [ ] Interrupteur de notifications par objectif
- [ ] Réglage global de l'heure d'envoi
- [ ] Demande d'autorisation (Android 13+) au moment où l'utilisateur active une notification
- [ ] Mois-anniversaire d'un 29/30/31 → dernier jour des mois plus courts
- [ ] Un seul message par objectif et par jour (le jalon le plus fort l'emporte)
- [ ] Identifiants stables : reprogrammer ne crée pas de doublons
- [ ] Reprogrammation après redémarrage, changement de fuseau, modification, et quotidiennement
- [ ] Appui sur la notification → ouvre l'objectif
- [ ] Cas partagés du planificateur : tous les jalons ci-dessus, horizon, objectif désactivé, objectif passé
- [ ] Message d'aide si l'optimisation de batterie bloque les notifications (Xiaomi/Huawei)
