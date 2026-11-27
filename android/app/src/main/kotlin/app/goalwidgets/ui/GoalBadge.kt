package app.goalwidgets.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import app.goalwidgets.core.GoalStyle

/**
 * Pastille d'un objectif en bitmap : fond de sa couleur, emoji (ou initiale) au centre. Icônes des
 * raccourcis (adaptatives : contenu dans les 2/3 centraux) et grande icône des notifications.
 */
object GoalBadge {
    fun bitmap(title: String, style: GoalStyle, sizePx: Int, round: Boolean = false): Bitmap {
        val text = style.emoji?.takeIf { it.isNotBlank() } ?: title.trim().take(1).uppercase().ifEmpty { "•" }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GoalTint.deepColor(style).toArgb() }
        if (round) c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bg) else c.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = sizePx * if (round) 0.5f else 0.34f
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText(text, sizePx / 2f, sizePx / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        return bmp
    }
}
