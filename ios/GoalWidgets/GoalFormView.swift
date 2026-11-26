// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import SwiftData
import UIKit
import GoalCore

/// Formulaire de création (`record` = nil) ou de modification, en plein écran : héros dans la couleur
/// choisie avec titre éditable et aperçu en direct, type en bascule, dates en cartes + feuille calendrier
/// (suggestions, durée), couleur, emoji, notifications, bouton collant. En modification : « Partager »,
/// « Continuer en compteur depuis » (cible dépassée) et « Supprimer », confirmés.
/// Les erreurs n'apparaissent qu'après une tentative d'enregistrement (secousse du bouton).
struct GoalFormView: View {
    let record: GoalRecord?
    let today: LocalDate

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    @State private var kind: GoalKind
    @State private var title: String
    @State private var startDate: LocalDate
    /// nil tant qu'aucune cible n'est choisie (création d'un compte à rebours, comme Android).
    @State private var targetDate: LocalDate?
    @State private var color: GoalColor?
    @State private var emoji: String
    /// Désactivées par défaut, y compris pour un nouvel objectif (spec 44 : autorisation demandée à l'activation).
    @State private var notify: Bool
    /// Option « série » (remise à zéro) : proposée et appliquée seulement à un compteur « depuis ».
    @State private var streak: Bool
    @State private var notificationsDenied = false
    @State private var attempted = false
    @State private var sheet: DateField?
    @State private var confirm: Confirm?
    @State private var shake: CGFloat = 0

    enum DateField: String, Identifiable {
        case start
        case target
        var id: String { rawValue }
    }

    private enum Confirm: Int, Identifiable {
        case delete
        case continueAsSince
        var id: Int { rawValue }
    }

    /// Erreurs du brouillon : règles de `CountdownValidation` / `SinceValidation`, plus la cible absente
    /// (`goal_form_error_target_missing`, comme `DraftError.TARGET_MISSING` d'Android).
    enum FormError: Hashable {
        case titleBlank
        case targetMissing
        case targetNotAfterStart
        case startInFuture
    }

    init(record: GoalRecord?, today: LocalDate) {
        self.record = record
        self.today = today
        let goal = record?.goal
        _kind = State(initialValue: goal?.kind ?? .countdown)
        _title = State(initialValue: record?.title ?? "")
        _startDate = State(initialValue: goal?.startDate ?? today)
        _targetDate = State(initialValue: goal?.targetDate)
        _color = State(initialValue: record?.style.color)
        _emoji = State(initialValue: record?.style.emoji ?? "")
        _notify = State(initialValue: record?.notificationsEnabled ?? false)
        _streak = State(initialValue: record?.streak ?? false)
    }

    private var style: GoalStyle { GoalStyle(color: color, emoji: emoji) }
    private var base: Color { GoalTint.deepColor(style) }

    private var draftErrors: Set<FormError> {
        var errors: Set<FormError> = []
        if title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { errors.insert(.titleBlank) }
        switch kind {
        case .countdown:
            if let target = targetDate {
                if CountdownValidation.check(title: "-", startDate: startDate, targetDate: target) == .targetNotAfterStart {
                    errors.insert(.targetNotAfterStart)
                }
            } else {
                errors.insert(.targetMissing)
            }
        case .since:
            if SinceValidation.check(title: "-", startDate: startDate, today: today) == .startInFuture {
                errors.insert(.startInFuture)
            }
        }
        return errors
    }

    private var errors: Set<FormError> { attempted ? draftErrors : [] }

    /// Objectif de l'aperçu : seulement quand les dates sont cohérentes (le titre peut être vide).
    private var previewGoal: Goal? {
        switch kind {
        case .countdown:
            guard let target = targetDate, target > startDate else { return nil }
            return Goal(kind: .countdown, startDate: startDate, targetDate: target)
        case .since:
            return Goal(kind: .since, startDate: startDate)
        }
    }

    /// Compte à rebours enregistré dont la cible est dépassée : la conversion est proposée.
    private var canContinueAsSince: Bool {
        guard let goal = record?.goal, goal.kind == .countdown else { return false }
        return CountdownEngine.compute(goal, today: today).phase == .reached
    }

