# Spec — Goal Widgets (compte à rebours / depuis une date)

## Problem Statement

Je veux avoir sous les yeux, directement sur l'écran d'accueil de mon téléphone, où j'en suis par rapport à des dates qui comptent pour moi : combien de jours il reste avant un objectif (un examen, un voyage, une échéance) et quel pourcentage du chemin est déjà parcouru, ou au contraire depuis combien de temps quelque chose s'est passé (un arrêt, un début de projet, une rencontre). Aujourd'hui, il faut ouvrir un calendrier et compter à la main ; rien ne me rappelle les étapes (« ça fait un mois », « c'est dans 7 jours »). Je veux pouvoir en suivre autant que je veux, sur n'importe quel téléphone.

## Solution

Une application mobile native (Android en Kotlin, iOS en Swift) dans laquelle je crée des **objectifs** datés. Chaque objectif est soit :

- un **compte à rebours** : une date de départ et une date cible ; l'app affiche les jours restants et le pourcentage de progression entre départ et cible ;
- un **compteur depuis** : une date passée ; l'app affiche le temps écoulé depuis (jours, et forme lisible « 1 an 3 mois 2 jours »).

Je peux placer autant de **widgets** que je veux sur l'écran d'accueil, chacun lié à un objectif de mon choix, qui se met à jour chaque jour tout seul. L'app m'envoie des **notifications de jalons** : chaque mois écoulé (« ça fait 3 mois »), et à l'approche d'une cible (« c'est dans 30 jours / 7 jours / demain / aujourd'hui »).

Un widget de bureau (ordinateur) est un bonus facultatif, hors périmètre de cette première version.

## User Stories

### Gestion des objectifs
1. En tant qu'utilisateur, je veux créer un objectif « compte à rebours » avec un titre et une date cible, afin de savoir combien de temps il me reste.
2. En tant qu'utilisateur, je veux que la date de départ d'un compte à rebours soit par défaut la date de création, afin de ne rien avoir à saisir de plus dans le cas courant.
3. En tant qu'utilisateur, je veux pouvoir choisir une date de départ personnalisée, afin que le pourcentage reflète la vraie période de préparation.
4. En tant qu'utilisateur, je veux créer un objectif « depuis » avec un titre et une date passée, afin de voir combien de temps s'est écoulé.
5. En tant qu'utilisateur, je veux créer autant d'objectifs que je veux, afin de suivre plusieurs échéances à la fois.
6. En tant qu'utilisateur, je veux voir la liste de tous mes objectifs dans l'app, avec leur état actuel, afin d'avoir une vue d'ensemble.
7. En tant qu'utilisateur, je veux modifier le titre, les dates ou le type d'un objectif, afin de corriger une erreur ou suivre un changement de plan.
8. En tant qu'utilisateur, je veux supprimer un objectif, afin de nettoyer ceux qui ne me servent plus.
9. En tant qu'utilisateur, je veux choisir une couleur et/ou un emoji pour chaque objectif, afin de les distinguer d'un coup d'œil.
10. En tant qu'utilisateur, je veux qu'un compte à rebours dont la date est passée m'indique clairement qu'il est atteint (« Atteint il y a 5 jours »), afin de ne pas voir un nombre négatif incompréhensible.
11. En tant qu'utilisateur, je veux pouvoir convertir un compte à rebours atteint en compteur « depuis », afin de continuer à suivre l'événement après coup.
12. En tant qu'utilisateur, je veux pouvoir trier mes objectifs (par date la plus proche, par création), afin de retrouver vite les plus urgents.

