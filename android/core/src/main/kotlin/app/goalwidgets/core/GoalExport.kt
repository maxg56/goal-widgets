package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Couleur d'un objectif : palette nommée commune Android/iOS (chaque app choisit ses teintes
 * claires/sombres). `wire` est la valeur du format d'export.
 */
enum class GoalColor(val wire: String) {
    RED("red"),
    ORANGE("orange"),
    YELLOW("yellow"),
    GREEN("green"),
    TEAL("teal"),
    BLUE("blue"),
    INDIGO("indigo"),
    PURPLE("purple"),
    PINK("pink"),
    GRAY("gray");

    companion object {
        /** `null` pour une valeur absente ou inconnue (couleur ajoutée par une version future). */
        fun fromWire(value: String?): GoalColor? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Un objectif tel qu'il circule dans un fichier d'export (format `goal-widgets-export` v1,
 * cf. `docs/export-format.md`). [id] est un UUID en minuscules, stable d'un appareil à l'autre :
 * c'est lui qui évite les doublons à l'import.
 */
data class ExportedGoal(
    val id: String,
    val goal: Goal,
    val color: GoalColor? = null,
    val emoji: String? = null,
    /** `null` = non précisé par le fichier (l'app applique sa valeur par défaut). */
    val notificationsEnabled: Boolean? = null,
    val createdAt: Instant,
)

/** Raison pour laquelle un fichier est refusé en entier (aucun objectif n'est importé). */
sealed interface ImportError {
    /** Code stable, utilisé par les cas partagés. */
    val code: String

    /** Le fichier n'est pas du JSON. */
    data object NotJson : ImportError { override val code = "notJson" }

    /** JSON valide mais pas un export Goal Widgets (marqueur `format`, `version` ou `goals` absents). */
    data object NotAnExport : ImportError { override val code = "notAnExport" }

    /** Export d'une version plus récente que celle que l'app sait lire. */
    data class UnsupportedVersion(val version: Int) : ImportError { override val code = "unsupportedVersion" }

    /** L'objectif n° [index] (à partir de 0) a un champ [field] absent ou invalide. */
    data class InvalidGoal(val index: Int, val field: String) : ImportError { override val code = "invalidGoal" }

    /** Deux objectifs du fichier ont le même [id]. */
    data class DuplicateId(val id: String) : ImportError { override val code = "duplicateId" }
}

/** Lecture/écriture du format d'export versionné. */
object GoalExport {
    const val FORMAT = "goal-widgets-export"
    const val VERSION = 1

    sealed interface Decoded {
        data class Success(val goals: List<ExportedGoal>) : Decoded
        data class Failure(val error: ImportError) : Decoded
    }

    private val json = Json { prettyPrint = true }

    /** Écrit [goals] (dans cet ordre) ; [exportedAt] est informatif. */
    fun encode(goals: List<ExportedGoal>, exportedAt: Instant): String {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", formatInstant(exportedAt))
            put("goals", buildJsonArray { goals.forEach { add(encodeGoal(it)) } })
        }
        return json.encodeToString(JsonElement.serializer(), root) + "\n"
    }

    private fun encodeGoal(g: ExportedGoal) = buildJsonObject {
        put("id", g.id.lowercase(Locale.ROOT))
        put("title", g.goal.title)
        put("kind", g.goal.kind.wire)
        put("startDate", g.goal.startDate.toString())
        put("targetDate", g.goal.targetDate?.toString())
        put("color", g.color?.wire)
        put("emoji", g.emoji)
        put("notificationsEnabled", g.notificationsEnabled)
        put("createdAt", formatInstant(g.createdAt))
    }

    /** Lit un fichier entier ; au moindre objectif invalide, tout le fichier est refusé. */
    fun decode(text: String): Decoded {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            return Decoded.Failure(ImportError.NotJson)
        }
        if (root !is JsonObject) return Decoded.Failure(ImportError.NotAnExport)
        if (root.string("format") != FORMAT) return Decoded.Failure(ImportError.NotAnExport)
        val version = (root["version"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?: return Decoded.Failure(ImportError.NotAnExport)
        if (version < 1) return Decoded.Failure(ImportError.NotAnExport)
        if (version > VERSION) return Decoded.Failure(ImportError.UnsupportedVersion(version))
        val list = root["goals"] as? JsonArray ?: return Decoded.Failure(ImportError.NotAnExport)

        val goals = mutableListOf<ExportedGoal>()
        val seen = mutableSetOf<String>()
        list.forEachIndexed { index, element ->
            val goal = try {
                decodeGoal(element)
            } catch (e: InvalidField) {
                return Decoded.Failure(ImportError.InvalidGoal(index, e.field))
            }
            if (!seen.add(goal.id)) return Decoded.Failure(ImportError.DuplicateId(goal.id))
            goals += goal
        }
        return Decoded.Success(goals)
    }

    private class InvalidField(val field: String) : Exception(field)

    private fun decodeGoal(element: JsonElement): ExportedGoal {
        val o = element as? JsonObject ?: throw InvalidField("goal")
        val id = o.string("id")?.lowercase(Locale.ROOT)?.takeIf { UUID_PATTERN.matches(it) }
            ?: throw InvalidField("id")
        val title = o.string("title")?.trim()?.takeIf { it.isNotEmpty() } ?: throw InvalidField("title")
        val kind = GoalKind.entries.firstOrNull { it.wire == o.string("kind") } ?: throw InvalidField("kind")
        val startDate = o.date("startDate") ?: throw InvalidField("startDate")
        val targetDate = when (kind) {
            GoalKind.COUNTDOWN -> o.date("targetDate") ?: throw InvalidField("targetDate")
            GoalKind.SINCE -> null // ignorée, comme dans l'app
        }
        val notifications = when (val n = o["notificationsEnabled"]) {
            null, JsonNull -> null
            is JsonPrimitive -> n.takeUnless { it.isString }?.booleanOrNull ?: throw InvalidField("notificationsEnabled")
            else -> throw InvalidField("notificationsEnabled")
        }
        val createdAt = o.string("createdAt")?.let(::parseInstant) ?: throw InvalidField("createdAt")
        return ExportedGoal(
            id = id,
            goal = Goal(kind, startDate, targetDate, title),
            color = GoalColor.fromWire(o.string("color")),
            emoji = o.string("emoji")?.trim()?.takeIf { it.isNotEmpty() },
            notificationsEnabled = notifications,
            createdAt = createdAt,
        )
    }

    private val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** Chaîne JSON du champ, `null` si absent, `null` JSON ou pas une chaîne. */
    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** Date `AAAA-MM-JJ` ; `null` si absente ; [InvalidField] si présente mais invalide. */
    private fun JsonObject.date(key: String): LocalDate? {
        val raw = get(key)
        if (raw == null || raw == JsonNull) return null
        val text = (raw as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw InvalidField(key)
        if (!DATE_PATTERN.matches(text)) throw InvalidField(key)
        return try {
            LocalDate.parse(text) // strict : 2026-02-30 est refusé
        } catch (e: DateTimeParseException) {
            throw InvalidField(key)
        }
    }

    /** Instant RFC 3339 : `Z` ou décalage, fractions de seconde facultatives. */
    private fun parseInstant(text: String): Instant? = try {
        OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    } catch (e: DateTimeParseException) {
        null
    }

    /** UTC, millisecondes toujours écrites : `2026-01-01T09:00:00.000Z`. */
    private fun formatInstant(instant: Instant): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
            .format(instant.truncatedTo(ChronoUnit.MILLIS).atOffset(ZoneOffset.UTC))
}

/** Résultat de la fusion d'un import avec les objectifs déjà présents. */
data class ImportPlan(val toAdd: List<ExportedGoal>, val skipped: Int)

object GoalImport {
    /**
     * Objectifs à ajouter : ceux dont l'id (comparé sans casse) n'existe pas encore localement.
     * Un objectif déjà présent est ignoré, jamais écrasé : importer deux fois ne crée aucun doublon.
     */
    fun plan(existingIds: Set<String>, incoming: List<ExportedGoal>): ImportPlan {
        val known = existingIds.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
        val toAdd = incoming.filter { known.add(it.id.lowercase(Locale.ROOT)) }
        return ImportPlan(toAdd, skipped = incoming.size - toAdd.size)
    }
}
