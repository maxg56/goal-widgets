// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import SwiftData
import UniformTypeIdentifiers
import WidgetKit
import GoalCore

/// Accueil « Mes objectifs » : grandes cartes en dégradé de la couleur de l'objectif, chiffre géant,
/// anneau de progression ; appui → détail. En-tête : frise de tous les objectifs, réglages des
/// notifications, menu ⋯ (tri mémorisé, export vers Fichiers ou partage, import).
struct HomeView: View {
    /// Objectifs triés (affichés).
    let records: [GoalRecord]
    /// Tous les enregistrements (export).
    let allRecords: [GoalRecord]
    let today: LocalDate
    @Binding var orderRaw: String
    let animateEntrance: Bool
    let zoom: Namespace.ID
    let onOpen: (GoalRecord) -> Void
    let onAdd: () -> Void
    let onOpenTimeline: () -> Void
    let onOpenNotifications: () -> Void

    @Environment(\.modelContext) private var modelContext
    @State private var isExporting = false
    @State private var exportDocument: GoalExportDocument?
    @State private var exportCount = 0
    @State private var isImporting = false
    @State private var transferMessage: String?

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                header
                if records.isEmpty {
                    emptyState
                } else {
                    ForEach(Array(records.enumerated()), id: \.element.id) { index, record in
                        HeroCard(
                            record: record,
                            today: today,
                            enterIndex: animateEntrance ? index : nil,
                            onTap: { onOpen(record) }
                        )
                        .zoomSource(id: record.id, in: zoom)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 120)
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .overlay(alignment: .bottomTrailing) { addButton }
        .animation(Motion.soft, value: records.map(\.id))
        // Import (app Fichiers) : attaché à un autre niveau que `fileExporter`
        // (deux sélecteurs sur la même vue peuvent s'empêcher l'un l'autre).
        .fileImporter(isPresented: $isImporting, allowedContentTypes: GoalTransfer.importTypes) { result in
            handleImport(result)
        }
        .background {
            Color.clear.fileExporter(
                isPresented: $isExporting,
                document: exportDocument,
                contentType: .json,
                defaultFilename: GoalTransfer.suggestedFileName()
            ) { result in
                handleExport(result)
            }
        }
        .alert(transferMessage ?? "", isPresented: isShowingTransferMessage) {
            Button("action_ok", role: .cancel) {}
        }
    }

    // MARK: - En-tête

