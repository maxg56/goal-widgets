# 02: Android — créer un compte à rebours et le voir dans la liste

**What to build:** Sur Android, l'utilisateur crée un objectif « compte à rebours » (titre, date cible, date de départ par défaut = aujourd'hui, modifiable). L'objectif est enregistré localement (Room) et apparaît dans la liste avec les jours restants et une barre de progression en %. Le calcul est fait par le moteur de compte à rebours pur (objectif + date du jour → état d'affichage), couvert par le jeu de cas partagé.

**Blocked by:** 01 — Squelette des deux apps + jeu de cas partagé

**Status:** done

- [x] Formulaire de création : titre, date cible, date de départ (défaut aujourd'hui)
- [x] Validation : date cible postérieure à la date de départ
- [x] L'objectif survit à la fermeture/réouverture de l'app
- [x] La liste affiche « dans N jours » et une barre de progression
- [x] Le pourcentage est borné entre 0 % et 100 %
- [x] Jours comptés en jours calendaires locaux ; pluriels corrects (« 1 jour », « 2 jours »)
- [x] Cas partagés ajoutés : avant départ, milieu, veille, passage à minuit, 29 février
- [x] Le moteur ne dépend d'aucune API Android ; la date du jour est un paramètre

**Notes (t02) :** vérifié par `./gradlew test assembleDebug` (21 cas partagés + 6 tests de validation). Aucun émulateur : l'écran (formulaire, sélecteur de date, liste, barre de progression) et la persistance après fermeture/réouverture n'ont pas été vérifiés à l'écran — la persistance repose sur Room (base `goals.db`, schéma exporté dans `android/app/schemas`). Le moteur gère déjà les phases `today`/`reached` (alignées sur t03) ; l'affichage « Aujourd'hui » / « Atteint il y a N jours » est en place, la conversion en « depuis » reste au ticket 04.
