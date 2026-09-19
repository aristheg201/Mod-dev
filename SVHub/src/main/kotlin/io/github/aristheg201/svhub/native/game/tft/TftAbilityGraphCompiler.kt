package io.github.aristheg201.svhub.native.game.tft

import io.github.aristheg201.svhub.engine.EffectDefinition

/** Explicit adapter for version-one ability fields. New content supplies graphs directly. */
internal object TftAbilityGraphCompiler {
    fun legacy(ability: TftAbilityDefinition, star: Int, ap: Double, overtime: Double): List<EffectDefinition> {
        val starScale = listOf(1.0, 1.45, 2.20)[star.coerceIn(1, 3) - 1]
        val scale = starScale * ap
        val payload = mutableListOf<EffectDefinition>()
        if (ability.damage > 0) payload += node("damage", values = mapOf("amount" to ability.damage * scale * overtime), strings = mapOf("type" to ability.damageType))
        if (ability.stunMs > 0) payload += node("add_status", values = mapOf("duration_ms" to ability.stunMs.toDouble()), strings = mapOf("id" to "stun"))
        val effects = mutableListOf<EffectDefinition>()
        if (ability.dash > 0) effects += node("repeat", values = mapOf("count" to ability.dash.toDouble()), children = listOf(node("dash", "self")))
        if (ability.radius > 0 && payload.isNotEmpty()) effects += node("area_effect", "units_in_radius", mapOf("radius" to ability.radius.toDouble()), mapOf("center" to "target", "relation" to "enemy"), payload)
        else effects += payload
        if (ability.heal > 0) effects += node("heal", values = mapOf("amount" to ability.heal * scale))
        if (ability.shield > 0) effects += node("shield", values = mapOf("amount" to ability.shield * scale))
        ability.effects["execute_below_pct"]?.let { effects += node("execute", values = mapOf("threshold" to it)) }
        val selector = mapOf("current" to "current_target", "lowest_hp_enemy" to "lowest_percent_hp")[ability.target] ?: ability.target
        return listOf(node("targeted_effect", selector, children = effects))
    }

    private fun node(op: String, selector: String = "current_target", values: Map<String, Double> = emptyMap(),
                     strings: Map<String, String> = emptyMap(), children: List<EffectDefinition> = emptyList()) =
        EffectDefinition(op, selector, values, strings, children, emptyList())
}
