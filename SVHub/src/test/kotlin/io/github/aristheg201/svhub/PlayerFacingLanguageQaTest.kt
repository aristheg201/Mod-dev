package io.github.aristheg201.svhub

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertTrue

class PlayerFacingLanguageQaTest {
    private val banned = listOf(
        "server authoritative", "server owns", "provider", "snapshot", "runtime",
        "capability", "definition", "replication", "fallback", "backend",
        "session", "worker", "server restart", "profile"
    )

    @Test
    fun `player facing localization contains no implementation language`() {
        listOf("en_us", "vi_vn").forEach { locale ->
            val path = "assets/svhub/lang/$locale.json"
            val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing $path" }
            val root = stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            val violations = root.entrySet().mapNotNull { (key, value) ->
                val text = value.asString.lowercase()
                banned.firstOrNull { token -> text.contains(token) }
                    ?.let { token -> "$key contains '$token': ${value.asString}" }
            }
            assertTrue(
                violations.isEmpty(),
                "$locale player-facing localization leaked implementation language:\n${violations.joinToString("\n")}"
            )
        }
    }
}
