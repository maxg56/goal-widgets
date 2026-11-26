// Non vérifié : écrit sans Xcode (machine Linux).
// Partagé avec l'extension widget (project.yml) : SwiftUI pur, aucune API réservée à l'app.
import SwiftUI
import GoalCore

/*
 * Dessin des graphiques (détail, aperçu du formulaire, widget). Tous les calculs (périodes, hauteurs,
 * caps, positions) viennent de GoalCore ; ici on ne fait que dessiner, bouts arrondis.
 * `reveal` (0 → 1) est animable : la vue est recalculée à chaque image de l'animation, ce qui permet
 * une apparition décalée élément par élément. Le widget dessine avec `reveal = 1`.
 */

/// Part d'un élément `i` sur `n` apparue, pour une vague de largeur `wave` éléments.
func stagger(_ reveal: Double, _ i: Int, _ n: Int, wave: Double = 4) -> Double {
    min(max((reveal * (Double(n) + wave) - Double(i)) / wave, 0), 1)
}

/// Anneau épais à bouts ronds (piste translucide + arc de `fraction`), départ en haut, sens horaire.
struct RingGauge: View {
    var fraction: Double
    var color: Color
    var track: Color
    var lineWidth: CGFloat

    var body: some View {
        ZStack {
            Circle().stroke(track, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: min(max(fraction, 0), 1))
                .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .opacity(fraction > 0.003 ? 1 : 0)
        }
        .padding(lineWidth / 2)
    }
}

/// Arcs concentriques (`ArcsChart`) : la première période à l'extérieur ; passé plein, période en cours partielle.
struct ArcsDrawing: View, Animatable {
    let arcs: ArcsChart
    let accent: Color
    var reveal: Double = 1
    /// Épaisseur maximale d'un arc.
    var maxStroke: CGFloat = 26

    var animatableData: Double {
        get { reveal }
        set { reveal = newValue }
    }

    var body: some View {
        Canvas { context, size in
            let n = arcs.segments.count
            guard n > 0 else { return }
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let outer = min(size.width, size.height) / 2 - 2
            let inner = outer * 0.34
            let pitch = (outer - inner) / CGFloat(n)
            let stroke = min(pitch * 0.72, maxStroke)
            let current = arcs.currentIndex
            for (i, segment) in arcs.segments.enumerated() {
                let r = outer - pitch * CGFloat(i) - stroke / 2
                guard r > 0 else { continue }
                let ring = Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2))
                let future = segment.state == .remaining
                context.stroke(ring, with: .color(accent.opacity(future ? 0.08 : 0.14)), lineWidth: stroke)
                let sweep = 360 * segment.fill * stagger(reveal, i, n, wave: 3)
                if sweep > 0.5 {
                    let alpha = i == current ? 1 : 0.55 + 0.45 * Double(i + 1) / Double(n)
                    var arc = Path()
                    arc.addArc(center: center, radius: r, startAngle: .degrees(-90), endAngle: .degrees(-90 + sweep), clockwise: false)
                    context.stroke(arc, with: .color(accent.opacity(alpha)), style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                }
            }
        }
    }
}

/// Barres arrondies (`BarsChart`, hauteur = chemin cumulé) de `range` : écoulé plein, restant translucide,
/// barre du jour en pleine couleur avec halo. `labels` = jour/mois sous les barres.
struct BarsDrawing: View, Animatable {
    let chart: BarsChart
    let range: ClosedRange<Int>
    let accent: Color
    var reveal: Double = 1
    var labels: Bool = true
    var labelColor: Color = .secondary
    /// Largeur imposée d'une barre (défilement du détail) ; nil = les barres remplissent la largeur.
    var barWidth: CGFloat? = nil
    var gap: CGFloat = 5

    var animatableData: Double {
        get { reveal }
        set { reveal = newValue }
    }

    var body: some View {
        Canvas { context, size in
            guard !chart.bars.isEmpty else { return }
            let indices = Array(range).filter { chart.bars.indices.contains($0) }
            let n = indices.count
            guard n > 0 else { return }
            let bw = barWidth ?? min(max((size.width - gap * CGFloat(n)) / CGFloat(n), 3), 34)
            let left = barWidth == nil ? (size.width - (bw + gap) * CGFloat(n)) / 2 + gap / 2 : gap / 2
            let topSpace: CGFloat = labels ? 14 : 2
            let labelSpace: CGFloat = labels ? 22 : 0
            let chartHeight = max(size.height - labelSpace - topSpace, 1)
            let bottom = topSpace + chartHeight
            let todayIndex = chart.todayIndex
            for (k, i) in indices.enumerated() {
                let bar = chart.bars[i]
                let h = CGFloat(bar.height) * chartHeight * stagger(reveal, k, n, wave: 6)
                let x = left + CGFloat(k) * (bw + gap)
                let isToday = i == todayIndex
                let color: Color
                if isToday {
                    color = accent
                } else if bar.segment.state == .elapsed {
                    color = accent.opacity(0.78)
                } else {
                    color = accent.opacity(0.2)
                }
                if h > 0 {
                    let rect = CGRect(x: x, y: bottom - h, width: bw, height: h)
                    if isToday {
                        let halo = rect.insetBy(dx: -3, dy: -3)
                        context.fill(Path(roundedRect: halo, cornerRadius: bw / 2 + 3), with: .color(accent.opacity(0.22)))
                    }
                    context.fill(Path(roundedRect: rect, cornerRadius: bw / 2), with: .color(color))
                    if isToday && h > bw {
                        let dot = bw * 0.22
                        context.fill(
                            Path(ellipseIn: CGRect(x: x + bw / 2 - dot, y: bottom - h + bw / 2 - dot, width: dot * 2, height: dot * 2)),
                            with: .color(.white)
                        )
                    }
                }
                if labels, let text = Self.label(chart.unit, bar.segment.start, index: k, roomy: bw >= 16) {
                    let bold = isToday || text.count == 4
                    context.draw(
                        Text(text)
                            .font(.system(size: 10, weight: bold ? .bold : .regular))
                            .foregroundStyle(isToday ? accent : labelColor),
                        at: CGPoint(x: x + bw / 2, y: bottom + 5),
                        anchor: .top
                    )
                }
            }
        }
    }

