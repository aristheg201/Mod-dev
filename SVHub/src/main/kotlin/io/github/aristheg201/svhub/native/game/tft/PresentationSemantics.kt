package io.github.aristheg201.svhub.native.game.tft

/** Provider-neutral intent. Cobblemon's poser remains responsible for the concrete animation. */
enum class PokemonAnimationSemantic {
    IDLE, MOVE, ATTACK_PHYSICAL, ATTACK_SPECIAL, CAST_STATUS, DASH, HIT, RECOIL,
    FAINT, TRANSFORM, SPAWN, VICTORY, CRY;

    fun candidateLabels(): List<String> = when (this) {
        IDLE -> listOf("idle")
        MOVE -> listOf("walk", "run", "moving")
        ATTACK_PHYSICAL -> listOf("physical", "attack", "bite")
        ATTACK_SPECIAL -> listOf("special", "attack", "shoot")
        CAST_STATUS -> listOf("status", "cast", "cry")
        DASH -> listOf("run", "dash", "walk")
        HIT -> listOf("hit", "recoil")
        RECOIL -> listOf("recoil", "hit")
        FAINT -> listOf("faint", "cry")
        TRANSFORM -> listOf("transform", "evolution", "cry")
        SPAWN -> listOf("send_out", "spawn", "cry")
        VICTORY -> listOf("victory", "cry", "idle")
        CRY -> listOf("cry", "idle")
    }
}

enum class PokemonVfxPhase { CAST, PROJECTILE, TRAVEL, IMPACT, AREA, PERSISTENT }

data class SemanticAnimationResolution(
    val semantic: PokemonAnimationSemantic,
    val selectedLabel: String?,
    val availableLabels: Set<String>,
    val outcome: String
)

object PokemonAnimationResolver {
    fun resolve(semantic: PokemonAnimationSemantic, available: Collection<String>): SemanticAnimationResolution {
        val normalized = available.filter(String::isNotBlank).associateBy { it.lowercase() }
        val selected = semantic.candidateLabels().firstNotNullOfOrNull(normalized::get)
        return SemanticAnimationResolution(
            semantic, selected, normalized.values.toSortedSet(),
            if (selected == null) "poser-default" else "resolved"
        )
    }
}
