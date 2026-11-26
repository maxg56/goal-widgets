// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import GoalCore

/*
 * Graphiques du détail : dessin partagé (Charts/ChartDrawing.swift) + apparition en ressort quand la
 * page devient active, libellés, défilement des barres et de la frise. Aucun calcul ici.
 */

/// Révélation 0 → 1 en ressort, relancée quand `active` devient vrai.
private struct Reveal<Content: View>: View {
    let active: Bool
    var damping: Double = 0.7
    @ViewBuilder let content: (Double) -> Content
    @State private var reveal = 0.0

    var body: some View {
        content(reveal)
            .onAppear { start(active) }
            .onChange(of: active) { _, now in start(now) }
    }

    private func start(_ now: Bool) {
        guard now, reveal < 1 else { return }
        withAnimation(Motion.reveal(damping: damping)) { reveal = 1 }
    }
}

/// Note discrète en bas à droite (« 1 point = 1 jour »).
private struct UnitNote: View {
    let key: String

    var body: some View {
        Text(LocalizedStringKey(key))
            .font(.system(size: 11))
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .trailing)
    }
}

/// Points (`DotGrid` sur l'objectif entier) : apparition en vague, point du jour qui respire.
struct DotsChartView: View {
    let grid: DotGrid
    let accent: Color
    let active: Bool

    var body: some View {
        VStack(spacing: 6) {
            Reveal(active: active, damping: 1) { reveal in
                if active {
                    TimelineView(.animation(minimumInterval: 1.0 / 30)) { context in
                        let t = context.date.timeIntervalSinceReferenceDate
                        DotsDrawing(grid: grid, accent: accent, reveal: reveal, breath: 1.05 + 0.2 * sin(t * .pi / 1.3))
                    }
                } else {
                    DotsDrawing(grid: grid, accent: accent, reveal: reveal)
                }
            }
            UnitNote(key: {
                switch grid.unit {
                case .day: return "dots_unit_day"
                case .week: return "dots_unit_week"
                case .month: return "dots_unit_month"
                }
            }())
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("chart_dots"))
    }
}

/// Anneau épais à bouts ronds, pourcentage au centre ; l'arc suit la valeur en ressort.
struct RingChartView: View {
    let ring: RingChart
    let accent: Color
    let active: Bool