    var body: some View {
        ScrollViewReader { scroller in
            ScrollView {
                VStack(spacing: 26) {
                    hero(scroller: scroller)
                    Group {
                        KindToggle(kind: $kind, base: base)
                        DatesCard(
                            kind: kind,
                            start: startDate,
                            target: targetDate,
                            today: today,
                            base: base,
                            errors: errors,
                            onPick: { sheet = $0 }
                        )
                        FormSection(title: "goal_form_color") {
                            ColorSwatches(selection: $color)
                        }
                        FormSection(title: "goal_form_emoji") {
                            EmojiChoices(selection: $emoji, base: base)
                        }
                        .id("emoji")
                        if kind == .since {
                            StreakCard(enabled: $streak, base: base)
                                .transition(.opacity.combined(with: .move(edge: .top)))
                        }
                        NotificationCard(enabled: $notify, denied: notificationsDenied, base: base)
                        if record != nil {
                            ManageCard(
                                canContinueAsSince: canContinueAsSince,
                                onContinue: { confirm = .continueAsSince },
                                onDelete: { confirm = .delete }
                            )
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        // Bouton collant, au-dessus du clavier.
        .safeAreaInset(edge: .bottom) { saveBar }
        .animation(.spring(response: 0.5, dampingFraction: 0.85), value: color)
        .animation(Motion.soft, value: kind)
        .sheet(item: $sheet) { field in
            DateSheet(
                field: field,
                kind: kind,
                current: field == .target ? targetDate : startDate,
                other: field == .target ? startDate : targetDate,
                today: today,
                base: base
            ) { picked in
                if field == .target { targetDate = picked } else { startDate = picked }
                sheet = nil
            }
        }
        .alert(item: $confirm) { what in
            switch what {
            case .delete:
                return Alert(
                    title: Text("goal_delete_confirm_title"),
                    message: Text(GoalLabels.format("form_delete_confirm_message", record?.title ?? "")),
                    primaryButton: .destructive(Text("action_delete")) { delete() },
                    secondaryButton: .cancel(Text("action_cancel"))
                )
            case .continueAsSince:
                return Alert(
                    title: Text("form_continue_confirm_title"),
                    message: Text("form_continue_confirm_message"),
                    primaryButton: .default(Text("action_continue")) { continueAsSince() },
                    secondaryButton: .cancel(Text("action_cancel"))
                )
            }
        }
        .onChange(of: notify) { _, enabled in
            // Spec 44 : l'autorisation est demandée au moment où l'utilisateur active une notification.
            guard enabled else { return }
            Task {
                let granted = await NotificationPermission.requestIfNeeded()
                notificationsDenied = !granted
            }
        }
        .task {
            if notify {
                notificationsDenied = await NotificationPermission.isDenied()
            }
        }
    }

    // MARK: - Héros

    private func hero(scroller: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 17, weight: .semibold))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(Text("action_cancel"))
                Text(record == nil ? LocalizedStringKey("goal_form_new") : LocalizedStringKey("goal_form_edit"))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.85))
                Spacer()
                // Partager (t13) : l'objectif tel qu'enregistré (les modifications non enregistrées ne partent pas).
                if let record, let shared = GoalTransfer.exportedGoal(record) {
                    ShareLink(
                        item: SharedGoalFile(goal: shared),
                        preview: SharePreview(GoalShare.fileName(title: shared.title))
                    ) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 17, weight: .semibold))
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(Text("action_share"))
                }
            }
            .foregroundStyle(.white)
            HStack(alignment: .center, spacing: 14) {
                EmojiBubble(emoji: GoalStyle.normalizedEmoji(emoji)) {
                    withAnimation(Motion.soft) { scroller.scrollTo("emoji", anchor: .center) }
                }
                TextField(
                    "goal_form_title",
                    text: $title,
                    prompt: Text(kind == .countdown
                        ? LocalizedStringKey("form_title_placeholder_countdown")
                        : LocalizedStringKey("form_title_placeholder_since"))
                        .foregroundStyle(.white.opacity(0.45)),
                    axis: .vertical
                )
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(.white)
                .tint(.white)
                .lineLimit(1...3)
                .textInputAutocapitalization(.sentences)
            }
            .padding(.horizontal, 12)
            .padding(.top, 8)
            if errors.contains(.titleBlank) {
                Text("goal_form_error_empty_title")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(base)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 5)
                    .background(Capsule().fill(.white))
                    .padding(.leading, 86)
                    .padding(.top, 8)
                    .transition(.scale(scale: 0.8, anchor: .leading).combined(with: .opacity))
            }
            LivePreview(goal: previewGoal, today: today, kind: kind)
                .padding(.horizontal, 12)
                .padding(.top, 22)
        }
        .padding(.horizontal, 8)
        .padding(.bottom, 28)
        .background {
            // Le fond déborde sous la barre d'état ; le contenu reste dans la zone sûre.
            ZStack {
                base
                GoalTint.sheen()
                RadialGradient(colors: [.white.opacity(0.22), .clear], center: UnitPoint(x: 0.85, y: 0.1), startRadius: 0, endRadius: 300)
            }
            .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 40, bottomTrailingRadius: 40, style: .continuous))
            .ignoresSafeArea(edges: .top)
        }
        .animation(Motion.bouncy, value: errors)
    }

    // MARK: - Bouton collant

    private var saveBar: some View {
        Button(action: save) {
            HStack(spacing: 10) {
                Image(systemName: "checkmark")
                Text(record == nil ? LocalizedStringKey("form_create") : LocalizedStringKey("action_save"))
            }
            .font(.system(size: 17, weight: .bold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 62)
            .background {
                ZStack {
                    base
                    LinearGradient(colors: [.white.opacity(0.15), .clear, .black.opacity(0.2)], startPoint: .leading, endPoint: .trailing)
                }
                .clipShape(Capsule())
            }
        }
        .buttonStyle(PressableStyle())
        .offset(x: shake)
        .padding(.horizontal, 20)
        .padding(.top, 22)
        .padding(.bottom, 8)
        .background(
            LinearGradient(
                colors: [Color(.systemGroupedBackground).opacity(0), Color(.systemGroupedBackground).opacity(0.92), Color(.systemGroupedBackground)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea(edges: .bottom)
        )
    }

    // MARK: - Actions

    private func save() {
        attempted = true
        guard draftErrors.isEmpty else {
            Task { @MainActor in
                for x: CGFloat in [22, -18, 12, -8, 4, 0] {
                    withAnimation(.linear(duration: 0.045)) { shake = x }
                    try? await Task.sleep(nanoseconds: 45_000_000)
                }
            }
            return
        }
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let goal: Goal
        switch kind {
        case .countdown:
            goal = Goal(kind: .countdown, startDate: startDate, targetDate: targetDate)
        case .since:
            goal = Goal(kind: .since, startDate: startDate, targetDate: nil)
        }
        if let record {
            record.update(title: cleanTitle, goal: goal)
            record.notificationsEnabled = notify
            record.style = style
            record.streak = streak
        } else {
            let created = GoalRecord(
                title: cleanTitle,
                kind: goal.kind,
                startDate: goal.startDate,
                targetDate: goal.targetDate,
                notificationsEnabled: notify,
                style: style
            )
            created.streak = goal.kind == .since && streak
            modelContext.insert(created)
        }
        // La sauvegarde recharge les widgets et reprogramme les notifications (GoalWidgetsApp, ModelContext.didSave).
        try? modelContext.save()
        dismiss()
    }

    /// « Continuer en compteur depuis » (même enregistrement : les widgets liés suivent) ; titre, couleur,
    /// emoji et notifications gardés.
    private func continueAsSince() {
        guard let record, record.continueAsSince() else { return }
        try? modelContext.save()
        dismiss()
    }

    private func delete() {
        guard let record else { return }
        modelContext.delete(record)
        try? modelContext.save()
        dismiss()
    }
}

// MARK: - Aperçu en direct

/// Grand nombre qui défile au changement + mini anneau en ressort.
private struct LivePreview: View {
    let goal: Goal?
    let today: LocalDate
    let kind: GoalKind

    var body: some View {
        let state = goal.map { CountdownEngine.compute($0, today: today) }
        let ring = goal.map { RingChart.compute($0, today: today) }
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                let number = state.map(GoalLabels.bigNumber) ?? "—"
                Text(number)
                    .font(.system(size: bigNumberSize(number, max: 72), weight: .black, design: .rounded))
                    .kerning(-2)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .contentTransition(.numericText())
                Text(caption(state))
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(.white.opacity(0.85))
                if let text = state?.breakdown.flatMap(GoalLabels.breakdown) {
                    Text(text)
                        .font(.system(size: 12))
                        .foregroundStyle(.white.opacity(0.6))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            ZStack {
                RingGauge(fraction: ring?.fraction ?? 0, color: .white, track: .white.opacity(0.2), lineWidth: 10)
                if let ring {
                    let texts = GoalLabels.ringTexts(ring)
                    VStack(spacing: 0) {
                        Text(texts.center)
                            .font(.system(size: 15, weight: .black))
                        Text(texts.caption)
                            .font(.system(size: 9))
                            .opacity(0.7)
                            .lineLimit(1)
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .minimumScaleFactor(0.6)
                }
            }
            .frame(width: 88, height: 88)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(.white.opacity(0.12)))
        .animation(.spring(response: 0.55, dampingFraction: 0.7), value: state)
        .animation(.spring(response: 0.8, dampingFraction: 0.55), value: ring?.fraction)
    }

    private func caption(_ state: DisplayState?) -> String {
        if let state { return GoalLabels.caption(state) }
        return kind == .countdown ? String(localized: "goal_form_error_target_missing") : ""
    }
}

/// Bulle d'emoji du héros : rebondit quand il change ; appui → section Emoji.
private struct EmojiBubble: View {
    let emoji: String?
    let action: () -> Void
    @State private var bounce: CGFloat = 1

    var body: some View {
        Button(action: action) {
            Group {
                if let emoji {
                    Text(emoji).font(.system(size: 30))
                } else {
                    Text(verbatim: "+").font(.system(size: 28, weight: .light)).foregroundStyle(.white)
                }
            }
            .frame(width: 60, height: 60)
            .background(Circle().fill(.white.opacity(0.18)))
            .scaleEffect(bounce)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("form_choose_emoji"))
        .onChange(of: emoji) { _, _ in
            bounce = 0.6
            withAnimation(.spring(response: 0.35, dampingFraction: 0.4)) { bounce = 1 }
        }
    }
}

// MARK: - Type

private struct KindToggle: View {
    @Binding var kind: GoalKind
    let base: Color
    @Namespace private var indicator

    var body: some View {
        HStack(spacing: 0) {
            option(.countdown, "goal_kind_countdown")
            option(.since, "goal_kind_since")
        }
        .padding(5)
        .frame(height: 56)
        .background(Capsule().fill(Color(.tertiarySystemFill)))
    }

    private func option(_ value: GoalKind, _ label: LocalizedStringKey) -> some View {
        let selected = value == kind
        return Text(label)
            .font(.system(size: 15, weight: selected ? .bold : .medium))
            .foregroundStyle(selected ? Color.white : Color.secondary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background {
                if selected {
                    Capsule()
                        .fill(LinearGradient(colors: [base.opacity(0.85), base], startPoint: .leading, endPoint: .trailing))
                        .matchedGeometryEffect(id: "kind", in: indicator)
                }
            }
            .contentShape(Capsule())
            .onTapGesture {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.68)) { kind = value }
            }
            .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

// MARK: - Dates

private struct DatesCard: View {
    let kind: GoalKind
    let start: LocalDate
    let target: LocalDate?
    let today: LocalDate
    let base: Color
    let errors: Set<GoalFormView.FormError>
    let onPick: (GoalFormView.DateField) -> Void

    @Environment(\.colorScheme) private var colorScheme

    /// Teinte lisible sur la carte : éclaircie en mode sombre.
    private var accent: Color { colorScheme == .dark ? base.mixedWithWhite(0.45) : base }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Group {
                switch kind {
                case .countdown:
                    DateChip(label: "form_date_start", date: start, accent: accent, error: false) { onPick(.start) }
                    let days = target.map { start.days(until: $0) }
                    Connector(accent: accent, text: connectorText(days: days), error: (days ?? 1) <= 0)
                    DateChip(
                        label: "form_date_target",
                        date: target,
                        accent: accent,
                        error: errors.contains(.targetMissing) || errors.contains(.targetNotAfterStart)
                    ) { onPick(.target) }
                case .since:
                    DateChip(label: "goal_form_since_date", date: start, accent: accent, error: errors.contains(.startInFuture)) { onPick(.start) }
                    let days = start.days(until: today)
                    Connector(
                        accent: accent,
                        text: days < 0
                            ? String(localized: "goal_form_error_start_in_future")
                            : GoalLabels.format("form_duration_until_today", Self.duration(days, Breakdown.between(start, today))),
                        error: days < 0
                    )
                }
            }
            .transition(.opacity.combined(with: .offset(y: 8)))
            if let message {
                Text(LocalizedStringKey(message))
                    .font(.system(size: 13))
                    .foregroundStyle(.red)
                    .padding(.top, 10)
                    .padding(.leading, 6)
                    .transition(.opacity)
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 30, style: .continuous).fill(Color(.secondarySystemGroupedBackground)))
    }

    private var message: String? {
        if errors.contains(.targetMissing) { return "goal_form_error_target_missing" }
        if errors.contains(.targetNotAfterStart) { return "goal_form_error_target_not_after_start" }
        if errors.contains(.startInFuture) { return "goal_form_error_start_in_future" }
        return nil
    }

    private func connectorText(days: Int?) -> String {
        guard let days, let target else { return String(localized: "goal_form_error_target_missing") }
        if days <= 0 { return String(localized: "goal_form_error_target_not_after_start") }
        return Self.duration(days, Breakdown.between(start, target))
    }

    /// « → 45 jours · 1 mois 14 jours ».
    static func duration(_ days: Int, _ breakdown: Breakdown) -> String {
        let count = GoalLabels.plural("form_duration", days)
        return GoalLabels.breakdown(breakdown).map { GoalLabels.joined(count, $0) } ?? count
    }
}

private extension Color {
    /// Mélange avec du blanc (`amount` 0–1), résolu pour le thème courant.
    func mixedWithWhite(_ amount: CGFloat) -> Color {
        let source = UIColor(self)
        return Color(UIColor { traits in
            var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
            source.resolvedColor(with: traits).getRed(&r, green: &g, blue: &b, alpha: &a)
            return UIColor(red: r + (1 - r) * amount, green: g + (1 - g) * amount, blue: b + (1 - b) * amount, alpha: a)
        })
    }
}

private struct DateChip: View {
    let label: LocalizedStringKey
    let date: LocalDate?
    let accent: Color
    let error: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: "calendar")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(accent)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(accent.opacity(0.16)))
                VStack(alignment: .leading, spacing: 2) {
                    Text(label)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(.secondary)
                    Text(date.map(DateFormats.weekdayLong) ?? String(localized: "goal_form_choose_date"))
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(date == nil ? accent : Color.primary)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Color(.systemBackground)))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(error ? Color.red : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(PressableStyle())
    }
}

