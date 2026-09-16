package io.github.aristheg201.svhub.search

import io.github.aristheg201.svhub.content.HubContent
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

data class SearchHit(
    val id: String,
    val route: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val score: Int,
    val source: String = "hub"
)

class SearchIndex private constructor(private val docs: List<SearchDocument>) {
    data class SearchDocument(
        val id: String,
        val route: String,
        val title: String,
        val subtitle: String,
        val category: String,
        val normalizedTitle: String,
        val normalizedBody: String,
        val normalizedTags: List<String>
    )

    fun search(query: String, limit: Int = 30): List<SearchHit> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()
        val tokens = q.split(' ').filter(String::isNotBlank)
        return docs.asSequence()
            .mapNotNull { doc ->
                var score = 0
                if (doc.normalizedTitle == q) score += 250
                if (doc.normalizedTitle.startsWith(q)) score += 160
                if (doc.normalizedTitle.contains(q)) score += 90
                if (doc.normalizedTags.any { it == q }) score += 120
                if (doc.normalizedTags.any { it.startsWith(q) }) score += 75
                if (doc.normalizedBody.contains(q)) score += 35
                tokens.forEach { token ->
                    if (doc.normalizedTitle.contains(token)) score += 30
                    if (doc.normalizedTags.any { it.contains(token) }) score += 20
                    if (doc.normalizedBody.contains(token)) score += 8
                }
                if (score <= 0) null else SearchHit(doc.id, doc.route, doc.title, doc.subtitle, doc.category, score)
            }
            .sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { stripMiniMessage(it.title).lowercase(Locale.ROOT) })
            .take(max(1, limit.coerceAtMost(100)))
            .toList()
    }

    companion object {
        fun build(content: HubContent, locale: String = content.defaultLocale): SearchIndex {
            val docs = content.pages.map { page ->
                val title = page.title.resolve(locale, content.defaultLocale)
                val subtitle = page.subtitle.resolve(locale, content.defaultLocale)
                val body = page.components.joinToString(" ") { component ->
                    component.props.entrySet().joinToString(" ") { (_, value) ->
                        if (value.isJsonPrimitive) value.asString else value.toString()
                    }
                }
                SearchDocument(
                    id = page.id,
                    route = page.route,
                    title = title,
                    subtitle = subtitle,
                    category = page.category,
                    normalizedTitle = normalize(title),
                    normalizedBody = normalize(body),
                    normalizedTags = page.tags.map(::normalize)
                )
            }
            return SearchIndex(docs)
        }

        fun normalize(input: String): String = Normalizer.normalize(stripMiniMessage(input), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9:_/.-]+"), " ")
            .trim()

        /** Strip only recognized formatting tags; literal command args like <tên> survive. */
        fun stripMiniMessage(input: String): String = input
            .replace(SAFE_TAG, "")
            .replace("\\<", "<")
            .replace("\\\\", "\\")

        private val SAFE_TAG = Regex(
            "</?(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|dark_gray|dark_grey|blue|green|aqua|red|light_purple|yellow|white|color|colour|bold|b|italic|i|em|underlined|underline|u|strikethrough|st|obfuscated|obf|gradient|rainbow|reset|newline|br)(?::[^>]*)?>|</?#[0-9a-fA-F]{3,8}>",
            RegexOption.IGNORE_CASE
        )
    }
}