### Affichage et calcul
13. En tant qu'utilisateur, je veux voir le nombre de jours restants pour un compte à rebours, afin de mesurer l'échéance.
14. En tant qu'utilisateur, je veux voir le pourcentage de progression entre la date de départ et la date cible, afin de sentir où j'en suis.
15. En tant qu'utilisateur, je veux que le pourcentage soit borné entre 0 % et 100 %, afin de ne jamais voir de valeur absurde.
16. En tant qu'utilisateur, je veux une barre ou un anneau de progression, afin de visualiser le pourcentage sans lire de chiffre.
17. En tant qu'utilisateur, je veux voir le temps écoulé d'un compteur « depuis » en jours et en forme lisible (années, mois, jours), afin de l'apprécier dans les deux échelles.
18. En tant qu'utilisateur, je veux que les jours soient comptés en jours calendaires dans mon fuseau horaire local, afin que « dans 1 jour » veuille bien dire demain.
19. En tant qu'utilisateur, je veux que le jour J affiche « Aujourd'hui » plutôt que « 0 jour », afin que l'affichage soit naturel.
20. En tant qu'utilisateur, je veux que les singuliers/pluriels soient corrects (« 1 jour », « 2 jours »), afin que l'app ait l'air soignée.
21. En tant qu'utilisateur, je veux que l'app soit en français (et prête pour d'autres langues), afin de l'utiliser dans ma langue.

### Widgets
22. En tant qu'utilisateur, je veux ajouter un widget sur l'écran d'accueil de mon téléphone et choisir l'objectif qu'il affiche, afin de le voir sans ouvrir l'app.
23. En tant qu'utilisateur, je veux ajouter plusieurs widgets, chacun lié à un objectif différent, afin de suivre plusieurs dates en même temps.
24. En tant qu'utilisateur, je veux pouvoir mettre deux widgets sur le même objectif (ex. tailles différentes), afin d'organiser mon écran comme je veux.
25. En tant qu'utilisateur, je veux au moins une taille petite (chiffre + titre) et une taille moyenne (chiffre + titre + progression), afin d'adapter le widget à la place disponible.
26. En tant qu'utilisateur, je veux que le widget se mette à jour automatiquement au changement de jour, afin que le chiffre soit toujours juste sans ouvrir l'app.
27. En tant qu'utilisateur, je veux qu'un widget reflète immédiatement une modification de l'objectif faite dans l'app, afin de ne pas voir d'information périmée.
28. En tant qu'utilisateur, je veux qu'un widget dont l'objectif a été supprimé affiche un état clair (« Objectif supprimé — touchez pour en choisir un »), afin de ne pas rester bloqué.
29. En tant qu'utilisateur, je veux qu'un appui sur le widget ouvre le détail de l'objectif concerné dans l'app, afin de le consulter ou le modifier.
30. En tant qu'utilisateur, je veux pouvoir changer l'objectif affiché par un widget existant, afin de ne pas devoir le supprimer et le recréer.
31. En tant qu'utilisateur, je veux que les widgets respectent le mode clair/sombre du système, afin qu'ils s'intègrent à mon écran.
32. En tant qu'utilisateur Android, je veux que les widgets fonctionnent sur les lanceurs courants (Pixel, Samsung, Xiaomi…), afin que l'app marche « sur tous les téléphones ».
33. En tant qu'utilisateur iPhone, je veux des widgets sur l'écran d'accueil (et idéalement l'écran verrouillé), afin d'avoir la même expérience qu'Android.

### Notifications
34. En tant qu'utilisateur, je veux recevoir une notification à chaque « mois-anniversaire » d'un compteur « depuis » (« Ça fait 3 mois que… »), afin de célébrer les étapes.
35. En tant qu'utilisateur, je veux une notification à chaque anniversaire annuel (« Ça fait 1 an »), afin de marquer les grandes étapes.
36. En tant qu'utilisateur, je veux des notifications à l'approche d'un compte à rebours (30 jours, 7 jours, 1 jour, jour J), afin de ne pas être pris de court.
37. En tant qu'utilisateur, je veux aussi un rappel mensuel sur un compte à rebours long (« Plus que 4 mois »), afin de garder l'objectif en tête.
38. En tant qu'utilisateur, je veux activer/désactiver les notifications par objectif, afin de ne recevoir que celles qui m'intéressent.
39. En tant qu'utilisateur, je veux choisir l'heure de la journée à laquelle les notifications arrivent, afin de ne pas être dérangé la nuit.
40. En tant qu'utilisateur, je veux que les notifications des 31 du mois tombent sur le dernier jour des mois plus courts, afin de ne jamais rater un mois-anniversaire.
41. En tant qu'utilisateur, je veux que les notifications continuent d'arriver même si je n'ouvre pas l'app pendant longtemps, afin de pouvoir lui faire confiance.
42. En tant qu'utilisateur, je veux que les notifications soient reprogrammées après un redémarrage du téléphone ou un changement de fuseau horaire, afin qu'elles restent justes.
43. En tant qu'utilisateur, je veux qu'un appui sur une notification ouvre le détail de l'objectif concerné, afin d'avoir le détail.
44. En tant qu'utilisateur, je veux que l'app me demande l'autorisation de notifier au moment pertinent (quand j'active une notification), afin de comprendre pourquoi elle la demande.

