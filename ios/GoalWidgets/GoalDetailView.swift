// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import GoalCore

/// Détail d'un objectif : une page par objectif (pager horizontal vers ses voisins, dans l'ordre de
/// l'accueil), chiffre géant, graphique au choix (points, anneau, arcs, barres), frise de l'objectif en
/// bas. Crayon → personnalisation (× pour masquer, rangée « Masqués » pour remettre), mémorisée pour
/// tous les objectifs (`detail_layout` / `detail_chart`, même texte qu'Android). « Modifier » ouvre le
/// formulaire, l'icône de partage envoie l'objectif enregistré.
struct GoalDetailView: View {
    let records: [GoalRecord]
    let initialID: UUID
    let today: LocalDate
    @Binding var focusedID: UUID?
    let onEdit: (GoalRecord) -> Void

    @Environment(\.dismiss) private var dismiss
    @AppStorage("detail_layout") private var layoutRaw = ""
    @AppStorage("detail_chart") private var chartRaw = ""
    @State private var shownID: UUID?
    @State private var editing = false
    /// Compteur « série » dont la remise à zéro attend confirmation.
    @State private var resetting: GoalRecord?

    init(records: [GoalRecord], initialID: UUID, today: LocalDate, focusedID: Binding<UUID?>, onEdit: @escaping (GoalRecord) -> Void) {
        self.records = records
        self.initialID = initialID
        self.today = today
        _focusedID = focusedID
        self.onEdit = onEdit
        _shownID = State(initialValue: initialID)
    }

    private var layout: DetailLayout { DetailLayout.decode(layoutRaw) }
    private var chart: ChartKind { layout.chartToShow(preferred: ChartKind(rawValue: chartRaw)) }

    /// Objectif affiché ; s'il a disparu (supprimé), le premier.
    private var current: GoalRecord? {
        records.first { $0.id == shownID } ?? records.first
    }

    private var currentIndex: Int {
        records.firstIndex { $0.id == current?.id } ?? 0
    }

    var body: some View {
        ZStack {
            GoalBackdrop(base: GoalTint.deepColor(current?.style ?? .none))
                .ignoresSafeArea()
                .animation(Motion.slow, value: current?.id)
            VStack(spacing: 0) {
                if editing {
                    Text("detail_customize_hint")
                        .font(.system(size: 13))
                        .foregroundStyle(.white.opacity(0.8))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 4)
                        .transition(.move(edge: .top).combined(with: .opacity))
                    HiddenRow(items: hiddenItems)
                        .padding(.horizontal, 20)
                } else if records.count > 1 {
                    GoalPills(records: records, today: today, selectedID: current?.id) { id in
                        withAnimation(Motion.slow) { shownID = id }
                    }
                    .transition(.opacity)
                }
                pager
            }
            .animation(.spring(response: 0.45, dampingFraction: 0.8), value: editing)
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .toolbar { toolbar }
        .tint(.white)
        .onChange(of: shownID) { _, id in
            if let id { focusedID = id }
        }
        .onChange(of: records.isEmpty) { _, empty in
            if empty { dismiss() }
        }
        .sheet(item: $resetting) { record in
            ResetStreakSheet(record: record, today: today)
        }
    }