/// Liaison verticale entre deux dates, avec la durée calculée dans une pastille.
private struct Connector: View {
    let accent: Color
    let text: String
    let error: Bool

    var body: some View {
        HStack(spacing: 0) {
            Path { p in
                p.move(to: CGPoint(x: 34, y: 2))
                p.addLine(to: CGPoint(x: 34, y: 44))
            }
            .stroke(accent.opacity(0.45), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: [4, 8]))
            .frame(width: 68, height: 46)
            Text(text)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(error ? Color.red : accent)
                .lineLimit(1)
                .padding(.horizontal, 12)
                .padding(.vertical, 5)
                .background(Capsule().fill((error ? Color.red : accent).opacity(0.12)))
                .id(text)
                .transition(.scale(scale: 0.85).combined(with: .opacity))
        }
        .frame(height: 46)
        .animation(.spring(response: 0.4, dampingFraction: 0.6), value: text)
    }
}

/// Feuille du bas : suggestions rapides, calendrier graphique, date choisie et écart, « Valider ».
private struct DateSheet: View {
    let field: GoalFormView.DateField
    let kind: GoalKind
    let current: LocalDate?
    let other: LocalDate?
    let today: LocalDate
    let base: Color
    let onPick: (LocalDate) -> Void

    @State private var selection: Date

