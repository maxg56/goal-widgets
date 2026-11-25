# 02: Android — créer un compte à rebours et le voir dans la liste

**What to build:** Sur Android, l'utilisateur crée un objectif « compte à rebours » (titre, date cible, date de départ par défaut = aujourd'hui, modifiable). L'objectif est enregistré localement (Room) et apparaît dans la liste avec les jours restants et une barre de progression en %. Le calcul est fait par le moteur de compte à rebours pur (objectif + date du jour → état d'affichage), couvert par le jeu de cas partagé.

**Blocked by:** 01 — Squelette des deux apps + jeu de cas partagé

**Status:** ready-for-agent

- [ ] Formulaire de création : titre, date cible, date de départ (défaut aujourd'hui)
- [ ] Validation : date cible postérieure à la date de départ
- [ ] L'objectif survit à la fermeture/réouverture de l'app
- [ ] La liste affiche « dans N jours » et une barre de progression
- [ ] Le pourcentage est borné entre 0 % et 100 %
- [ ] Jours comptés en jours calendaires locaux ; pluriels corrects (« 1 jour », « 2 jours »)
- [ ] Cas partagés ajoutés : avant départ, milieu, veille, passage à minuit, 29 février
- [ ] Le moteur ne dépend d'aucune API Android ; la date du jour est un paramètre
