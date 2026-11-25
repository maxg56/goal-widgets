# 09: iOS — notifications de jalons

**What to build:** Même comportement que le ticket 08, sur iPhone : planificateur Swift validé par les mêmes cas partagés, notifications locales programmées sur un horizon glissant qui respecte la limite iOS de 64 notifications en attente, et reprogrammation à l'ouverture de l'app, après modification, au changement de fuseau et via une tâche de fond.

**Blocked by:** 05 — iOS : objectifs « depuis », modifier, supprimer

**Status:** ready-for-agent

- [ ] Interrupteur par objectif et heure d'envoi globale
- [ ] Demande d'autorisation au moment pertinent
- [ ] Jamais plus de 64 notifications en attente, objectifs multiples compris (les plus proches d'abord)
- [ ] Reprogrammation à l'ouverture, après modification, changement de fuseau et en tâche de fond
- [ ] Appui sur la notification → ouvre l'objectif
- [ ] Tous les cas partagés du planificateur passent en XCTest
