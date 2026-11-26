package app.goalwidgets.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goalwidgets.R
import app.goalwidgets.core.ArcsChart
import app.goalwidgets.core.BarsChart
import app.goalwidgets.core.ChartKind
import app.goalwidgets.core.DotGrid
import app.goalwidgets.core.DotGridLayout
import app.goalwidgets.core.DotState
import app.goalwidgets.core.DotUnit
import app.goalwidgets.core.GoalStrip
import app.goalwidgets.core.RingChart
import app.goalwidgets.core.SegmentState
import app.goalwidgets.core.SegmentUnit
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/*
 * Graphiques du détail, dessinés au Canvas (bouts arrondis, apparition en ressort). Tous les calculs
 * (périodes, hauteurs, caps, positions) viennent de :core ; ici on ne fait que dessiner.
 */

/** Part d'un élément [i] sur [n] apparue, pour une vague de largeur [wave] éléments. */
private fun stagger(reveal: Float, i: Int, n: Int, wave: Float = 4f): Float =
    ((reveal * (n + wave)) - i).div(wave).coerceIn(0f, 1.1f)

/** Sélecteur de graphique : l'indicateur glisse en ressort ; en personnalisation, × sur chaque chip. */
@Composable
fun ChartSelector(
    selected: ChartKind,
    kinds: List<ChartKind>,
    onSelect: (ChartKind) -> Unit,
    editing: Boolean,
    onRemove: (ChartKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(4.dp),
    ) {
        val n = kinds.size.coerceAtLeast(1)
        val chipWidth = maxWidth / n
        val x by animateDpAsState(chipWidth * kinds.indexOf(selected).coerceAtLeast(0), Motion.bouncy(), label = "chip")
        Box(Modifier.offset(x = x).width(chipWidth).fillMaxHeight().clip(RoundedCornerShape(50)).background(Color.White))
        Row(Modifier.fillMaxSize()) {
            kinds.forEachIndexed { i, kind ->
                val isSelected = kind == selected
                val name = GoalLabels.chartName(resources, kind)
                val removable = editing && kinds.size > 1
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .wobble(removable, seed = i + 5, amplitude = 1.6f)
                        .clip(RoundedCornerShape(50))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(kind) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name,
                        color = if (isSelected) Color.Black.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                    AnimatedVisibility(
                        visible = removable,
                        enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                    ) {
                        val hide = stringResource(R.string.action_hide, name)
                        Box(
                            Modifier
                                .padding(start = 4.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.85f))
                                .clickable(onClickLabel = hide) { onRemove(kind) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = hide,
                                tint = if (isSelected) Color.White else Color.Black.copy(alpha = 0.8f),
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Grille de points ([DotGrid] de :core) : apparition en vague, point du jour qui respire. */
@Composable
fun DotsChartView(grid: DotGrid, accent: Color, active: Boolean, modifier: Modifier = Modifier) {
    val reveal = rememberReveal(active, damping = 1f)
    val breath by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "breath",
    )
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        Canvas(Modifier.weight(1f).fillMaxWidth()) {
            val n = grid.cells.size
            if (n == 0) return@Canvas
            val layout = DotGridLayout.fit(n, size.width, size.height)
            val cell = layout.pitch.coerceAtMost(28.dp.toPx())
            val left = (size.width - layout.columns * cell) / 2
            val top = (size.height - layout.rows * cell) / 2
            val wave = 24f
            grid.cells.forEachIndexed { i, dot ->
                val appear = ((reveal * (n + wave)) - i).div(wave).coerceIn(0f, 1f)
                if (appear <= 0f) return@forEachIndexed
                val center = Offset(left + (i % layout.columns + 0.5f) * cell, top + (i / layout.columns + 0.5f) * cell)
                val r = cell * 0.34f * appear
                when (dot.state) {
                    DotState.ELAPSED -> drawCircle(accent.copy(alpha = appear), r, center)
                    DotState.TODAY -> {
                        drawCircle(accent.copy(alpha = 0.25f * appear), r * 1.6f * breath, center)
                        drawCircle(accent, r * 1.1f, center)
                    }
                    DotState.REMAINING, DotState.OUTSIDE -> drawCircle(accent.copy(alpha = 0.18f * appear), r, center)
                }
            }
        }
        Text(
            stringResource(
                when (grid.unit) {
                    DotUnit.DAY -> R.string.dots_unit_day
                    DotUnit.WEEK -> R.string.dots_unit_week
                    DotUnit.MONTH -> R.string.dots_unit_month
                },
            ),
            color = onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/** Textes de l'anneau : centre, légende, note (communs au détail et à l'aperçu du formulaire). */
data class RingTexts(val center: String, val caption: String, val footnote: String)

@Composable
fun ringTexts(ring: RingChart): RingTexts {
    val resources = LocalContext.current.resources
    val center = GoalLabels.percent(resources, ring.percent)
    val milestone = ring.milestone
    return if (milestone == null) {
        RingTexts(
            center,
            stringResource(R.string.ring_caption_countdown),
            if (ring.daysLeft > 0) stringResource(R.string.ring_arrival, ring.date.format(DateFormats.medium))
            else stringResource(R.string.ring_reached),
        )
    } else {
        val label = GoalLabels.milestone(resources, milestone)
        RingTexts(
            center,
            stringResource(R.string.ring_caption_since, label),
            stringResource(
                R.string.ring_milestone_footnote,
                label,
                ring.date.format(DateFormats.medium),
                resources.getQuantityString(R.plurals.days_short, ring.daysLeft, ring.daysLeft),
            ),
        )
    }
}

/** Donut épais à bouts ronds, pourcentage au centre ; l'arc suit la valeur en ressort. */
@Composable
fun RingChartView(ring: RingChart, accent: Color, active: Boolean, modifier: Modifier = Modifier) {
    val texts = ringTexts(ring)
    val reveal = rememberReveal(active, damping = 0.55f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = minOf(size.minDimension * 0.13f, 34.dp.toPx())
            val d = size.minDimension - stroke - 8.dp.toPx()
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(accent.copy(alpha = 0.16f), 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            val sweep = 360f * (ring.fraction.toFloat() * reveal).coerceAtLeast(0f)
            if (sweep > 0.5f) drawArc(accent, -90f, sweep, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(texts.center, color = onSurface, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text(texts.caption, color = onSurface.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(texts.footnote, color = onSurface.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Libellé d'un arc (période en cours). */
@Composable
private fun arcLabel(arcs: ArcsChart, index: Int): String {
    val segment = arcs.segments[index]
    return when (arcs.unit) {
        SegmentUnit.DAY -> segment.start.format(DateFormats.dayMonth)
        SegmentUnit.WEEK -> stringResource(R.string.arcs_week_label, segment.start.format(DateFormats.dayMonth))
        SegmentUnit.MONTH -> segment.start.format(DateFormats.monthYear)
        SegmentUnit.YEAR -> if (arcs.step == 1) "${segment.start.year}" else "${segment.start.year}–${segment.end.year}"
    }
}

/** Un arc par période, la première à l'extérieur ; pleins pour le passé, partiel pour la période en cours. */
@Composable
fun ArcsChartView(arcs: ArcsChart, accent: Color, active: Boolean, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val reveal = rememberReveal(active, damping = 0.75f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val n = arcs.segments.size
    val current = arcs.currentIndex
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2 - 6.dp.toPx()
            val inner = outer * 0.34f
            val pitch = (outer - inner) / n
            val stroke = (pitch * 0.72f).coerceAtMost(26.dp.toPx())
            arcs.segments.forEachIndexed { i, seg ->
                val r = outer - pitch * i - stroke / 2
                if (r <= 0f) return@forEachIndexed
                val tl = Offset(center.x - r, center.y - r)
                val sz = Size(r * 2, r * 2)
                val future = seg.state == SegmentState.REMAINING
                drawArc(accent.copy(alpha = if (future) 0.08f else 0.14f), 0f, 360f, false, tl, sz, style = Stroke(stroke))
                val sweep = 360f * seg.fill.toFloat() * stagger(reveal, i, n, wave = 3f).coerceAtMost(1f)
                if (sweep > 0.5f) {
                    val alpha = if (i == current) 1f else 0.55f + 0.45f * (i + 1) / n
                    drawArc(accent.copy(alpha = alpha), -90f, sweep, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(arcLabel(arcs, current ?: arcs.segments.lastIndex), color = onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                current?.let { GoalLabels.percent(resources, (arcs.segments[it].fill * 100).roundToInt()) } ?: "—",
                color = onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
        Text(
            when (arcs.unit) {
                SegmentUnit.DAY -> stringResource(R.string.arcs_unit_day)
                SegmentUnit.WEEK -> stringResource(R.string.arcs_unit_week)
                SegmentUnit.MONTH -> stringResource(R.string.arcs_unit_month)
                SegmentUnit.YEAR -> resources.getQuantityString(R.plurals.arcs_unit_years, arcs.step, arcs.step)
            },
            color = onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** Barres arrondies (hauteur = chemin cumulé) ; écoulé plein, restant translucide, barre du jour soulignée. Défilable. */
@Composable
fun BarsChartView(chart: BarsChart, accent: Color, active: Boolean, modifier: Modifier = Modifier) {
    val reveal = rememberReveal(active, damping = 0.6f)
    val todayIndex = chart.todayIndex
    val measurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val n = chart.bars.size
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val density = LocalDensity.current
            val avail = maxWidth
            val gap = 5.dp
            val barWidth = ((avail - gap * n) / n).coerceIn(10.dp, 34.dp)
            val contentWidth = (barWidth + gap) * n
            val scroll = rememberScrollState()
            val scrollable = contentWidth > avail
            LaunchedEffect(scrollable, todayIndex) {
                if (scrollable && todayIndex != null) {
                    scroll.scrollTo(with(density) { ((barWidth + gap) * todayIndex - avail / 2).toPx() }.toInt().coerceAtLeast(0))
                }
            }
            Box(Modifier.fillMaxSize().then(if (scrollable) Modifier.horizontalScroll(scroll) else Modifier)) {
                Canvas(Modifier.width(if (scrollable) contentWidth else avail).fillMaxHeight()) {
                    val bw = barWidth.toPx()
                    val g = gap.toPx()
                    val left = if (scrollable) g / 2 else (size.width - (bw + g) * n) / 2 + g / 2
                    val topSpace = 14.dp.toPx()
                    val chartH = size.height - 22.dp.toPx() - topSpace
                    val bottom = topSpace + chartH
                    chart.bars.forEachIndexed { i, bar ->
                        val h = bar.height.toFloat() * chartH * stagger(reveal, i, n, wave = 6f)
                        val x = left + i * (bw + g)
                        val isToday = i == todayIndex
                        val color = when {
                            isToday -> accent
                            bar.segment.state == SegmentState.ELAPSED -> accent.copy(alpha = 0.78f)
                            else -> accent.copy(alpha = 0.2f)
                        }
                        if (h > 0f) {
                            drawRoundRect(color, Offset(x, bottom - h), Size(bw, h), CornerRadius(bw / 2, bw / 2))
                            if (isToday) {
                                val halo = 3.dp.toPx()
                                drawRoundRect(accent.copy(alpha = 0.22f), Offset(x - halo, bottom - h - halo), Size(bw + 2 * halo, h + 2 * halo), CornerRadius(bw / 2 + halo))
                                drawCircle(Color.White, bw * 0.22f, Offset(x + bw / 2, bottom - h + bw / 2))
                            }
                        }
                        barLabel(chart.unit, bar.segment.start, i, bw)?.let { text ->
                            val layout = measurer.measure(
                                text,
                                TextStyle(
                                    fontSize = 10.sp,
                                    color = if (isToday) accent else onSurface.copy(alpha = 0.55f),
                                    fontWeight = if (isToday || text.length == 4) FontWeight.Bold else FontWeight.Normal,
                                ),
                            )
                            drawText(layout, topLeft = Offset(x + bw / 2 - layout.size.width / 2f, bottom + 5.dp.toPx()))
                        }
                    }
                }
            }
        }
        Text(
            stringResource(
                when (chart.unit) {
                    SegmentUnit.DAY -> R.string.bars_unit_day
                    SegmentUnit.WEEK -> R.string.bars_unit_week
                    else -> R.string.bars_unit_month
                },
            ),
            color = onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

private fun DrawScope.barLabel(unit: SegmentUnit, start: LocalDate, i: Int, barWidthPx: Float): String? {
    val roomy = barWidthPx >= 16.dp.toPx()
    return when (unit) {
        SegmentUnit.DAY -> if (roomy || i % 3 == 0) start.dayOfMonth.toString() else null
        SegmentUnit.WEEK -> if (roomy || i % 2 == 0) start.dayOfMonth.toString() else null
        else -> when {
            start.monthValue == 1 || i == 0 -> start.year.toString()
            roomy -> start.format(DateFormats.monthShort).take(1).uppercase()
            else -> null
        }
    }
}

/**
 * Frise horizontale d'un objectif ([GoalStrip]) : partie écoulée remplie en ressort, graduations
 * mensuelles, repère du jour. Défilable si longue ; un appui affiche la date touchée et l'écart.
 */
@Composable
fun GoalStripView(strip: GoalStrip, accent: Color, active: Boolean, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val reveal = rememberReveal(active, damping = 0.7f)
    val pulse by rememberInfiniteTransition(label = "stripToday").animateFloat(
        0.7f, 1.15f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse",
    )
    val measurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    var selected by remember(strip) { mutableStateOf<LocalDate?>(null) }
    val startLabel = stringResource(R.string.strip_start, strip.goalStart.format(DateFormats.dayMonth))
    val targetLabel = strip.target?.let { stringResource(R.string.strip_target, it.format(DateFormats.medium)) }
    val todayLabel = stringResource(R.string.strip_today)
    val months = (strip.ticks.size + 1).coerceAtLeast(1)

    BoxWithConstraints(modifier.fillMaxWidth().height(132.dp).semantics { contentDescription = startLabel }) {
        val density = LocalDensity.current
        val avail = maxWidth
        val pad = 28.dp
        val contentWidth = maxOf(avail, 60.dp * months)
        val scrollable = contentWidth > avail
        val scroll = rememberScrollState()
        LaunchedEffect(scrollable) {
            if (scrollable) {
                val todayX = with(density) { (pad + (contentWidth - pad * 2) * strip.todayPosition.toFloat() - avail / 2).toPx() }
                scroll.scrollTo(todayX.toInt().coerceAtLeast(0))
            }
        }
        Box(Modifier.fillMaxSize().then(if (scrollable) Modifier.horizontalScroll(scroll) else Modifier)) {
            Canvas(
                Modifier
                    .width(contentWidth)
                    .fillMaxHeight()
                    .pointerInput(strip) {
                        detectTapGestures { pos ->
                            val p = pad.toPx()
                            val day = strip.dateAt(((pos.x - p) / (size.width - 2 * p)).toDouble())
                            selected = if (selected == day) null else day
                        }
                    },
            ) {
                val p = pad.toPx()
                val w = size.width - 2 * p
                fun xOf(position: Double) = p + w * position.toFloat()
                val y = 70.dp.toPx()
                val thick = 12.dp.toPx()
                val todayX = xOf(strip.todayPosition)
                val solidEnd = xOf(strip.position(strip.solidEnd))
                drawLine(accent.copy(alpha = 0.16f), Offset(p, y), Offset(solidEnd, y), thick, StrokeCap.Round)
                if (size.width - p > solidEnd + thick) {
                    drawLine(
                        accent.copy(alpha = 0.22f), Offset(solidEnd + thick, y), Offset(size.width - p, y), thick / 3, StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                    )
                }
                val step = GoalStrip.labelStep((w / months).toDouble(), 40.dp.toPx().toDouble())
                strip.ticks.forEachIndexed { k, tick ->
                    val x = xOf(tick.position)
                    val jan = tick.yearStart
                    drawLine(
                        onSurface.copy(alpha = if (jan) 0.45f else 0.2f),
                        Offset(x, y + thick), Offset(x, y + thick + (if (jan) 10 else 6).dp.toPx()),
                        1.5.dp.toPx(), StrokeCap.Round,
                    )
                    if (jan || k % step == 0) {
                        val txt = if (jan) tick.date.year.toString() else tick.date.format(DateFormats.monthShort).removeSuffix(".")
                        label(measurer, txt, x, y + thick + 12.dp.toPx(), onSurface.copy(alpha = if (jan) 0.75f else 0.45f), jan, size.width)
                    }
                }
                val filled = p + (todayX - p) * reveal.coerceAtLeast(0f)
                if (filled > p) drawLine(accent, Offset(p, y), Offset(filled, y), thick, StrokeCap.Round)
                val startX = xOf(strip.startPosition)
                drawCircle(accent, thick * 0.75f, Offset(startX, y))
                label(measurer, startLabel, startX, y - 40.dp.toPx(), onSurface.copy(alpha = 0.6f), false, size.width)
                strip.targetPosition?.let { pos ->
                    val tx = xOf(pos)
                    drawCircle(surface, thick * 0.85f, Offset(tx, y))
                    drawCircle(accent, thick * 0.85f, Offset(tx, y), style = Stroke(3.dp.toPx()))
                    label(measurer, targetLabel.orEmpty(), tx, y - 40.dp.toPx(), onSurface.copy(alpha = 0.75f), true, size.width)
                }
                if (reveal > 0.2f) {
                    drawCircle(accent.copy(alpha = 0.22f), thick * 1.3f * pulse, Offset(filled, y))
                    drawCircle(Color.White, thick * 0.55f, Offset(filled, y))
                    drawCircle(accent, thick * 0.55f, Offset(filled, y), style = Stroke(3.dp.toPx()))
                    pill(measurer, todayLabel, filled, y - 22.dp.toPx(), accent, Color.White, size.width)
                }
                selected?.let { d ->
                    val x = xOf(strip.position(d))
                    drawLine(onSurface.copy(alpha = 0.5f), Offset(x, y - thick), Offset(x, y + thick), 2.dp.toPx(), StrokeCap.Round)
                    val rel = GoalLabels.relative(resources, ChronoUnit.DAYS.between(strip.today, d))
                    pill(measurer, resources.getString(R.string.joined_dot, d.format(DateFormats.medium), rel), x, y + 44.dp.toPx(), onSurface, surface, size.width)
                }
            }
        }
    }
}

/** Texte centré sur [x] (gardé dans la zone), haut en [top]. */
private fun DrawScope.label(m: TextMeasurer, text: String, x: Float, top: Float, color: Color, bold: Boolean, maxX: Float) {
    val layout = m.measure(text, TextStyle(fontSize = 10.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    val left = (x - layout.size.width / 2f).coerceIn(0f, (maxX - layout.size.width).coerceAtLeast(0f))
    drawText(layout, topLeft = Offset(left, top))
}

/** Pastille arrondie centrée sur [x], centre vertical en [cy]. */
private fun DrawScope.pill(m: TextMeasurer, text: String, x: Float, cy: Float, bg: Color, fg: Color, maxX: Float) {
    val layout = m.measure(text, TextStyle(fontSize = 11.sp, color = fg, fontWeight = FontWeight.Bold))
    val padH = 9.dp.toPx()
    val padV = 4.dp.toPx()
    val w = layout.size.width + padH * 2
    val h = layout.size.height + padV * 2
    val left = (x - w / 2).coerceIn(0f, (maxX - w).coerceAtLeast(0f))
    drawRoundRect(bg, Offset(left, cy - h / 2), Size(w, h), CornerRadius(h / 2))
    drawText(layout, topLeft = Offset(left + padH, cy - h / 2 + padV))
}

/** Petit anneau blanc (cartes de l'accueil, aperçu du formulaire). */
@Composable
fun MiniRing(fraction: Float, modifier: Modifier, stroke: androidx.compose.ui.unit.Dp = 10.dp, track: Color = Color.White.copy(alpha = 0.18f)) {
    Canvas(modifier) {
        val s = stroke.toPx()
        val d = size.minDimension - s
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
        if (fraction > 0.003f) {
            drawArc(Color.White, -90f, 360f * fraction.coerceIn(0f, 1.05f), false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
    }
}