    private var header: some View {
        HStack(alignment: .center, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text("goal_list_title")
                    .font(.system(size: 34, weight: .black))
                    .lineLimit(1)
                Text(GoalLabels.plural("home_goal_count", records.count))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Button(action: onOpenTimeline) {
                Image(systemName: "calendar")
                    .font(.system(size: 17, weight: .semibold))
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(Color.accentColor.opacity(0.15)))
            }
            .accessibilityLabel(Text("timeline_open"))
            Button(action: onOpenNotifications) {
                Image(systemName: "bell")
                    .font(.system(size: 17, weight: .semibold))
                    .frame(width: 40, height: 40)
            }
            .accessibilityLabel(Text("notifications_settings_title"))
            moreMenu
        }
        .padding(.top, 12)
        .padding(.bottom, 4)
        .padding(.leading, 8)
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Text("goal_list_empty")
                .font(.title3.weight(.semibold))
            Text("home_empty_hint")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(32)
        .padding(.top, 80)
    }

    private var addButton: some View {
        Button(action: onAdd) {
            Label("goal_add", systemImage: "plus")
                .font(.headline)
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .background(Capsule().fill(Color.accentColor))
                .shadow(color: .black.opacity(0.2), radius: 10, y: 4)
        }
        .buttonStyle(PressableStyle())
        .padding(16)
    }

    private var moreMenu: some View {
        Menu {
            Picker(selection: $orderRaw) {
                Text("goal_sort_closest_date").tag(GoalOrder.closestDate.rawValue)
                Text("goal_sort_created").tag(GoalOrder.created.rawValue)
            } label: {
                Label("goal_sort_title", systemImage: "arrow.up.arrow.down")
            }
            .pickerStyle(.menu)

            Section {
                Button {
                    startExport()
                } label: {
                    Label("action_export", systemImage: "folder")
                }
                ShareLink(
                    item: GoalExportFile(goals: GoalTransfer.exportedGoals(allRecords)),
                    preview: SharePreview(GoalTransfer.suggestedFileName())
                ) {
                    Label("action_share_export", systemImage: "square.and.arrow.up")
                }
                Button {
                    isImporting = true
                } label: {
                    Label("action_import", systemImage: "square.and.arrow.down")
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .font(.system(size: 19, weight: .semibold))
                .frame(width: 40, height: 40)
        }
        .accessibilityLabel(Text("action_more"))
    }

    // MARK: - Export / import (t11)

    private var isShowingTransferMessage: Binding<Bool> {
        Binding(
            get: { transferMessage != nil },
            set: { if !$0 { transferMessage = nil } }
        )
    }

    private func startExport() {
        let goals = GoalTransfer.exportedGoals(allRecords)
        exportCount = goals.count
        exportDocument = GoalExportDocument(data: GoalExport.encode(goals, exportedAt: Date()))
        isExporting = true
    }

    private func handleExport(_ result: Result<URL, Error>) {
        switch result {
        case .success:
            transferMessage = GoalTransfer.Outcome.exported(count: exportCount).message
        case let .failure(error):
            if (error as? CocoaError)?.code == .userCancelled { return }
            transferMessage = GoalTransfer.Outcome.exportFailed.message
        }
        exportDocument = nil
    }

    private func handleImport(_ result: Result<URL, Error>) {
        let outcome: GoalTransfer.Outcome
        switch result {
        case let .success(url):
            outcome = GoalTransfer.importFile(at: url, into: modelContext)
        case let .failure(error):
            if (error as? CocoaError)?.code == .userCancelled { return }
            outcome = .unreadable
        }
        if case let .imported(added, _) = outcome, added > 0 {
            // La sauvegarde déclenche déjà ces deux actions (GoalWidgetsApp, ModelContext.didSave) ;
            // appel explicite pour ne pas dépendre de cette notification.
            WidgetCenter.shared.reloadAllTimelines()
            let container = modelContext.container
            Task { @MainActor in
                await NotificationScheduler.reschedule(container: container)
            }
        }
        transferMessage = outcome.message
    }
}

/// Carte héros : dégradé de la couleur de l'objectif, emoji en filigrane, chiffre géant, légende,
/// anneau qui se remplit en ressort après l'entrée de la carte (compte à rebours).
struct HeroCard: View {
    let record: GoalRecord
    let today: LocalDate
    let enterIndex: Int?
    let onTap: () -> Void

    @State private var ringShown = false

    var body: some View {
        if let goal = record.goal {
            let state = CountdownEngine.compute(goal, today: today)
            let style = record.style
            let emoji = GoalStyle.normalizedEmoji(style.emoji)
            Button(action: onTap) {
                ZStack(alignment: .topTrailing) {
                    GoalTint.background(GoalTint.deepColor(style), diagonal: true)
                    if let emoji {
                        // Emoji géant en filigrane, débordant du coin.
                        Text(emoji)
                            .font(.system(size: 150))
                            .opacity(0.16)
                            .offset(x: 36, y: -40)
                            .accessibilityHidden(true)
                    }
                    content(goal: goal, state: state, emoji: emoji)
                }
                .clipShape(RoundedRectangle(cornerRadius: 32, style: .continuous))
                .contentShape(RoundedRectangle(cornerRadius: 32, style: .continuous))
            }
            .buttonStyle(PressableStyle())
            .staggeredEnter(enterIndex)
            .task {
                try? await Task.sleep(nanoseconds: UInt64(150 + 70 * min(enterIndex ?? 0, 8)) * 1_000_000)
                ringShown = true
            }
        }
    }

    private func content(goal: Goal, state: DisplayState, emoji: String?) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack(spacing: 12) {
                if let emoji {
                    Text(emoji)
                        .font(.system(size: 22))
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(.white.opacity(0.18)))
                }
                Text(record.title)
                    .font(.title2.bold())
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    let number = GoalLabels.bigNumber(state)
                    Text(number)
                        .font(.system(size: bigNumberSize(number, max: 84), weight: .black, design: .rounded))
                        .kerning(-3)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                        .contentTransition(.numericText())
                    Text(GoalLabels.caption(state))
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white.opacity(0.85))
                    if let detail = detail(goal: goal, state: state) {
                        Text(detail)
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.65))
                            .padding(.top, 4)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let progress = state.progress {
                    CardRing(progress: ringShown ? progress : 0, percent: state.percent ?? 0)
                }
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Forme lisible (« 1 an 2 mois »), sinon la date (« le 10 décembre 2026 », « depuis le … »).
    private func detail(goal: Goal, state: DisplayState) -> String? {
        if let breakdown = state.breakdown, let text = GoalLabels.breakdown(breakdown) { return text }
        switch goal.kind {
        case .countdown:
            return goal.targetDate.map { GoalLabels.format("goal_date_countdown", DateFormats.long($0)) }
        case .since:
            return GoalLabels.format("goal_date_since", DateFormats.long(goal.startDate))
        }
    }
}

/// Anneau de la carte : l'arc suit la progression en ressort, le pourcentage compte avec lui.
private struct CardRing: View {
    let progress: Double
    let percent: Int

    var body: some View {
        ZStack {
            RingGauge(fraction: progress, color: .white, track: .white.opacity(0.18), lineWidth: 10)
            CountingPercent(value: progress > 0 ? Double(percent) : 0, target: percent)
                .font(.headline.weight(.bold))
                .foregroundStyle(.white)
        }
        .frame(width: 104, height: 104)
        .animation(.spring(response: 1.2, dampingFraction: 0.6), value: progress)
    }
}

/// Pourcentage qui compte pendant l'animation (valeur interpolée image par image).
struct CountingPercent: View, Animatable {
    var value: Double
    /// Valeur finale : le ressort peut dépasser, le texte non.
    let target: Int

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    var body: some View {
        Text(GoalLabels.percent(min(Int(max(value, 0).rounded(.down)), target)))
            .monospacedDigit()
    }
}
