package io.github.aristheg201.svhub.client

import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import io.github.aristheg201.svhub.search.SearchHit
import io.github.aristheg201.svhub.search.SearchIndex
import net.minecraft.client.Minecraft
import net.minecraft.commands.SharedSuggestionProvider
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class CommandView(
    val name: String,
    val route: String,
    val syntaxes: List<String>,
    val score: Int = 0
)

object CommandViewProvider {
    fun all(): List<CommandView> {
        val dispatcher = Minecraft.getInstance().connection?.commands ?: return emptyList()
        return dispatcher.root.children
            .filter { it is LiteralCommandNode<*> }
            .map {
                @Suppress("UNCHECKED_CAST")
                it as LiteralCommandNode<SharedSuggestionProvider>
            }
            .map { node ->
                val variants = syntaxVariants(node).ifEmpty { listOf("/${node.name}") }
                CommandView(node.name, "command/${enc(node.name)}", variants)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun resolveRoute(route: String): CommandView? {
        val name = URLDecoder.decode(route.removePrefix("command/"), StandardCharsets.UTF_8)
        return all().firstOrNull { it.name == name }
    }

    fun search(query: String, limit: Int): List<SearchHit> {
        val q = SearchIndex.normalize(query)
        if (q.isBlank()) return emptyList()
        return all().mapNotNull { command ->
            val name = SearchIndex.normalize(command.name)
            val syntax = SearchIndex.normalize(command.syntaxes.joinToString(" "))
            var score = 0
            if (name == q) score += 220
            if (name.startsWith(q)) score += 150
            if (name.contains(q)) score += 100
            if (syntax.contains(q)) score += 40
            if (score == 0) null else SearchHit("command:${command.name}", command.route, "/${command.name}", command.syntaxes.firstOrNull().orEmpty(), "command", score, "server-command-tree")
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun syntaxVariants(root: LiteralCommandNode<SharedSuggestionProvider>): List<String> {
        val out = linkedSetOf<String>()
        fun walk(node: CommandNode<SharedSuggestionProvider>, tokens: List<String>, depth: Int) {
            val current = tokens + token(node)
            if (node.command != null || node.children.isEmpty() || depth >= 3) out += "/" + current.filter(String::isNotBlank).joinToString(" ")
            if (depth < 3) node.children.take(12).forEach { child -> walk(child, current, depth + 1) }
        }
        walk(root, emptyList(), 0)
        return out.take(16)
    }

    private fun token(node: CommandNode<SharedSuggestionProvider>): String = when (node) {
        is LiteralCommandNode<*> -> node.literal
        is ArgumentCommandNode<*, *> -> "<${node.name}>"
        else -> node.name
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