### Données et confidentialité
45. En tant qu'utilisateur, je veux que mes objectifs soient stockés uniquement sur mon téléphone, sans compte, afin de garder mes dates privées.
46. En tant qu'utilisateur, je veux que mes objectifs survivent à une mise à jour de l'app, afin de ne rien perdre.
47. En tant qu'utilisateur, je veux pouvoir exporter/importer mes objectifs dans un fichier, afin de les transférer sur un nouveau téléphone.

### Bonus (facultatif, plus tard)
48. En tant qu'utilisateur, je veux un widget sur mon ordinateur affichant un objectif, afin de le voir aussi quand je travaille.

## Implementation Decisions

- **Plateformes** : deux applications natives — Android (Kotlin, widgets via Jetpack Glance) et iOS (Swift/SwiftUI, widgets via WidgetKit). Compatibilité visée : Android 8+ et iOS 17+.
- **Modèle de domaine** — un `Goal` :
  - `id`, `title`, `kind` (`countdown` | `since`), `startDate` (date locale, sans heure), `targetDate` (date locale, uniquement pour `countdown`), `color`, `emoji`, `notificationsEnabled`, `createdAt`.
  - Les dates sont des **dates calendaires locales** (pas d'instants UTC) : un objectif « le 14 mars » reste le 14 mars quel que soit le fuseau.
- **Module 1 — Moteur de compte à rebours (pur, sans dépendance OS)** :
  - Entrée : un `Goal` et la date du jour.
  - Sortie : un état d'affichage — `phase` (`upcoming` | `today` | `reached` | `elapsed`), `days` (entier ≥ 0), `progress` (0.0–1.0, `countdown` uniquement, borné), `breakdown` (années/mois/jours), et les libellés formatés (« dans 12 jours », « Aujourd'hui », « Atteint il y a 3 jours », « depuis 1 an 2 mois »).
  - Utilisé à la fois par l'écran de l'app et par les widgets : un seul endroit calcule.
