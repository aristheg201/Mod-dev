package io.github.aristheg201.svhub.api

import io.github.aristheg201.svhub.content.HubActionSpec
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.ConcurrentHashMap

data class HubIntegrationDescriptor(
    val id: String,
    val displayName: String,
    val capabilities: Set<String> = emptySet(),
    val requiredMods: Set<String> = emptySet()
)

fun interface ServerActionHandler {
    /** Return true when the action was accepted/handled. */
    fun execute(player: ServerPlayer, action: HubActionSpec): Boolean
}

/**
 * Stable common extension surface for third-party mods. Registrations are additive
 * and duplicate IDs are rejected unless the descriptor is identical.
 */
object SVHubApi {
    private val idPattern = Regex("^[a-z0-9_.:-]{1,128}$")
    private val integrations = ConcurrentHashMap<String, HubIntegrationDescriptor>()
    private val actionHandlers = ConcurrentHashMap<String, ServerActionHandler>()

    @JvmStatic
    fun registerIntegration(descriptor: HubIntegrationDescriptor) {
        require(idPattern.matches(descriptor.id)) { "Invalid SVHub integration id '${descriptor.id}'" }
        val previous = integrations.putIfAbsent(descriptor.id, descriptor)
        require(previous == null || previous == descriptor) { "SVHub integration '${descriptor.id}' is already registered" }
    }

    @JvmStatic
    fun registerServerActionType(type: String, handler: ServerActionHandler) {
        require(idPattern.matches(type)) { "Invalid SVHub action type '$type'" }
        require(type !in CORE_ACTION_TYPES) { "'$type' is a core SVHub action type and cannot be replaced" }
        val previous = actionHandlers.putIfAbsent(type, handler)
        require(previous == null || previous === handler) { "SVHub action type '$type' is already registered" }
    }

    fun integrations(): List<HubIntegrationDescriptor> = integrations.values.sortedBy { it.id }
    fun actionTypes(): Set<String> = actionHandlers.keys.toSet()
    fun dispatchCustomAction(player: ServerPlayer, action: HubActionSpec): Boolean = actionHandlers[action.type]?.execute(player, action) ?: false

    fun advertisedCapabilities(): Set<String> = buildSet {
        integrations().forEach { integration ->
            add("integration:${integration.id}")
            integration.capabilities.forEach { capability -> add("integration:${integration.id}:$capability") }
        }
    }

    val CORE_ACTION_TYPES: Set<String> = setOf("open_page", "run_command", "copy_text", "open_url", "close", "back")
}
