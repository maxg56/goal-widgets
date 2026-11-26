// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import GoalCore

/// Frise verticale de tous les objectifs (`GoalsTimeline`) : passé en haut, futur en bas, repère
/// « Aujourd'hui », connecteurs remplis selon la progression. Appui sur un objectif → son détail.
struct GoalTimelineView: View {
    let records: [GoalRecord]
    let today: LocalDate
    let onOpen: (GoalRecord) -> Void

    private static let dateColumn: CGFloat = 52
    private static let lineColumn: CGFloat = 36

    private var rows: [TimelineRow<GoalRecord>] {
        GoalsTimeline.arrange(records.filter { $0.goal != nil }, today: today) { $0.goal! }
    }

    var body: some View {
        let rows = self.rows
        ScrollViewReader { scroller in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                        Group {
                            switch row {
                            case .today:
                                TodayMarker(today: today, isFirst: index == 0, isLast: index == rows.count - 1)
                                    .id("today")
                            case let .node(record, date):
                                NodeRow(
                                    record: record,
                                    date: date,
                                    today: today,
                                    index: index,
                                    isFirst: index == 0,
                                    isLast: index == rows.count - 1,
                                    onTap: { onOpen(record) }
                                )
                            }
                        }
                        .staggeredEnter(index, dx: -24, dy: 0)
                    }
                }
                .padding(.leading, 12)
                .padding(.trailing, 16)
                .padding(.top, 8)
                .padding(.bottom, 120)
            }
            // Ouverture centrée sur aujourd'hui, un peu de passé visible au-dessus.
            .onAppear { scroller.scrollTo("today", anchor: UnitPoint(x: 0.5, y: 0.3)) }
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("timeline_title")
        .navigationBarTitleDisplayMode(.inline)
    }

    private struct NodeRow: View {
        let record: GoalRecord
        let date: LocalDate
        let today: LocalDate
        let index: Int
        let isFirst: Bool
        let isLast: Bool
        let onTap: () -> Void

        @State private var started = false

        var body: some View {
            let accent = record.style.color.map(GoalAppearance.color) ?? Color.accentColor
            let state = record.goal.map { CountdownEngine.compute($0, today: today) }
            let past = date < today
            // Connecteur : plein dans le passé, selon la progression dans le futur.
            let fillTarget = past || state?.phase != .upcoming ? 1.0 : (state?.progress ?? 0)
            Button(action: onTap) {
                HStack(alignment: .top, spacing: 0) {
                    VStack(spacing: 0) {
                        Text("\(date.day)")
                            .font(.title2.bold())
                            .foregroundStyle(past ? Color.secondary : Color.primary)
                        Text(DateFormats.monthShort(date) + (date.year != today.year ? " \(date.year % 100)" : ""))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .frame(width: GoalTimelineView.dateColumn)
                    .padding(.top, 18)
                    ZStack(alignment: .top) {
                        GeometryReader { proxy in
                            let top: CGFloat = isFirst ? 30 : 0
                            let bottom: CGFloat = isLast ? 30 : proxy.size.height
                            let x = proxy.size.width / 2
                            Capsule()
                                .fill(Color(.separator))
                                .frame(width: 4, height: max(bottom - top, 0))
                                .position(x: x, y: (top + bottom) / 2)
                            Capsule()
                                .fill(accent)
                                .frame(width: 4, height: max(bottom - top, 0) * (started ? fillTarget : 0))
                                .position(x: x, y: top + max(bottom - top, 0) * (started ? fillTarget : 0) / 2)
                        }
                        ZStack {
                            Circle().fill(past ? accent : Color(.tertiarySystemFill))
                            Circle().strokeBorder(accent, lineWidth: 2)
                            if let emoji = GoalStyle.normalizedEmoji(record.style.emoji) {
                                Text(emoji).font(.system(size: 15))
                            } else {
                                Circle()
                                    .fill(past ? Color(.systemBackground) : accent)
                                    .frame(width: 8, height: 8)
                            }
                        }
                        .frame(width: 32, height: 32)
                        .padding(.top, 14)
                    }
                    .frame(width: GoalTimelineView.lineColumn)
                    .frame(maxHeight: .infinity, alignment: .top)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(record.title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        if let state {
                            HStack(alignment: .lastTextBaseline, spacing: 6) {
                                Text(GoalLabels.bigNumber(state))
                                    .font(.system(size: 26, weight: .black, design: .rounded))
                                    .foregroundStyle(accent)
                                Text(GoalLabels.caption(state))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                                Spacer(minLength: 0)
                                if let percent = state.percent {
                                    Text(GoalLabels.percent(percent))
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(accent)
                                }
                            }
                            if let breakdown = state.breakdown, let text = GoalLabels.breakdown(breakdown) {
                                Text(text)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color(.secondarySystemGroupedBackground)))
                    .padding(.leading, 6)
                    .padding(.vertical, 8)
                }
                // Hauteur de la rangée = celle de la carte ; la colonne du trait s'étire dessus.
                .fixedSize(horizontal: false, vertical: true)
                .contentShape(Rectangle())
            }
            .buttonStyle(PressableStyle(scale: 0.98))
            .task {
                try? await Task.sleep(nanoseconds: UInt64(200 + 80 * min(index, 8)) * 1_000_000)
                withAnimation(.spring(response: 1.2, dampingFraction: 0.8)) { started = true }
            }
        }
    }

    private struct TodayMarker: View {
        let today: LocalDate
        let isFirst: Bool
        let isLast: Bool

        @State private var pulse = false

        var body: some View {
            HStack(spacing: 0) {
                Color.clear.frame(width: GoalTimelineView.dateColumn)
                ZStack {
                    GeometryReader { proxy in
                        let c = proxy.size.height / 2
                        let top: CGFloat = isFirst ? c : 0
                        let bottom: CGFloat = isLast ? c : proxy.size.height
                        Rectangle()
                            .fill(Color(.separator))
                            .frame(width: 4, height: max(bottom - top, 0))
                            .position(x: proxy.size.width / 2, y: (top + bottom) / 2)
                    }
                    Circle()
                        .fill(Color.accentColor.opacity(0.25))
                        .frame(width: 28, height: 28)
                        .scaleEffect(pulse ? 1 : 0.6)
                    Circle()
                        .fill(Color.accentColor)
                        .frame(width: 14, height: 14)
                }
                .frame(width: GoalTimelineView.lineColumn)
                Text(GoalLabels.format("timeline_today", DateFormats.weekdayDayMonth(today)))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.accentColor)
                    .lineLimit(1)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(Capsule().fill(Color.accentColor.opacity(0.15)))
                    .padding(.leading, 6)
                Rectangle()
                    .fill(Color.accentColor.opacity(0.35))
                    .frame(height: 2)
                    .padding(.leading, 8)
            }
            .frame(height: 56)
            .onAppear {
                withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) { pulse = true }
            }
        }
    }
}
