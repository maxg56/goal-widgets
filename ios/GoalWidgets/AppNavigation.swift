// Non vérifié : écrit sans Xcode (machine Linux).
import Combine
import SwiftUI
import SwiftData
import WidgetKit
import GoalCore

/// Écrans poussés sur la pile de navigation (l'accueil est la racine).
enum Route: Hashable {
    /// Détail ouvert sur un objectif (le pager peut ensuite passer aux voisins).
    case detail(UUID)
    /// Frise de tous les objectifs.
    case timeline
}

/// Formulaire en plein écran : création ou modification.
struct FormRequest: Identifiable {
    let id = UUID()
    let record: GoalRecord?
}

/// Date du jour de l'interface : relue au retour au premier plan et au passage de minuit.
final class TodayClock: ObservableObject {
    @Published private(set) var today = LocalDate.today()
    private var cancellable: AnyCancellable?

    init() {
        cancellable = NotificationCenter.default.publisher(for: .NSCalendarDayChanged)
            .merge(with: NotificationCenter.default.publisher(for: .NSSystemTimeZoneDidChange))
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.refresh() }
    }

    func refresh() {
        let now = LocalDate.today()
        if now != today { today = now }
    }
}

extension GoalRecord {
    /// Objectifs lisibles triés selon l'ordre choisi (`GoalOrder`, cas partagés `sort.json`).
    /// Un enregistrement illisible (goal nil) n'est pas affiché par la nouvelle interface.
    static func sorted(_ records: [GoalRecord], order: GoalOrder, today: LocalDate) -> [GoalRecord] {
        let readable = records.filter { $0.goal != nil }
        return order.sort(readable, today: today) { record in
            GoalSortKey(id: record.exportID, goal: record.goal!, createdAt: record.createdAt)
        }
    }
}

/// Navigation de l'app : accueil en cartes → détail (zoom depuis la carte sur iOS 18), frise de tous les
/// objectifs, formulaire en plein écran, réglages des notifications en feuille. Un lien profond (widget,
/// notification) ouvre le détail de l'objectif — après la fermeture du formulaire s'il est ouvert, pour
/// ne pas perdre la saisie.
struct RootView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @Query(sort: \GoalRecord.createdAt, order: .forward) private var records: [GoalRecord]
    @AppStorage("goal_order") private var orderRaw = GoalOrder.defaultOrder.rawValue
    @StateObject private var clock = TodayClock()

    @State private var path: [Route] = []
    @State private var form: FormRequest?
    @State private var isShowingNotificationSettings = false
    /// Objectif affiché par le pager du détail : cible du zoom retour vers sa carte.
    @State private var focusedID: UUID?
    /// Ouverture demandée pendant que le formulaire est ouvert : appliquée à sa fermeture.
    @State private var pendingOpen: UUID?
    /// Fichier d'objectif(s) reçu et ouvert avec l'app (t13).
    @State private var receivedShare: ReceivedShare?
    /// Entrée décalée des cartes : premier affichage seulement.
    @State private var homeAnimated = false
    @Namespace private var zoom

    private var sorted: [GoalRecord] {
        GoalRecord.sorted(records, order: GoalOrder(storedValue: orderRaw), today: clock.today)
    }

    var body: some View {
        NavigationStack(path: $path) {
            HomeView(
                records: sorted,
                allRecords: records,
                today: clock.today,
                orderRaw: $orderRaw,
                animateEntrance: !homeAnimated,
                zoom: zoom,
                onOpen: { open($0.id) },
                onAdd: { form = FormRequest(record: nil) },
                onOpenTimeline: { path.append(.timeline) },
                onOpenNotifications: { isShowingNotificationSettings = true }
            )
            .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 1) { homeAnimated = true } }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .detail(let id):
                    GoalDetailView(
                        records: sorted,
                        initialID: id,
                        today: clock.today,
                        focusedID: $focusedID,
                        onEdit: { form = FormRequest(record: $0) }
                    )
                    .zoomTransition(sourceID: focusedID ?? id, in: zoom)
                case .timeline:
                    GoalTimelineView(records: sorted, today: clock.today) { record in
                        open(record.id)
                    }
                }
            }
        }
        .fullScreenCover(item: $form, onDismiss: applyPendingOpen) { request in
            GoalFormView(record: request.record, today: clock.today)
        }
        .sheet(isPresented: $isShowingNotificationSettings) {
            NotificationSettingsView()
        }
        .sheet(item: $receivedShare) { share in
            SharedGoalPreviewView(share: share)
        }
        .onOpenURL { url in
            if url.isFileURL {
                // Fichier reçu (t13) : « Ouvrir avec Goal Widgets » → aperçu → Ajouter / Annuler.
                receivedShare = ReceivedShare.read(url)
            } else if let id = GoalDeepLink.goalID(from: url) {
                requestOpen(id)
            }
        }
        // Appui sur une notification de jalon : même ouverture que le widget.
        .onReceive(NotificationRouter.shared.$openedGoalID.compactMap { $0 }) { id in
            requestOpen(id)
            NotificationRouter.shared.openedGoalID = nil
        }
        // Objectif supprimé : son détail disparaît de la pile.
        .onChange(of: records.map(\.id)) { _, ids in
            let existing = Set(ids)
            path.removeAll { route in
                if case .detail(let id) = route { return !existing.contains(id) }
                return false
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { clock.refresh() }
        }
    }

    /// Ouvre le détail depuis l'app (carte, frise).
    private func open(_ id: UUID) {
        focusedID = id
        if let index = path.firstIndex(where: { if case .detail = $0 { return true }; return false }) {
            path = Array(path[..<index]) + [.detail(id)]
        } else {
            path.append(.detail(id))
        }
    }

    /// Ouverture externe (widget, notification) : pile = accueil + détail.
    private func requestOpen(_ id: UUID) {
        // Objectif supprimé entre-temps (notification ancienne) : l'accueil suffit.
        guard records.contains(where: { $0.id == id }) else { return }
        if form != nil {
            pendingOpen = id
            return
        }
        isShowingNotificationSettings = false
        focusedID = id
        path = [.detail(id)]
    }

    private func applyPendingOpen() {
        guard let id = pendingOpen else { return }
        pendingOpen = nil
        if records.contains(where: { $0.id == id }) {
            focusedID = id
            path = [.detail(id)]
        }
    }
}

extension View {
    /// Source du zoom carte → détail (iOS 18+ ; rien sur iOS 17).
    @ViewBuilder
    func zoomSource(id: UUID, in namespace: Namespace.ID) -> some View {
        if #available(iOS 18.0, *) {
            matchedTransitionSource(id: id, in: namespace)
        } else {
            self
        }
    }

    /// Transition zoom de la destination (iOS 18+) ; sur iOS 17 : poussée standard de NavigationStack.
    @ViewBuilder
    func zoomTransition(sourceID: UUID, in namespace: Namespace.ID) -> some View {
        if #available(iOS 18.0, *) {
            navigationTransition(.zoom(sourceID: sourceID, in: namespace))
        } else {
            self
        }
    }
}