    init(
        field: GoalFormView.DateField,
        kind: GoalKind,
        current: LocalDate?,
        other: LocalDate?,
        today: LocalDate,
        base: Color,
        onPick: @escaping (LocalDate) -> Void
    ) {
        self.field = field
        self.kind = kind
        self.current = current
        self.other = other
        self.today = today
        self.base = base
        self.onPick = onPick
        _selection = State(initialValue: DateFormats.date(current ?? today))
    }

    private var selected: LocalDate { LocalDate.today(now: selection) }

    private var suggestions: [(key: String, date: LocalDate)] {
        if field == .target {
            return [
                ("form_suggest_in_week", today.adding(days: 7)),
                ("form_suggest_month", today.adding(months: 1)),
                ("form_suggest_3_months", today.adding(months: 3)),
                ("form_suggest_year", today.adding(months: 12)),
                ("form_suggest_new_year", LocalDate(year: today.year + 1, month: 1, day: 1)),
                ("countdown_today", today),
            ]
        }
        return [
            ("countdown_today", today),
            ("form_suggest_week_ago", today.adding(days: -7)),
            ("form_suggest_month", today.adding(months: -1)),
            ("form_suggest_year", today.adding(months: -12)),
            ("form_suggest_jan_first", LocalDate(year: today.year, month: 1, day: 1)),
        ]
    }

