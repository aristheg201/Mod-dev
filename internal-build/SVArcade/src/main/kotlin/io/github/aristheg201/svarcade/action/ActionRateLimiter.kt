package io.github.aristheg201.svarcade.action

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ActionRateLimiter {
    private val lastExecution = ConcurrentHashMap<String, Long>()

    fun allow(player: UUID, actionId: String, cooldownMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val key = "$player:$actionId"
        val last = lastExecution[key] ?: 0L
        if (now - last < cooldownMs) return false
        lastExecution[key] = now
        return true
    }

    fun clear(player: UUID) {
        val prefix = "$player:"
        lastExecution.keys.removeIf { it.startsWith(prefix) }
    }
}
