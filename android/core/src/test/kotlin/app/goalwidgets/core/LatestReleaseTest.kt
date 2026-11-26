package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LatestReleaseTest {
    /** Extrait réduit d'une réponse de `GET /repos/{owner}/{repo}/releases/latest`. */
    private fun release(tag: String, vararg assets: String) = """
        {
          "tag_name": "$tag",
          "name": "Goal Widgets 1.42 (abc1234)",
          "draft": false,
          "prerelease": false,
          "assets": [${assets.joinToString(",")}]
        }
    """.trimIndent()

    private fun asset(name: String, size: Long = 6_543_210) = """
        {
          "name": "$name",
          "size": $size,
          "content_type": "application/vnd.android.package-archive",
          "browser_download_url": "https://github.com/maxg56/goal-widgets/releases/download/build-1042/$name"
        }
    """.trimIndent()

    @Test
    fun `release build-n with an apk asset gives version n, download url and size`() {
        val parsed = LatestRelease.parse(release("build-1042", asset("goal-widgets-1042.apk")))

        assertEquals(
            LatestRelease(
                versionCode = 1042,
                title = "Goal Widgets 1.42 (abc1234)",
                apkUrl = "https://github.com/maxg56/goal-widgets/releases/download/build-1042/goal-widgets-1042.apk",
                apkSize = 6_543_210,
            ),
            parsed,
        )
    }

    @Test
    fun `tag without a build number is not an update`() {
        assertNull(LatestRelease.parse(release("build-", asset("goal-widgets.apk"))))
        assertNull(LatestRelease.parse(release("build-abc", asset("goal-widgets.apk"))))
        assertNull(LatestRelease.parse(release("v1.2", asset("goal-widgets.apk"))))
    }

    @Test
    fun `release without an apk asset is not an update`() {
        assertNull(LatestRelease.parse(release("build-1042")))
        assertNull(LatestRelease.parse(release("build-1042", asset("notes.txt"))))
    }

    @Test
    fun `the apk asset is chosen among other assets`() {
        val parsed = LatestRelease.parse(release("build-7", asset("notes.txt", 10), asset("goal-widgets-7.apk", 99)))

        assertEquals(99L, parsed?.apkSize)
        assertEquals(true, parsed?.apkUrl?.endsWith("/goal-widgets-7.apk"))
    }

    @Test
    fun `malformed or unexpected json is not an update`() {
        assertNull(LatestRelease.parse("not json"))
        assertNull(LatestRelease.parse("""{"message": "API rate limit exceeded"}"""))
        assertNull(LatestRelease.parse("[]"))
    }

    private val v1042 = LatestRelease(1042, "Goal Widgets 1.42", "https://example.invalid/a.apk", 1)

    @Test
    fun `a higher remote version is an update`() {
        assertEquals(true, v1042.isNewerThan(installedVersionCode = 1))
        assertEquals(true, v1042.isNewerThan(installedVersionCode = 1041))
    }

    @Test
    fun `same or lower remote version is not an update`() {
        assertEquals(false, v1042.isNewerThan(installedVersionCode = 1042))
        assertEquals(false, v1042.isNewerThan(installedVersionCode = 2000))
    }

    @Test
    fun `apk asset that cannot be downloaded safely is not an update`() {
        assertNull(LatestRelease.parse(release("build-1042", asset("goal-widgets-1042.apk", size = 0))))
        val plainHttp = release("build-1042", asset("goal-widgets-1042.apk")).replace("https://", "http://")
        assertNull(LatestRelease.parse(plainHttp))
    }
}