    /// Jour (barres jour/semaine) ; année au 1er janvier et à la première barre, sinon initiale du mois.
    static func label(_ unit: SegmentUnit, _ start: LocalDate, index: Int, roomy: Bool) -> String? {
        switch unit {
        case .day:
            return roomy || index % 3 == 0 ? "\(start.day)" : nil
        case .week:
            return roomy || index % 2 == 0 ? "\(start.day)" : nil
        case .month, .year:
            if start.month == 1 || index == 0 { return "\(start.year)" }
            return roomy ? String(DateFormats.monthShort(start).prefix(1)).uppercased() : nil
        }
    }
}

/// Grille de points (`DotGrid`) : apparition en vague ; le point du jour plus gros, entouré d'un halo
/// (`breath` le fait respirer dans l'app).
struct DotsDrawing: View, Animatable {
    let grid: DotGrid
    let accent: Color
    var reveal: Double = 1
    var breath: Double = 1
    /// Taille maximale d'un emplacement.
    var maxPitch: Double = 28

    var animatableData: Double {
        get { reveal }
        set { reveal = newValue }
    }

    var body: some View {
        Canvas { context, size in
            let n = grid.cells.count
            guard n > 0 else { return }
            let layout = DotGridLayout.fit(count: n, width: size.width, height: size.height)
            let cell = CGFloat(min(layout.pitch, maxPitch))
            let left = (size.width - CGFloat(layout.columns) * cell) / 2
            let top = (size.height - CGFloat(layout.rows) * cell) / 2
            let wave = 24.0
            for (i, dot) in grid.cells.enumerated() {
                let appear = min(max((reveal * (Double(n) + wave) - Double(i)) / wave, 0), 1)
                guard appear > 0 else { continue }
                let center = CGPoint(
                    x: left + (CGFloat(i % layout.columns) + 0.5) * cell,
                    y: top + (CGFloat(i / layout.columns) + 0.5) * cell
                )
                let r = cell * 0.34 * appear
                func circle(_ radius: CGFloat) -> Path {
                    Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
                }
                switch dot.state {
                case .elapsed:
                    context.fill(circle(r), with: .color(accent.opacity(appear)))
                case .today:
                    context.fill(circle(r * 1.6 * breath), with: .color(accent.opacity(0.25 * appear)))
                    context.fill(circle(r * 1.1), with: .color(accent))
                case .remaining, .outside:
                    context.fill(circle(r), with: .color(accent.opacity(0.18 * appear)))
                }
            }
        }
    }
}

/// Mini frise (widget, cartes) : piste, partie écoulée jusqu'à aujourd'hui, repère du jour et cible.
struct MiniStripDrawing: View, Animatable {
    let strip: GoalStrip
    let color: Color
    var reveal: Double = 1

    var animatableData: Double {
        get { reveal }
        set { reveal = newValue }
    }

    var body: some View {
        Canvas { context, size in
            let thick = min(size.height * 0.45, 8)
            let y = size.height / 2
            let pad = thick
            let width = max(size.width - 2 * pad, 1)
            func x(_ position: Double) -> CGFloat { pad + width * CGFloat(min(max(position, 0), 1)) }
            func line(_ from: CGFloat, _ to: CGFloat) -> Path {
                var p = Path()
                p.move(to: CGPoint(x: from, y: y))
                p.addLine(to: CGPoint(x: to, y: y))
                return p
            }
            let solidEnd = x(strip.position(strip.solidEnd))
            context.stroke(line(pad, solidEnd), with: .color(color.opacity(0.25)), style: StrokeStyle(lineWidth: thick, lineCap: .round))
            if size.width - pad > solidEnd + thick {
                context.stroke(
                    line(solidEnd + thick, size.width - pad),
                    with: .color(color.opacity(0.3)),
                    style: StrokeStyle(lineWidth: thick / 3, lineCap: .round, dash: [4, 4])
                )
            }
            let todayX = pad + (x(strip.todayPosition) - pad) * CGFloat(reveal)
            if todayX > pad {
                context.stroke(line(pad, todayX), with: .color(color), style: StrokeStyle(lineWidth: thick, lineCap: .round))
            }
            if let target = strip.targetPosition {
                let tx = x(target)
                let r = thick * 0.8
                context.stroke(
                    Path(ellipseIn: CGRect(x: tx - r, y: y - r, width: r * 2, height: r * 2)),
                    with: .color(color),
                    lineWidth: max(1.5, thick * 0.3)
                )
            }
            let r = thick * 0.75
            context.fill(Path(ellipseIn: CGRect(x: todayX - r, y: y - r, width: r * 2, height: r * 2)), with: .color(color))
        }
    }
}