    // MARK: - Barre du haut

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            Text(GoalLabels.format("detail_position", "\(currentIndex + 1)", "\(records.count)"))
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white.opacity(0.75))
                .monospacedDigit()
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            if editing {
                Button {
                    editing = false
                } label: {
                    Label("detail_done", systemImage: "checkmark")
                        .labelStyle(.titleAndIcon)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.black.opacity(0.85))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Capsule().fill(.white))
                }
                .transition(.scale(scale: 0.8).combined(with: .opacity))
            } else {
                if let current, let shared = GoalTransfer.exportedGoal(current) {
                    ShareLink(
                        item: SharedGoalFile(goal: shared),
                        preview: SharePreview(GoalShare.fileName(title: shared.title))
                    ) {
                        RoundIcon(systemName: "square.and.arrow.up")
                    }
                    .accessibilityLabel(Text("action_share"))
                }
                Button {
                    editing = true
                } label: {
                    RoundIcon(systemName: "pencil")
                }
                .accessibilityLabel(Text("detail_customize"))
                Button {
                    if let current { onEdit(current) }
                } label: {
                    Text("action_edit")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Capsule().fill(.white.opacity(0.2)))
                }
            }
        }
    }

    // MARK: - Pager

    /// Pager iOS 17 : défilement horizontal par pages (`scrollTargetBehavior(.paging)`), position liée à
    /// `shownID` ; les pages voisines s'estompent et rétrécissent (`scrollTransition`).
    private var pager: some View {
        ScrollViewReader { scroller in
            pagerScroll
                // Filet : la position initiale de `scrollPosition` n'est pas toujours appliquée
                // à une pile paresseuse au premier affichage.
                .onAppear { scroller.scrollTo(initialID) }
        }
    }

    private var pagerScroll: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 0) {
                ForEach(records) { record in
                    DetailPage(
                        record: record,
                        today: today,
                        isActive: record.id == current?.id,
                        chart: chart,
                        layout: layout,
                        editing: editing,
                        onLayoutChange: { layoutRaw = $0.encode() },
                        onSelectChart: { kind in
                            withAnimation(.spring(response: 0.5, dampingFraction: 0.7)) { chartRaw = kind.rawValue }
                        },
                        onReset: { resetting = record }
                    )
                    .containerRelativeFrame(.horizontal)
                    .scrollTransition(axis: .horizontal) { content, phase in
                        content
                            .opacity(1 - abs(phase.value) * 0.6)
                            .scaleEffect(1 - abs(phase.value) * 0.12)
                    }
                    .id(record.id)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $shownID)
        .scrollDisabled(editing)
    }

    /// Éléments masqués, dans l'ordre d'affichage, avec l'action qui les remet.
    private var hiddenItems: [(label: String, restore: () -> Void)] {
        let layout = self.layout
        let parts: [(label: String, restore: () -> Void)] = DetailPart.allCases.filter { !layout.shows($0) }.map { part in
            (label: Self.partLabel(part), restore: { layoutRaw = layout.restoring(part).encode() })
        }
        let charts: [(label: String, restore: () -> Void)] = ChartKind.allCases.filter { !layout.charts.contains($0) }.map { kind in
            (label: GoalLabels.format("detail_hidden_chart", GoalLabels.chartName(kind)),
             restore: { layoutRaw = layout.restoring(kind).encode() })
        }
        return parts + charts
    }

    static func partLabel(_ part: DetailPart) -> String {
        switch part {
        case .number: return String(localized: "detail_part_number")
        case .caption: return String(localized: "detail_part_caption")
        case .chart: return String(localized: "detail_part_chart")
        case .timeline: return String(localized: "detail_part_timeline")
        }
    }
}

/// Icône ronde translucide de la barre du détail.
private struct RoundIcon: View {
    let systemName: String

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 36, height: 36)
            .background(Circle().fill(.white.opacity(0.2)))
    }
}