    private var summary: String {
        var parts = [DateFormats.weekdayLong(selected), GoalLabels.relative(today.days(until: selected))]
        if kind == .countdown, let other {
            let n = field == .target ? other.days(until: selected) : selected.days(until: other)
            parts.append(GoalLabels.plural("form_sheet_duration", n))
        }
        return parts.joined(separator: " · ")
    }

    private var titleKey: LocalizedStringKey {
        if field == .target { return "goal_form_target_date" }
        return kind == .since ? "form_sheet_since" : "goal_form_start_date"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                Text(titleKey)
                    .font(.system(size: 22, weight: .bold))
                    .padding(.horizontal, 24)
                    .padding(.top, 24)
                Text(summary)
                    .font(.system(size: 14))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 24)
                    .animation(Motion.soft, value: summary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(suggestions, id: \.key) { suggestion in
                            let on = selected == suggestion.date
                            Button {
                                withAnimation(Motion.soft) { selection = DateFormats.date(suggestion.date) }
                            } label: {
                                Text(LocalizedStringKey(suggestion.key))
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(on ? Color.white : Color.primary)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 9)
                                    .background(Capsule().fill(on ? base : Color(.tertiarySystemFill)))
                            }
                            .buttonStyle(PressableStyle())
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                }
                DatePicker("", selection: $selection, displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .labelsHidden()
                    .tint(base)
                    .padding(.horizontal, 12)
                Button {
                    onPick(selected)
                } label: {
                    Text("action_validate")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(Capsule().fill(base))
                }
                .buttonStyle(PressableStyle())
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(32)
    }
}

