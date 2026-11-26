package app.goalwidgets.shortcuts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.MainActivity
import app.goalwidgets.R
import app.goalwidgets.core.GoalOrder
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.SortKey
import app.goalwidgets.ui.GoalTint
import java.time.Instant
import java.time.LocalDate

/**
 * Raccourcis de l'icône de l'app (appui long sur l'icône, Android 7.1+) : « Nouvel objectif » puis
 * les objectifs les plus proches (date la plus proche). Raccourcis dynamiques : ils suivent
 * l'applicationId (build « preview » compris) et sont recalculés après chaque modification.
 */
object AppShortcuts {
    /** Les lanceurs en affichent 4 à 5 ; « Nouvel objectif » + 3 objectifs. */
    private const val MAX_GOALS = 3
    private const val NEW_GOAL_ID = "new_goal"

    suspend fun update(context: Context) {
        runCatching {
            val app = context.applicationContext as GoalWidgetsApplication
            val today = LocalDate.now()
            val goals = GoalOrder.CLOSEST_DATE
                .sort(app.database.goalDao().getAll(), today) { SortKey(it.uid, it.toGoal(), Instant.ofEpochMilli(it.createdAtMillis)) }
                .take(MAX_GOALS)
            val newGoal = ShortcutInfoCompat.Builder(context, NEW_GOAL_ID)
                .setShortLabel(context.getString(R.string.shortcut_new_goal))
                .setIcon(IconCompat.createWithAdaptiveBitmap(badge("+", GoalStyle.NONE, context)))
                .setIntent(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_GOAL))
                .setRank(0)
                .build()
            val shortcuts = listOf(newGoal) + goals.mapIndexed { i, row ->
                val style = row.style()
                ShortcutInfoCompat.Builder(context, "goal_${row.id}")
                    .setShortLabel(row.title.ifBlank { context.getString(R.string.app_name) })
                    .setIcon(IconCompat.createWithAdaptiveBitmap(badge(style.emoji ?: row.title.take(1).uppercase(), style, context)))
                    .setIntent(
                        Intent(context, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .setData(Uri.parse("goalwidgets://goal/${row.id}"))
                            .putExtra(MainActivity.EXTRA_GOAL_ID, row.id),
                    )
                    .setRank(i + 1)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    /**
     * Icône adaptative : disque de la couleur de l'objectif, emoji ou initiale au centre (zone sûre
     * des icônes adaptatives : les 2/3 centraux).
     */
    private fun badge(text: String, style: GoalStyle, context: Context): Bitmap {
        val size = (108 * context.resources.displayMetrics.density).toInt().coerceIn(108, 432)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(GoalTint.deepColor(style).toArgb())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size * 0.34f
            typeface = Typeface.DEFAULT_BOLD
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2
        c.drawText(text, size / 2f, y, paint)
        return bmp
    }
}