    var body: some View {
        let texts = GoalLabels.ringTexts(ring)
        GeometryReader { proxy in
            let side = min(proxy.size.width, proxy.size.height)
            let stroke = min(side * 0.13, 34)
            Reveal(active: active, damping: 0.55) { reveal in
                RingGauge(fraction: ring.fraction * reveal, color: accent, track: accent.opacity(0.16), lineWidth: stroke)
            }
            .padding(4)
            .frame(width: side, height: side)
            .overlay {
                VStack(spacing: 2) {
                    Text(texts.center)
                        .font(.system(size: 40, weight: .black, design: .rounded))
                    Text(texts.caption)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(.secondary)
                    Text(texts.footnote)
                        .font(.system(size: 11))
                        .foregroundStyle(.tertiary)
                        .multilineTextAlignment(.center)
                        .padding(.top, 4)
                        .padding(.horizontal, stroke + 12)
                }
                .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .accessibilityElement(children: .combine)
    }
}

/// Un arc par période, la première à l'extérieur ; libellé et part de la période en cours au centre.
struct ArcsChartView: View {
    let arcs: ArcsChart
    let accent: Color
    let active: Bool

    var body: some View {
        ZStack {
            Reveal(active: active, damping: 0.75) { reveal in
                ArcsDrawing(arcs: arcs, accent: accent, reveal: reveal)
            }
            VStack(spacing: 2) {
                if !arcs.segments.isEmpty {
                    Text(label(arcs.currentIndex ?? arcs.segments.count - 1))
                        .font(.system(size: 13, weight: .bold))
                    Text(arcs.currentIndex.map { GoalLabels.percent(Int((arcs.segments[$0].fill * 100).rounded())) } ?? "—")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .overlay(alignment: .bottomTrailing) {
            Text(unitNote)
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }

    private func label(_ index: Int) -> String {
        let segment = arcs.segments[index]
        switch arcs.unit {
        case .day, .week:
            return GoalLabels.format("arcs_week_label", DateFormats.dayMonth(segment.start))
        case .month:
            return DateFormats.monthYear(segment.start)
        case .year:
            return arcs.step == 1 ? "\(segment.start.year)" : "\(segment.start.year)–\(segment.end.year)"
        }
    }

    private var unitNote: String {
        switch arcs.unit {
        case .day, .week: return String(localized: "arcs_unit_week")
        case .month: return String(localized: "arcs_unit_month")
        case .year: return GoalLabels.plural("arcs_unit_years", arcs.step)
        }
    }
}

/// Barres arrondies (hauteur = chemin cumulé) ; défilables si elles ne tiennent pas, centrées sur aujourd'hui.
struct BarsChartView: View {
    let chart: BarsChart
    let accent: Color
    let active: Bool

    private let gap: CGFloat = 5

    var body: some View {
        VStack(spacing: 6) {
            GeometryReader { proxy in
                let n = chart.bars.count
                let available = proxy.size.width
                let barWidth = min(max((available - gap * CGFloat(n)) / CGFloat(max(n, 1)), 10), 34)
                let contentWidth = (barWidth + gap) * CGFloat(n)
                if contentWidth > available {
                    ScrollViewReader { scroller in
                        ScrollView(.horizontal, showsIndicators: false) {
                            ZStack(alignment: .topLeading) {
                                bars(barWidth: barWidth)
                                    .frame(width: contentWidth, height: proxy.size.height)
                                // Repère invisible sur la barre du jour (défilement initial).
                                if let today = chart.todayIndex {
                                    Color.clear
                                        .frame(width: barWidth, height: 1)
                                        .offset(x: CGFloat(today) * (barWidth + gap))
                                        .id("today")
                                }
                            }
                        }
                        .onAppear { scroller.scrollTo("today", anchor: .center) }
                    }
                } else {
                    bars(barWidth: nil)
                }
            }
            UnitNote(key: {
                switch chart.unit {
                case .day: return "bars_unit_day"
                case .week: return "bars_unit_week"
                default: return "bars_unit_month"
                }
            }())
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("chart_bars"))
    }

    private func bars(barWidth: CGFloat?) -> some View {
        Reveal(active: active, damping: 0.6) { reveal in
            BarsDrawing(
                chart: chart,
                range: 0...max(chart.bars.count - 1, 0),
                accent: accent,
                reveal: reveal,
                labelColor: .secondary,
                barWidth: barWidth,
                gap: gap
            )
        }
    }
}

/// Frise horizontale d'un objectif (`GoalStrip`) : partie écoulée remplie en ressort, graduations
/// mensuelles, repère du jour qui pulse. Défilable si longue ; un appui affiche la date touchée et l'écart.
struct GoalStripView: View {
    let strip: GoalStrip
    let accent: Color
    let active: Bool

    @State private var selected: LocalDate?
    private let pad: CGFloat = 28

    var body: some View {
        GeometryReader { proxy in
            let available = proxy.size.width
            let months = max(strip.ticks.count + 1, 1)
            let contentWidth = max(available, 60 * CGFloat(months))
            if contentWidth > available {
                ScrollViewReader { scroller in
                    ScrollView(.horizontal, showsIndicators: false) {
                        ZStack(alignment: .topLeading) {
                            canvas(width: contentWidth, height: proxy.size.height)
                            Color.clear
                                .frame(width: 1, height: 1)
                                .offset(x: pad + (contentWidth - 2 * pad) * CGFloat(strip.todayPosition))
                                .id("today")
                        }
                    }
                    .onAppear { scroller.scrollTo("today", anchor: .center) }
                }
            } else {
                canvas(width: available, height: proxy.size.height)
            }
        }
        .frame(height: 132)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(GoalLabels.format("strip_start", DateFormats.dayMonth(strip.goalStart)))
    }

    private func canvas(width: CGFloat, height: CGFloat) -> some View {
        Reveal(active: active, damping: 0.7) { reveal in
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !active)) { context in
                let t = context.date.timeIntervalSinceReferenceDate
                StripCanvas(
                    strip: strip,
                    accent: accent,
                    reveal: reveal,
                    pulse: 0.925 + 0.225 * sin(t * .pi / 1.2),
                    selected: selected,
                    pad: pad
                )
            }
        }
        .frame(width: width, height: height)
        .contentShape(Rectangle())
        .onTapGesture(coordinateSpace: .local) { location in
            let day = strip.date(at: Double((location.x - pad) / (width - 2 * pad)))
            withAnimation(Motion.soft) { selected = selected == day ? nil : day }
        }
    }
}

/// Dessin de la frise (étiquettes et pastilles mesurées dans le Canvas).
private struct StripCanvas: View {
    let strip: GoalStrip
    let accent: Color
    let reveal: Double
    let pulse: Double
    let selected: LocalDate?
    let pad: CGFloat

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let startLabel = GoalLabels.format("strip_start", DateFormats.dayMonth(strip.goalStart))
        let targetLabel = strip.target.map { GoalLabels.format("strip_target", DateFormats.medium($0)) }
        let todayLabel = String(localized: "strip_today")
        let surface = colorScheme == .dark ? Color(white: 0.11) : Color.white
        let onSurface = Color.primary
        Canvas { context, size in
            let w = size.width - 2 * pad
            func x(_ position: Double) -> CGFloat { pad + w * CGFloat(position) }
            func line(_ a: CGFloat, _ b: CGFloat, _ y: CGFloat) -> Path {
                var p = Path()
                p.move(to: CGPoint(x: a, y: y))
                p.addLine(to: CGPoint(x: b, y: y))
                return p
            }
            func circle(_ c: CGPoint, _ r: CGFloat) -> Path {
                Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
            }
            /// Texte centré sur `cx`, gardé dans la zone.
            func label(_ text: String, _ cx: CGFloat, top: CGFloat, color: Color, bold: Bool) {
                let resolved = context.resolve(Text(text).font(.system(size: 10, weight: bold ? .bold : .regular)).foregroundStyle(color))
                let s = resolved.measure(in: CGSize(width: 400, height: 40))
                let left = min(max(cx - s.width / 2, 0), max(size.width - s.width, 0))
                context.draw(resolved, at: CGPoint(x: left, y: top), anchor: .topLeading)
            }
            /// Pastille arrondie centrée sur `cx`.
            func pill(_ text: String, _ cx: CGFloat, cy: CGFloat, background: Color, foreground: Color) {
                let resolved = context.resolve(Text(text).font(.system(size: 11, weight: .bold)).foregroundStyle(foreground))
                let s = resolved.measure(in: CGSize(width: 400, height: 40))
                let pw = s.width + 18
                let ph = s.height + 8
                let left = min(max(cx - pw / 2, 0), max(size.width - pw, 0))
                let rect = CGRect(x: left, y: cy - ph / 2, width: pw, height: ph)
                context.fill(Path(roundedRect: rect, cornerRadius: ph / 2), with: .color(background))
                context.draw(resolved, at: CGPoint(x: rect.midX, y: rect.midY), anchor: .center)
            }

            let y: CGFloat = 70
            let thick: CGFloat = 12
            let todayX = x(strip.todayPosition)
            let solidEnd = x(strip.position(strip.solidEnd))
            context.stroke(line(pad, solidEnd, y), with: .color(accent.opacity(0.16)), style: StrokeStyle(lineWidth: thick, lineCap: .round))
            if size.width - pad > solidEnd + thick {
                context.stroke(
                    line(solidEnd + thick, size.width - pad, y),
                    with: .color(accent.opacity(0.22)),
                    style: StrokeStyle(lineWidth: thick / 3, lineCap: .round, dash: [6, 6])
                )
            }
            let monthWidth = Double(w) / Double(max(strip.ticks.count + 1, 1))
            let step = GoalStrip.labelStep(monthWidth: monthWidth, minSpacing: 40)
            for (k, tick) in strip.ticks.enumerated() {
                let tx = x(tick.position)
                let jan = tick.yearStart
                var mark = Path()
                mark.move(to: CGPoint(x: tx, y: y + thick))
                mark.addLine(to: CGPoint(x: tx, y: y + thick + (jan ? 10 : 6)))
                context.stroke(mark, with: .color(onSurface.opacity(jan ? 0.45 : 0.2)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                if jan || k % step == 0 {
                    let text = jan ? "\(tick.date.year)" : DateFormats.monthShort(tick.date).replacingOccurrences(of: ".", with: "")
                    label(text, tx, top: y + thick + 12, color: onSurface.opacity(jan ? 0.75 : 0.45), bold: jan)
                }
            }
            let filled = pad + (todayX - pad) * CGFloat(max(reveal, 0))
            if filled > pad {
                context.stroke(line(pad, filled, y), with: .color(accent), style: StrokeStyle(lineWidth: thick, lineCap: .round))
            }
            let startX = x(strip.startPosition)
            context.fill(circle(CGPoint(x: startX, y: y), thick * 0.75), with: .color(accent))
            label(startLabel, startX, top: y - 40, color: onSurface.opacity(0.6), bold: false)
            if let targetPosition = strip.targetPosition, let targetLabel {
                let tx = x(targetPosition)
                context.fill(circle(CGPoint(x: tx, y: y), thick * 0.85), with: .color(surface))
                context.stroke(circle(CGPoint(x: tx, y: y), thick * 0.85), with: .color(accent), lineWidth: 3)
                label(targetLabel, tx, top: y - 40, color: onSurface.opacity(0.75), bold: true)
            }
            if reveal > 0.2 {
                let c = CGPoint(x: filled, y: y)
                context.fill(circle(c, thick * 1.3 * CGFloat(pulse)), with: .color(accent.opacity(0.22)))
                context.fill(circle(c, thick * 0.55), with: .color(.white))
                context.stroke(circle(c, thick * 0.55), with: .color(accent), lineWidth: 3)
                pill(todayLabel, filled, cy: y - 22, background: accent, foreground: .white)
            }
            if let selected {
                let sx = x(strip.position(selected))
                var tick = Path()
                tick.move(to: CGPoint(x: sx, y: y - thick))
                tick.addLine(to: CGPoint(x: sx, y: y + thick))
                context.stroke(tick, with: .color(onSurface.opacity(0.5)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                let relative = GoalLabels.relative(strip.today.days(until: selected))
                pill(GoalLabels.joined(DateFormats.medium(selected), relative), sx, cy: y + 44, background: onSurface, foreground: surface)
            }
        }
    }
}

/// Sélecteur de graphique : l'indicateur glisse en ressort ; en personnalisation, × sur chaque puce.
struct ChartSelector: View {
    let selected: ChartKind
    let kinds: [ChartKind]
    let editing: Bool
    let onSelect: (ChartKind) -> Void
    let onRemove: (ChartKind) -> Void

    @Namespace private var indicator

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(kinds.enumerated()), id: \.element) { index, kind in
                let isSelected = kind == selected
                let name = GoalLabels.chartName(kind)
                let removable = editing && kinds.count > 1
                HStack(spacing: 4) {
                    Text(name)
                        .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                        .foregroundStyle(isSelected ? Color.black.opacity(0.82) : Color.white.opacity(0.9))
                        .lineLimit(1)
                    if removable {
                        Button { onRemove(kind) } label: {
                            Image(systemName: "xmark")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(isSelected ? Color.white : Color.black.opacity(0.8))
                                .frame(width: 18, height: 18)
                                .background(Circle().fill(isSelected ? Color.black.opacity(0.75) : Color.white.opacity(0.85)))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(GoalLabels.format("action_hide", name))
                        .transition(.scale.combined(with: .opacity))
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background {
                    if isSelected {
                        Capsule().fill(Color.white).matchedGeometryEffect(id: "chip", in: indicator)
                    }
                }
                .contentShape(Capsule())
                .onTapGesture { onSelect(kind) }
                .wobble(removable, seed: index + 5, amplitude: 1.6)
                .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
            }
        }
        .padding(4)
        .frame(height: 44)
        .background(Capsule().fill(Color.white.opacity(0.14)))
        .animation(Motion.bouncy, value: selected)
        .animation(.spring(response: 0.4, dampingFraction: 0.45), value: editing)
    }
}
