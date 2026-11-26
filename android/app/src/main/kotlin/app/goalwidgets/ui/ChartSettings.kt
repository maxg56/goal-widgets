package app.goalwidgets.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.core.ChartUnit
import app.goalwidgets.core.GoalStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Réglage général de l'unité des graphiques (menu ⋮ de l'accueil), mémorisé dans les préférences.
 * Chaque objectif peut le remplacer ([GoalStyle.chartUnit]) : [unitFor] applique l'héritage.
 * Lu aussi par les widgets (même processus).
 */
class ChartSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _global = MutableStateFlow(ChartUnit.fromWire(prefs.getString(KEY_UNIT, null)) ?: ChartUnit.AUTO)
    val global: StateFlow<ChartUnit> = _global.asStateFlow()

    /** Mémorise le réglage général ; l'appelant redessine les widgets. */
    fun setGlobal(unit: ChartUnit) {
        _global.value = unit
        prefs.edit().putString(KEY_UNIT, unit.wire).apply()
    }

    /** Unité à utiliser pour un objectif : la sienne, sinon le réglage général. */
    fun unitFor(style: GoalStyle): ChartUnit = ChartUnit.resolve(style.chartUnit, _global.value)

    private companion object {
        const val PREFS = "charts"
        const val KEY_UNIT = "unit"
    }
}

/** Réglage général courant, recomposé quand il change. */
@Composable
fun globalChartUnit(): ChartUnit {
    val app = LocalContext.current.applicationContext as GoalWidgetsApplication
    val unit by app.chartSettings.global.collectAsState()
    return unit
}