/// Une page du détail.
private struct DetailPage: View {
    let record: GoalRecord
    let today: LocalDate
    let isActive: Bool
    let chart: ChartKind
    let layout: DetailLayout
    let editing: Bool
    let onLayoutChange: (DetailLayout) -> Void
    let onSelectChart: (ChartKind) -> Void
    let onReset: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        if let goal = record.goal {
            let state = CountdownEngine.compute(goal, today: today)
            let accent = GoalTint.accent(record.style)
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 0) {
                    if let emoji = GoalStyle.normalizedEmoji(record.style.emoji) {
                        Text(emoji).font(.system(size: 40))
                    }
                    Text(record.title)
                        .font(.system(size: 24, weight: .bold))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                    if layout.shows(.number) {
                        numberBlock(state)
                    }
                    if layout.shows(.caption) {
                        captionBlock(state)
                    }
                    if record.isStreak && !editing {
                        StreakBar(record: record, today: today, onReset: onReset)
                            .padding(.top, 14)
                            .transition(.opacity)
                    }
                    if layout.shows(.chart) {
                        chartBlock(goal: goal, accent: accent)
                            .padding(.top, 22)
                    }
                    if layout.shows(.timeline) {
                        timelineBlock(goal: goal, accent: accent)
                            .padding(.top, 16)
                    }
                    if record.isStreak {
                        StreakHistoryCard(record: record, card: card)
                            .padding(.top, 16)
                    }
                    Spacer(minLength: 32)
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .animation(.spring(response: 0.45, dampingFraction: 0.8), value: layout)
            }
        }
    }

    private var card: Color {
        (colorScheme == .dark ? Color(white: 0.11) : Color.white).opacity(0.96)
    }

    private func numberBlock(_ state: DisplayState) -> some View {
        let number = GoalLabels.bigNumber(state)
        return EditableBlock(editing: editing, seed: 0, label: GoalDetailView.partLabel(.number), badgeOffset: 0, onRemove: {
            onLayoutChange(layout.hiding(.number))
        }) {
            Text(number)
                .font(.system(size: bigNumberSize(number, max: 132), weight: .black, design: .rounded))
                .kerning(-5)
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.4)
                .contentTransition(.numericText())
                .padding(.horizontal, 12)
                .padding(.top, 2)
        }
        .transition(.scale.combined(with: .opacity))
    }

    private func captionBlock(_ state: DisplayState) -> some View {
        let detail = [
            state.breakdown.flatMap(GoalLabels.breakdown),
            state.percent.map { GoalLabels.format("detail_percent_of_path", GoalLabels.percent($0)) },
        ].compactMap { $0 }.joined(separator: " · ")
        return EditableBlock(editing: editing, seed: 1, label: GoalDetailView.partLabel(.caption), onRemove: {
            onLayoutChange(layout.hiding(.caption))
        }) {
            VStack(spacing: 6) {
                Text(GoalLabels.caption(state))
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(.white.opacity(0.85))
                if !detail.isEmpty {
                    Text(detail)
                        .font(.system(size: 13))
                        .foregroundStyle(.white.opacity(0.6))
                        .multilineTextAlignment(.center)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 4)
        }
        .transition(.scale.combined(with: .opacity))
    }

    private func chartBlock(goal: Goal, accent: Color) -> some View {
        EditableBlock(editing: editing, seed: 2, label: GoalDetailView.partLabel(.chart), onRemove: {
            onLayoutChange(layout.hiding(.chart))
        }) {
            VStack(spacing: 12) {
                // Un seul graphique restant : sélecteur inutile (gardé en personnalisation).
                if editing || layout.charts.count > 1 {
                    ChartSelector(
                        selected: chart,
                        kinds: layout.charts,
                        editing: editing,
                        onSelect: onSelectChart,
                        onRemove: { onLayoutChange(layout.hiding($0)) }
                    )
                }
                ZStack {
                    chartView(goal: goal, accent: accent)
                        .id(chart)
                        .transition(.asymmetric(
                            insertion: .scale(scale: 0.86).combined(with: .opacity),
                            removal: .scale(scale: 1.06).combined(with: .opacity)
                        ))
                }
                .frame(maxWidth: .infinity)
                .frame(height: 320)
                .padding(16)
                .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(card))
                .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            }
        }
        .transition(.scale.combined(with: .opacity))
    }

    @ViewBuilder
    private func chartView(goal: Goal, accent: Color) -> some View {
        switch chart {
        case .dots:
            DotsChartView(grid: DotGrid.compute(goal, today: today, period: .whole), accent: accent, active: isActive)
        case .ring:
            RingChartView(ring: RingChart.compute(goal, today: today), accent: accent, active: isActive)
        case .arcs:
            ArcsChartView(arcs: ArcsChart.compute(goal, today: today), accent: accent, active: isActive)
        case .bars:
            BarsChartView(chart: BarsChart.compute(goal, today: today), accent: accent, active: isActive)
        }
    }

    private func timelineBlock(goal: Goal, accent: Color) -> some View {
        EditableBlock(editing: editing, seed: 3, label: GoalDetailView.partLabel(.timeline), onRemove: {
            onLayoutChange(layout.hiding(.timeline))
        }) {
            VStack(alignment: .leading, spacing: 0) {
                Text(goal.kind == .countdown ? LocalizedStringKey("strip_title_countdown") : LocalizedStringKey("strip_title_since"))
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 18)
                GoalStripView(strip: GoalStrip.compute(goal, today: today), accent: accent, active: isActive)
            }
            .padding(.top, 14)
            .padding(.bottom, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(card))
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        }
        .transition(.scale.combined(with: .opacity))
    }
}

/// Pastilles des objectifs (accès direct à une page du pager), suivent la page affichée.
private struct GoalPills: View {
    let records: [GoalRecord]
    let today: LocalDate
    let selectedID: UUID?
    let onSelect: (UUID) -> Void

    var body: some View {
        ScrollViewReader { scroller in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(records) { record in
                        pill(record)
                            .id(record.id)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
            }
            .onChange(of: selectedID) { _, id in
                guard let id else { return }
                withAnimation(Motion.soft) { scroller.scrollTo(id, anchor: .center) }
            }
            .onAppear { if let selectedID { scroller.scrollTo(selectedID, anchor: .center) } }
        }
    }

    private func pill(_ record: GoalRecord) -> some View {
        let isSelected = record.id == selectedID
        let foreground = isSelected ? GoalTint.deepColor(record.style) : Color.white
        let number = record.goal.map { GoalLabels.bigNumber(CountdownEngine.compute($0, today: today)) } ?? ""
        return Button {
            onSelect(record.id)
        } label: {
            HStack(spacing: 6) {
                if let emoji = GoalStyle.normalizedEmoji(record.style.emoji) {
                    Text(emoji).font(.system(size: 13))
                }
                Text(record.title)
                    .font(.system(size: 12, weight: .semibold))
                    .lineLimit(1)
                    .frame(maxWidth: 120, alignment: .leading)
                Text(number)
                    .font(.system(size: 12, weight: .black))
                    .opacity(0.7)
            }
            .foregroundStyle(foreground)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Capsule().fill(isSelected ? Color.white : Color.white.opacity(0.14)))
            .animation(Motion.soft, value: isSelected)
        }
        .buttonStyle(.plain)
    }
}