// MARK: - Couleur, emoji, notifications, gestion

private struct FormSection<Content: View>: View {
    let title: LocalizedStringKey
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 17, weight: .bold))
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Pastilles de couleur : « aucune » (gris-bleu neutre) + les 10 couleurs nommées.
private struct ColorSwatches: View {
    @Binding var selection: GoalColor?

    var body: some View {
        FlowLayout(spacing: 12) {
            swatch(nil)
            ForEach(GoalColor.allCases, id: \.self) { swatch($0) }
        }
    }

    private func swatch(_ color: GoalColor?) -> some View {
        let isSelected = selection == color
        let fill = color.map(GoalAppearance.deep) ?? GoalTint.neutral
        return Button {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.4)) { selection = color }
        } label: {
            ZStack {
                Circle().fill(fill)
                Circle().fill(LinearGradient(colors: [.white.opacity(0.25), .clear, .black.opacity(0.3)], startPoint: .topLeading, endPoint: .bottomTrailing))
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .frame(width: 36, height: 36)
            .padding(5)
            .overlay(Circle().strokeBorder(Color.primary, lineWidth: isSelected ? 3 : 0))
            .scaleEffect(isSelected ? 1.12 : 1)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(color.map(GoalAppearance.nameKey) ?? LocalizedStringKey("goal_color_none"))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Emojis suggérés (le choisi rebondit), « aucun », ou un emoji libre (≤ 16 unités UTF-16, comme Android).
private struct EmojiChoices: View {
    @Binding var selection: String
    let base: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    bubble(nil)
                    ForEach(GoalStyle.suggestedEmojis, id: \.self) { bubble($0) }
                }
                .padding(.vertical, 6)
                .padding(.horizontal, 4)
            }
            TextField("goal_form_emoji_custom", text: customBinding)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Color(.secondarySystemGroupedBackground)))
        }
    }

    /// Le champ libre n'affiche que ce qui n'est pas une suggestion.
    private var customBinding: Binding<String> {
        Binding(
            get: { GoalStyle.suggestedEmojis.contains(selection) ? "" : selection },
            set: { newValue in
                if newValue.utf16.count <= GoalStyle.emojiMaxLength { selection = newValue }
            }
        )
    }

    private func bubble(_ emoji: String?) -> some View {
        let isSelected = emoji.map { $0 == selection } ?? (GoalStyle.normalizedEmoji(selection) == nil)
        return Button {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.35)) { selection = emoji ?? "" }
        } label: {
            Group {
                if let emoji {
                    Text(verbatim: emoji).font(.system(size: 24))
                } else {
                    Image(systemName: "xmark").foregroundStyle(.secondary)
                }
            }
            .frame(width: 50, height: 50)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(isSelected ? base.opacity(0.22) : Color(.tertiarySystemFill)))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(isSelected ? base : .clear, lineWidth: 2))
            .scaleEffect(isSelected ? 1.14 : 1)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(emoji.map { Text(verbatim: $0) } ?? Text("goal_emoji_none"))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Carte « Notifications de jalons ». Refusées dans Réglages : l'interrupteur reste actif (préférence