- **Module 2 — Planificateur de jalons (pur)** :
  - Entrée : un `Goal`, la date du jour, les préférences (heure de notification), et un horizon (ex. 60 jours).
  - Sortie : la liste ordonnée des notifications à venir dans l'horizon — date-heure, identifiant stable (dérivé de l'objectif + type + date), titre, texte.
  - Règles : mois-anniversaires et anniversaires annuels pour `since` (avec repli sur le dernier jour du mois si le jour n'existe pas) ; J-30, J-7, J-1, Jour J et rappels mensuels pour `countdown` ; rien avant aujourd'hui ; aucun doublon le même jour (le jalon le plus fort l'emporte).
- **Couche OS (mince, non testée unitairement)** :
  - Stockage local : Room (Android) / SwiftData (iOS), partagé avec l'extension widget (App Group sur iOS).
  - Programmation : à chaque modification d'objectif, au démarrage de l'app, au redémarrage du téléphone, au changement de fuseau et via une tâche périodique quotidienne (WorkManager / BGTaskScheduler), on annule et reprogramme les notifications de l'horizon à partir du planificateur. L'horizon glissant contourne la limite iOS de 64 notifications en attente.
  - Widgets : chaque instance de widget stocke l'`id` de l'objectif choisi, et pour le widget « Objectif » le graphique affiché (anneau par défaut, arcs, barres, points) et les éléments retirés (configuration via un écran de choix sur Android, `AppIntent` configurable sur iOS). Rafraîchissement programmé à minuit local (timeline iOS ; `updatePeriodMillis` + alarme minuit sur Android) et forcé après toute modification dans l'app.
- **Interface (modern-ui)** : accueil en cartes ; appui → détail de l'objectif (grand nombre, graphique au choix — points, anneau, arcs, barres —, frise de l'objectif, personnalisable) ; frise de tous les objectifs en sous-écran. Les calculs des graphiques (périodes, hauteurs, prochain cap d'un « depuis », positions de la frise) sont dans le module pur et figés par `shared-cases/charts.json`.
- **Unité des graphiques (Android)** : barres, arcs et grille de points se découpent par jour, semaine ou mois. Réglage général dans le menu ⋮ de l'accueil (« Automatique » par défaut = règle selon la durée) ; chaque objectif peut le remplacer dans son formulaire (« Général » = hérite, colonne `chartUnit` du schéma v4, non exportée). Une unité qui donnerait trop de périodes (plus de 31 barres, 12 arcs ou 366 points) passe à l'unité au-dessus. La grille des périodes « mois » / « semaine » du widget reste au jour. Logique dans `:core` (`ChartUnit.resolve`, `compute(…, unit)`), testée.
- **Localisation** : chaînes externalisées dès le départ, français par défaut.
- **Cohérence entre les deux plateformes** : le moteur et le planificateur sont écrits deux fois (Kotlin et Swift). Leur comportement est figé par un **jeu de cas partagé** (fichier de données : objectif + date du jour → sortie attendue) que les deux suites de tests exécutent.

### Compteurs « série » (remise à zéro)

- **Besoin** : un compteur « depuis » qui mesure une abstinence ou une habitude (« sans fumer », « sans écran le soir ») doit pouvoir repartir de zéro en un geste après une rechute, sans perdre les séries précédentes.
- **Option par objectif** : interrupteur « Série (remise à zéro) » dans le formulaire, proposé uniquement pour un compteur « depuis », désactivé par défaut (et pour les objectifs existants). Seuls ces compteurs ont le bouton, le record et l'historique.
- **Remise à zéro** : la série en cours (départ → jour de la remise) rejoint l'historique et le compteur repart de ce jour. Dans l'app : bouton « Remettre à zéro » du détail et bouton ↺ de la carte d'accueil, toujours confirmés ; la date proposée est aujourd'hui, une date passée peut être choisie (entre le départ de la série en cours et aujourd'hui). Widget « Objectif » : bouton ↺, premier appui → « Confirmer ? » pendant 5 s, second appui → remise à zéro à aujourd'hui, sans ouvrir l'app.
- **Affichage** : détail = record (« Record en cours ! » quand la série en cours l'égale ou le bat), nombre de remises à zéro, liste des séries passées (la plus récente d'abord, 🏆 sur la plus longue) ; widget = « Record N j » une fois au moins une remise à zéro faite.
- **Notifications** : les jalons repartent de la nouvelle date (le planificateur ne change pas). Sur iOS, une remise à zéro depuis le widget retire les jalons en attente de l'objectif ; ceux de la nouvelle série sont programmés à la prochaine ouverture de l'app (ou tâche de fond).
- **Stockage** : `streak` (booléen) et l'historique en texte `AAAA-MM-JJ/AAAA-MM-JJ;…` (ordre chronologique, entrées illisibles ignorées). Room v5 (`MIGRATION_4_5`, deux `ALTER TABLE`) ; SwiftData : deux propriétés avec valeur par défaut (migration légère). Non inclus dans l'export pour l'instant.
- **Logique pure** : `Streaks` / `WidgetResetTap` dans `:core` et `GoalCore`, figés par `shared-cases/streaks.json`.

### Mises à jour automatiques (Android)

- **Publication** : chaque push sur `main` touchant `android/`, `shared-cases/` ou le workflow lance `.github/workflows/android-release.yml` (aussi déclenchable à la main) : `./gradlew test assembleRelease`, APK signé avec la clé de debug de l'appareil (secrets `SIGNING_*`), GitHub Release `build-<versionCode>` « Goal Widgets <versionName> » marquée *latest* (corps = message du commit, 10 dernières gardées). `versionCode` = 1000 + numéro d'exécution (toujours au-dessus du build de debug local, `versionCode` 1), `versionName` = `1.<run> (<sha court>)`.
- **Recherche, seulement app ouverte** : à chaque retour au premier plan puis toutes les 15 min tant qu'elle reste visible (aucun service, aucune tâche de fond). Un `GET` anonyme sur `api.github.com/repos/maxg56/goal-widgets/releases/latest` (User-Agent `GoalWidgets-Updater`, aucune donnée personnelle) ; échec réseau = rien. Logique pure dans `:core` (`LatestRelease.parse` / `isNewerThan`, testée).
- **Installation** : APK téléchargé dans le cache (taille vérifiée), signature comparée à l'app installée (différente → « Mise à jour impossible (signature différente) », pas de nouvel essai automatique pour cette version), puis session `PackageInstaller`. La première fois Android demande l'autorisation « sources inconnues » (pastille discrète, jamais de popup forcée) puis une confirmation ; ensuite, sur Android 12+, les mises à jour suivantes s'installent sans confirmation. L'app se ferme à la fin de l'installation (remplacement par le système) ; les données sont conservées.
- **Interface** : pastille « Mise à jour en cours… » pendant le téléchargement ; ligne « Version x · Rechercher une mise à jour » dans les réglages des notifications.
- **Samsung** : le « Bloqueur automatique » (Auto Blocker) interdit l'installation d'applications hors Play Store ; il doit rester désactivé pour que l'app se mette à jour.

## Testing Decisions

- **Ce qu'est un bon test** : il vérifie uniquement le comportement externe — « pour cet objectif et cette date du jour, j'obtiens cet affichage / ces notifications » — sans dépendre de la structure interne. Pas de mock de l'horloge système : la date du jour est un paramètre.
- **Modules testés** : uniquement les deux seams validés :
  1. Moteur de compte à rebours : jours restants/écoulés, bornes du pourcentage (avant départ, jour J, après cible), passage à minuit, années bissextiles (29 février), décomposition années/mois/jours en fin de mois, pluriels et libellés.
  2. Planificateur de jalons : mois-anniversaires (dont les 29/30/31), anniversaires annuels, J-30/J-7/J-1/Jour J, dédoublonnage, horizon, objectifs désactivés, objectifs dont la date est passée, stabilité des identifiants entre deux appels.
- **Jeu de cas partagé** : la même liste de cas (format de données neutre) est rejouée par les tests JVM (JUnit) et par les tests XCTest, garantissant qu'Android et iOS affichent exactement la même chose.
- **Non testé automatiquement** : rendu des widgets, programmation effective par l'OS — vérifiés à la main sur appareil/émulateur.
- **Prior art** : aucun dans ce dépôt (nouveau projet).

## Out of Scope

- Widget ou application de bureau (macOS / Windows / Linux) — bonus facultatif pour une version ultérieure.
- Synchronisation cloud, comptes utilisateur, partage d'objectifs entre personnes.
- Objectifs non datés (ex. « 10 000 pas », progression manuelle) : le pourcentage est uniquement temporel.
- Heures précises (objectif « le 14 mars à 18 h ») : la granularité est le jour.
- Widgets Apple Watch / Wear OS.
- Intégration aux calendriers du système (import d'événements).

## Further Notes

- **Alternative à considérer** : Kotlin Multiplatform permettrait d'écrire le moteur et le planificateur une seule fois et de les partager avec l'app iOS, tout en gardant des UI et widgets natifs. Cela supprimerait le besoin du jeu de cas dupliqué ; à trancher avant de commencer.
- **Bonus bureau** : si le moteur est partagé (KMP ou fichier de cas), un widget de bureau pourrait réutiliser la même logique (ex. widget macOS via WidgetKit, quasi gratuit depuis l'app iOS).
- Certains lanceurs Android (Xiaomi, Huawei) limitent les tâches de fond : prévoir un message guidant l'utilisateur pour désactiver l'optimisation de batterie si les notifications n'arrivent pas.
- Pas de tracker de tickets configuré : ce fichier tient lieu de spec. Étiquette prévue : `ready-for-agent`.
