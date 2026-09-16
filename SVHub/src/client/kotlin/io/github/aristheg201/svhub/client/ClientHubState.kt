package io.github.aristheg201.svhub.client

import io.github.aristheg201.svhub.client.api.SVHubClientApi
import io.github.aristheg201.svhub.client.cobblemon.CobblemonWikiProvider
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.render.GeneratedBackgroundRenderer
import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.HubContentCodec
import io.github.aristheg201.svhub.content.HubValidator
import io.github.aristheg201.svhub.network.ChunkAssembler
import io.github.aristheg201.svhub.search.SearchHit
import io.github.aristheg201.svhub.search.SearchIndex
import io.github.aristheg201.svhub.util.Compression
import net.fabricmc.loader.api.FabricLoader

object ClientHubState {
    @Volatile var playerContent: HubContent? = null
        private set
    @Volatile var editorContent: HubContent? = null
        private set
    @Volatile var serverRevision: Long = -1L
    @Volatile var canOpen: Boolean = false
    @Volatile var canEdit: Boolean = false
    @Volatile var serverManifest: String = "{}"
    @Volatile var lastEditorMessage: String? = null

    private val playerAssembler = ChunkAssembler()
    private val editorAssembler = ChunkAssembler()
    private var searchIndex: SearchIndex? = null

    fun loadCachedIfRevision(revision: Long): Long {
        val cached = ClientCache.load() ?: return -1L
        if (cached.revision != revision) return -1L
        applyPlayer(cached)
        return cached.revision
    }

    fun acceptSnapshotChunk(transferId: Long, editor: Boolean, index: Int, total: Int, chunk: String): String? {
        val assembler = if (editor) editorAssembler else playerAssembler
        return assembler.accept(transferId, index, total, chunk)
    }

    fun decodeSnapshot(encoded: String): HubContent {
        val decoded = HubContentCodec.decode(Compression.decodeUtf8(encoded))
        HubValidator.validate(decoded).requireValid()
        return decoded
    }

    /** Must be called on the Minecraft client thread. */
    fun applySnapshot(content: HubContent, revision: Long, editor: Boolean): Boolean {
        if (content.revision != revision || revision < serverRevision) return false
        if (editor) editorContent = content else applyPlayer(content)
        serverRevision = revision
        return true
    }

    fun applyEditor(content: HubContent) {
        HubValidator.validate(content).requireValid()
        editorContent = content
    }

    private fun applyPlayer(content: HubContent) {
        val clientMods = FabricLoader.getInstance().allMods.map { it.metadata.id }.toSet()
        val filtered = content.copy(pages = content.pages.mapNotNull { page ->
            if (page.visibility.clientMod != null && page.visibility.clientMod !in clientMods) return@mapNotNull null
            page.copy(components = page.components.filter { it.visibility.clientMod == null || it.visibility.clientMod in clientMods })
        })
        playerContent = filtered
        searchIndex = SearchIndex.build(filtered)
        ClientCache.save(filtered)
    }

    fun search(query: String, limit: Int = 30): List<SearchHit> {
        val content = playerContent ?: return emptyList()
        val hub = searchIndex?.search(query, limit) ?: emptyList()
        val pokemon = CobblemonWikiProvider.search(query, content, limit).map {
            SearchHit(it.key, it.route, it.displayName, it.speciesId, if (it.fakemon) "fakemon" else "pokemon", it.score, "cobblemon")
        }
        val mods = EnvironmentViewProvider.search(query, limit)
        val commands = CommandViewProvider.search(query, limit)
        val extensions = SVHubClientApi.search(query, content, limit)
        return (hub + pokemon + mods + commands + extensions)
            .sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title })
            .take(limit)
    }

    fun reset() {
        playerContent = null
        editorContent = null
        serverRevision = -1L
        canOpen = false
        canEdit = false
        serverManifest = "{}"
        lastEditorMessage = null
        searchIndex = null
        playerAssembler.clear()
        editorAssembler.clear()
        CobblemonWikiProvider.clearCaches()
        PokemonModelRenderer.clear()
        GeneratedBackgroundRenderer.clear()
    }
}
