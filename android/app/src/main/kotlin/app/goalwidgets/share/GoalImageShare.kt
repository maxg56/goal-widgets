package app.goalwidgets.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.R
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.GoalKind
import app.goalwidgets.data.GoalEntity
import app.goalwidgets.ui.DateFormats
import app.goalwidgets.ui.GoalLabels
import app.goalwidgets.ui.GoalTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * « Partager en image » : carte PNG 1080 × 1350 (format portrait des messageries et stories) dans la
 * couleur de l'objectif — emoji, titre, grand nombre, légende, anneau de progression, date. Écrite dans
 * `cache/shared/` (exposé par le FileProvider, remplacée à chaque partage) puis feuille de partage.
 */
object GoalImageShare {
    private const val W = 1080
    private const val H = 1350

    suspend fun share(context: Context, goalId: Long) {
        val app = context.applicationContext as GoalWidgetsApplication
        val entity = app.database.goalDao().getById(goalId) ?: return
        val file = withContext(Dispatchers.Default) {
            val bmp = render(context, entity, LocalDate.now())
            val dir = File(context.cacheDir, "shared")
            dir.deleteRecursively()
            dir.mkdirs()
            File(dir, "${GoalShareNames.safe(entity.title)}.png").apply {
                outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            }
        }
        val uri = FileProvider.getUriForFile(context, GoalSharing.authority(context), file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_image_chooser_title)))
    }

    fun render(context: Context, entity: GoalEntity, today: LocalDate): Bitmap {
        val res = context.resources
        val goal = entity.toGoal()
        val style = entity.style()
        val state = CountdownEngine.compute(goal, today)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Fond : dégradé de la couleur + halos (même esprit que les cartes et le widget « Vif »).
        val stops = GoalTint.gradient(GoalTint.deepColor(style)).map { it.toArgb() }.toIntArray()
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W * 0.4f, H.toFloat(), stops, null, Shader.TileMode.CLAMP) })
        c.drawCircle(W * 0.9f, H * 0.05f, W * 0.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(W * 0.9f, H * 0.05f, W * 0.8f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        })

        val white = 0xFFFFFFFF.toInt()
        fun text(size: Float, alpha: Int = 255, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (white and 0x00FFFFFF) or (alpha shl 24)
            textSize = size
            typeface = if (bold) Typeface.create("sans-serif-condensed", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val pad = 96f
        var y = 200f

        // Emoji et titre.
        style.emoji?.takeIf { it.isNotBlank() }?.let {
            c.drawText(it, pad, y, text(110f))
            y += 140f
        }
        val titlePaint = text(72f, bold = true)
        val title = TextUtils.ellipsize(goal.title, titlePaint, W - 2 * pad, TextUtils.TruncateAt.END).toString()
        c.drawText(title, pad, y, titlePaint)

        // Anneau à droite du grand nombre (compte à rebours) ; centré sinon.
        val number = GoalLabels.bigNumber(res, state)
        val numberPaint = text(if (number.length <= 3) 360f else 360f * 3 / number.length, bold = true)
        val numberY = H * 0.62f
        c.drawText(number, pad - 12f, numberY, numberPaint)
        c.drawText(GoalLabels.caption(res, state), pad, numberY + 90f, text(56f, alpha = 220))
        GoalLabels.breakdown(res, state.breakdown)?.let { c.drawText(it, pad, numberY + 170f, text(44f, alpha = 170)) }

        state.progress?.let { progress ->
            val r = 120f
            val cx = W - pad - r
            val cy = 200f + r - 60f
            val oval = RectF(cx - r, cy - r, cx + r, cy + r)
            c.drawArc(oval, 0f, 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 30f; color = 0x40FFFFFF })
            c.drawArc(oval, -90f, 360f * progress.toFloat(), false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 30f; color = white; strokeCap = Paint.Cap.ROUND
            })
            val pct = GoalLabels.percent(res, state.percent ?: 0)
            val pctPaint = text(54f, bold = true).apply { textAlign = Paint.Align.CENTER }
            c.drawText(pct, cx, cy + 20f, pctPaint)
        }

        // Pied : date de l'objectif.
        val date = when (goal.kind) {
            GoalKind.COUNTDOWN -> goal.targetDate?.let { res.getString(R.string.goal_date_countdown, it.format(DateFormats.long)) }
            GoalKind.SINCE -> res.getString(R.string.goal_date_since, goal.startDate.format(DateFormats.long))
        }
        date?.let { c.drawText(it, pad, H - 110f, text(44f, alpha = 200)) }
        return bmp
    }
}

/** Nom de fichier sûr à partir d'un titre. */
internal object GoalShareNames {
    fun safe(title: String): String =
        title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(40).ifBlank { "objectif" }
}
