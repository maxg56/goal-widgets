# 07: iOS — widget d'écran d'accueil

**What to build:** Même comportement que le ticket 06, sur iPhone, via WidgetKit : widget configurable (choix de l'objectif via AppIntent), tailles petite et moyenne, timeline qui se rafraîchit à minuit local, rechargement forcé après une modification dans l'app. Bonus si simple : variante écran verrouillé.

**Blocked by:** 03 — iOS : créer un compte à rebours

**Status:** done (non vérifié)

> Écrit sans Xcode (machine Linux) : ni compilé, ni testé (XCTest non exécuté), ni lancé sur simulateur. Cases cochées = code écrit.

- [x] Widget configurable : choix de l'objectif
- [x] Plusieurs widgets possibles, y compris sur le même objectif
- [x] Tailles petite et moyenne ; mode clair/sombre
- [x] Timeline avec une entrée par jour à minuit local
- [x] Rechargement des timelines après modification/suppression dans l'app
- [x] État « Objectif supprimé » clair
- [x] Appui → ouvre l'app sur l'objectif
- [ ] Vérifié à la main sur simulateur (impossible ici : pas de Xcode)
- [x] Bonus : écran verrouillé (`accessoryRectangular`, `accessoryCircular`)
