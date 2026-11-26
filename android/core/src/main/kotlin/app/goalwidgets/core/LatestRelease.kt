package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Dernière version publiée par la CI (GitHub Release `build-<versionCode>`, un APK en pièce jointe),
 * lue depuis `GET /repos/{owner}/{repo}/releases/latest`.
 */
data class LatestRelease(
    val versionCode: Long,
    val title: String,
    val apkUrl: String,
    val apkSize: Long,
) {
    /** Mise à jour nécessaire : version publiée strictement supérieure à la version installée. */
    fun isNewerThan(installedVersionCode: Long): Boolean = versionCode > installedVersionCode

    companion object {
        /** `null` si la réponse n'est pas une release `build-<n>` avec un APK téléchargeable. */
        fun parse(json: String): LatestRelease? {
            val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
            val tag = root.string("tag_name") ?: return null
            val versionCode = tag.removePrefix(TAG_PREFIX).toLongOrNull() ?: return null
            val apk = runCatching { root["assets"]!!.jsonArray }.getOrNull()
                ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                ?.firstOrNull { it.string("name")?.endsWith(".apk") == true }
                ?: return null
            val url = apk.string("browser_download_url")?.takeIf { it.startsWith("https://") } ?: return null
            val size = runCatching { apk["size"]?.jsonPrimitive?.longOrNull }.getOrNull()?.takeIf { it > 0 } ?: return null
            return LatestRelease(
                versionCode = versionCode,
                title = root.string("name") ?: tag,
                apkUrl = url,
                apkSize = size,
            )
        }

        private const val TAG_PREFIX = "build-"

        private fun JsonObject.string(key: String): String? =
            runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
    }
}
