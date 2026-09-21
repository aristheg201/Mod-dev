package io.github.aristheg201.svhub.native.game.tft

import io.github.aristheg201.svhub.engine.*

/** Validates every graph and scheduled runtime reference before a definition can become visible to a match. */
internal object TftEffectValidator {
    fun validate(set: TftSetDefinition) {
        val encounterIds = set.pveRounds.map { it.round }.toSet()
        set.roundSchedule.forEach { round ->
            when (round.type) {
                "pve", "boss" -> {
                    val reference = round.pve ?: round.label
                    require(reference in encounterIds) {
                        "round ${round.label}: ${round.type} encounter is not authored: $reference"
                    }
                }
                else -> require(round.pve == null) {
                    "round ${round.label}: only pve/boss rounds may reference a PvE encounter"
                }
            }
        }

        val runtime = BattleRuntime(BattleBoard(1, 1, false, emptySet()),
            BattleRuntime.Limits(12, 4096, 1024, 2048, 128), 0L,
            { _, _, _, _, _ -> error("Validation cannot spawn units") }, set.effectGraphs)
        val unitIds = set.units.map { it.id }.toSet()
        val itemIds = (set.components.map { it.id } + set.fullItems.map { "full:${it.id}" }).toSet()
        fun refs(graph: List<EffectDefinition>, field: String) {
            graph.forEachIndexed { index, node ->
                val path = "$field[$index]"
                node.strings()["definition"]?.let {
                    require(it in unitIds) { "$path.definition: unknown unit $it" }
                }
                if (node.op() in setOf("grant_item", "remove_item")) {
                    require(node.text("id", "") in itemIds) { "$path.id: unknown item ${node.text("id", "")}" }
                }
                refs(node.children(), "$path.children")
                refs(node.otherwise(), "$path.otherwise")
            }
        }
        fun graph(nodes: List<EffectDefinition>, field: String) {
            runtime.validate(nodes, field)
            refs(nodes, field)
        }
        fun triggers(nodes: List<TriggerDefinition>, field: String) {
            require(nodes.map { it.id() }.distinct().size == nodes.size) { "$field: duplicate trigger id" }
            nodes.forEach {
                runtime.validate(it, "$field.${it.id()}")
                refs(it.effects(), "$field.${it.id()}.effects")
            }
        }
        set.effectGraphs.forEach { (id, nodes) -> graph(nodes, "set ${set.id}.effectGraphs.$id") }
        set.units.forEach {
            require(it.ability.castDelayMs in 0..60_000) { "unit ${it.id}.ability.castDelayMs: out of bounds" }
            graph(it.ability.graph.ifEmpty { TftAbilityGraphCompiler.legacy(it.ability, 1, 1.0, 1.0) }, "unit ${it.id}.ability.graph")
            triggers(it.triggers, "unit ${it.id}.triggers")
        }
        set.traits.forEach { trait -> trait.tiers.forEach { tier ->
            triggers(tier.triggers, "trait ${trait.id}.tiers.${tier.threshold}.triggers")
            triggers(tier.teamTriggers, "trait ${trait.id}.tiers.${tier.threshold}.teamTriggers")
        } }
        set.components.forEach { triggers(it.triggers, "component ${it.id}.triggers") }
        set.fullItems.forEach { triggers(it.triggers, "item ${it.id}.triggers") }
        set.augments.forEach { triggers(it.triggers, "augment ${it.id}.triggers") }
    }
}
