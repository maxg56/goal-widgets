# 04: Android — objectifs « depuis », modifier, supprimer, état « atteint »

**What to build:** Sur Android, l'utilisateur peut aussi créer un objectif « depuis » une date passée, qui affiche le temps écoulé en jours et en forme lisible (« 1 an 2 mois 3 jours »). Un compte à rebours affiche « Aujourd'hui » le jour J et « Atteint il y a N jours » ensuite, et peut être converti en compteur « depuis ». L'utilisateur peut modifier ou supprimer n'importe quel objectif.

**Blocked by:** 02 — Android : créer un compte à rebours

**Status:** ready-for-agent

- [ ] Choix du type (compte à rebours / depuis) à la création
- [ ] Affichage « depuis N jours » + décomposition années/mois/jours
- [ ] Jour J : « Aujourd'hui » ; après : « Atteint il y a N jours » (jamais de nombre négatif)
- [ ] Action « Continuer en compteur depuis » sur un objectif atteint
- [ ] Modification de tous les champs ; suppression avec confirmation
- [ ] Cas partagés ajoutés : phases upcoming/today/reached/elapsed, fins de mois (31 → 30/28), années bissextiles