/// de l'objectif) et un lien mène aux réglages.
private struct NotificationCard: View {
    @Binding var enabled: Bool
    let denied: Bool
    let base: Color
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 14) {
                Image(systemName: enabled ? "bell.fill" : "bell")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(enabled ? base : Color.secondary)
                    .symbolEffect(.bounce, value: enabled)
                    .frame(width: 48, height: 48)
                    .background(Circle().fill((enabled ? base : Color.secondary).opacity(0.15)))
                VStack(alignment: .leading, spacing: 2) {
                    Text("goal_form_notifications")
                        .font(.system(size: 16, weight: .bold))
                    Text("goal_form_notifications_footer")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                }
                Toggle("goal_form_notifications", isOn: $enabled.animation(Motion.soft))
                    .labelsHidden()
                    .tint(base)
            }
            if enabled && denied {
                VStack(alignment: .leading, spacing: 4) {
                    Text("notifications_denied_hint")
                        .font(.system(size: 13))
                        .foregroundStyle(.red)
                    Button("notifications_open_settings") {
                        if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                    }
                    .font(.system(size: 14, weight: .semibold))
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(Color(.secondarySystemGroupedBackground)))
    }
}

/// Carte « Série » : remise à zéro possible, record et historique (compteur « depuis » seulement).
private struct StreakCard: View {
    @Binding var enabled: Bool
    let base: Color

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "arrow.counterclockwise")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(enabled ? base : Color.secondary)
                .frame(width: 48, height: 48)
                .background(Circle().fill((enabled ? base : Color.secondary).opacity(0.15)))
            VStack(alignment: .leading, spacing: 2) {
                Text("goal_form_streak")
                    .font(.system(size: 16, weight: .bold))
                Text("goal_form_streak_footer")
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
            }
            Toggle("goal_form_streak", isOn: $enabled.animation(Motion.soft))
                .labelsHidden()
                .tint(base)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(Color(.secondarySystemGroupedBackground)))
    }
}

/// Modification : « Continuer en compteur depuis » (cible dépassée) et « Supprimer », tous deux confirmés.
private struct ManageCard: View {
    let canContinueAsSince: Bool
    let onContinue: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            if canContinueAsSince {
                row("arrow.forward.circle", "goal_action_continue_as_since", "form_continue_hint", Color.accentColor, onContinue)
            }
            row("trash", "form_delete", "form_delete_hint", Color.red, onDelete)
        }
        .padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(Color(.secondarySystemGroupedBackground)))
    }

    private func row(
        _ icon: String,
        _ title: LocalizedStringKey,
        _ subtitle: LocalizedStringKey,
        _ color: Color,
        _ action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(color)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(color.opacity(0.12)))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(color)
                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
