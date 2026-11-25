# 05: iOS — objectifs « depuis », modifier, supprimer, état « atteint »

**What to build:** Même comportement que le ticket 04, sur iPhone : objectifs « depuis », états « Aujourd'hui » / « Atteint », conversion en compteur « depuis », modification et suppression.

**Blocked by:** 03 — iOS : créer un compte à rebours

**Status:** done (non vérifié)

> Non vérifié : écrit sans Xcode (machine Linux) — ni compilé ni testé. Les cases cochées le sont sur lecture du code. La logique du moteur (phases, `percent`, `breakdown`) a été rejouée par un portage Python jetable contre les 21 cas de `shared-cases/engine.json` et les vecteurs de `SinceEngineTests` (0 écart), ce qui ne dit rien de la compilation Swift.

- [x] Choix du type à la création — sélecteur segmenté dans `GoalFormView` (création et modification) ; rendu non vérifié (pas de Xcode)
- [ ] Affichages identiques à Android pour chaque phase — iOS fait (`upcoming`/`today`/`reached`/`elapsed`, clés de libellés au Journal `[t05]`), mais le ticket 04 Android n'est pas encore fait : identité non vérifiable
- [x] Conversion d'un objectif atteint en compteur « depuis » — `Goal.continuedAsSince()` (testé dans `SinceEngineTests`) ; balayage/menu contextuel dans la liste et bouton dans le formulaire de modification
- [x] Modification et suppression avec confirmation — `GoalFormView(mode: .edit)` + `confirmationDialog` (liste et formulaire) ; exécution non vérifiée (pas de Xcode)
- [ ] Tous les cas partagés (y compris ceux ajoutés par le ticket 04 s'il est fait) passent en XCTest — harnais étendu (`percent`, `breakdown`), non exécuté (pas de Xcode) ; 04 n'a pas encore ajouté ses cas
