package io.github.aristheg201.svarcade.client.cobblemon

import com.cobblemon.mod.common.api.drop.ItemDropEntry
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

data class PokemonInfo(
    val abilities: List<String>,
    val hiddenAbilities: List<String>,
    val catchRate: Int,
    val eggGroups: List<String>,
    val evYield: List<String>,
    val baseExperience: Int,
    val baseFriendship: Int,
    val experienceGroup: String,
    val gender: String,
    val bst: Int,
    val preEvolution: String?,
    val evolutions: List<String>,
    val forms: List<String>,
    val levelMoves: List<String>,
    val tmMoveCount: Int,
    val eggMoveCount: Int,
    val tutorMoveCount: Int,
    val otherMoveCount: Int,
    val drops: List<String>,
    val pokedex: List<String>
)

object PokemonInfoProvider {
    private val cache = ConcurrentHashMap<String, PokemonInfo>()

    fun resolve(view: PokemonView): PokemonInfo? {
        val key = "${view.speciesId}|${view.aspects.sorted().joinToString(",")}" 
        return cache[key] ?: build(view)?.also { cache[key] = it }
    }

    fun clear() = cache.clear()

    private fun build(view: PokemonView): PokemonInfo? {
        val id = ResourceLocation.tryParse(view.speciesId) ?: return null
        val species = PokemonSpecies.getByIdentifier(id) ?: return null
        val form = species.getForm(view.aspects)

        val commonAbilities = mutableListOf<String>()
        val hiddenAbilities = mutableListOf<String>()
        form.abilities.toList().forEach { ability ->
            val name = translated(ability.template.displayName, pretty(ability.template.name))
            if (ability is HiddenAbility) hiddenAbilities += name else commonAbilities += name
        }

        val evYield = form.evYield.entries
            .filter { it.value > 0 }
            .sortedBy { it.key.showdownId }
            .map { (stat, value) -> "${stat.displayName.string} +$value" }

        val levelMoves = form.moves.levelUpMoves.entries
            .sortedBy { it.key }
            .flatMap { (level, moves) -> moves.map { move -> "Lv.$level ${move.displayName.string}" } }
            .distinct()

        val otherMoveCount = form.moves.specialMoves.size + form.moves.legacyMoves.size +
            form.moves.evolutionMoves.size + form.moves.formChangeMoves.size

        val drops = form.drops.entries
            .filterIsInstance<ItemDropEntry>()
            .map { entry ->
                val chance = if (entry.percentage >= 100f) "100%" else "${trim(entry.percentage)}%"
                "${entry.item} ×${entry.quantity} · $chance"
            }
            .distinct()

        val evolutions = form.evolutions.mapNotNull { evolution ->
            val raw = evolution.result.species ?: return@mapNotNull null
            speciesName(raw)
        }.distinct()

        val forms = species.forms
            .mapNotNull { candidate ->
                val aspects = candidate.aspects.filter(String::isNotBlank)
                when {
                    candidate.name.isNotBlank() && !candidate.name.equals("Normal", true) ->
                        if (aspects.isEmpty()) candidate.name else "${candidate.name} (${aspects.joinToString(", ")})"
                    aspects.isNotEmpty() -> aspects.joinToString(", ") { pretty(it) }
                    else -> null
                }
            }
            .distinct()

        val pokedex = form.pokedex.map { line -> translated(line, line) }
            .filter { it.isNotBlank() }
            .distinct()

        val maleRatio = form.maleRatio
        val gender = when {
            maleRatio < 0f -> "Genderless"
            maleRatio <= 0f -> "100% Female"
            maleRatio >= 1f -> "100% Male"
            else -> "${(maleRatio * 100).roundToInt()}% Male / ${((1f - maleRatio) * 100).roundToInt()}% Female"
        }

        return PokemonInfo(
            abilities = commonAbilities.distinct(),
            hiddenAbilities = hiddenAbilities.distinct(),
            catchRate = form.catchRate,
            eggGroups = form.eggGroups.map { pretty(it.toString()) }.sorted(),
            evYield = evYield,
            baseExperience = form.baseExperienceYield,
            baseFriendship = form.baseFriendship,
            experienceGroup = pretty(form.experienceGroup.name),
            gender = gender,
            bst = form.baseStats.values.sum(),
            preEvolution = form.preEvolution?.species?.translatedName?.string,
            evolutions = evolutions,
            forms = forms,
            levelMoves = levelMoves,
            tmMoveCount = form.moves.tmMoves.size,
            eggMoveCount = form.moves.eggMoves.size,
            tutorMoveCount = form.moves.tutorMoves.size,
            otherMoveCount = otherMoveCount,
            drops = drops,
            pokedex = pokedex
        )
    }

    private fun speciesName(raw: String): String {
        val id = if (':' in raw) {
            ResourceLocation.tryParse(raw)
        } else {
            ResourceLocation.fromNamespaceAndPath("cobblemon", raw.lowercase())
        }
        return id?.let(PokemonSpecies::getByIdentifier)?.translatedName?.string ?: pretty(raw)
    }

    private fun translated(key: String, fallback: String): String {
        if (key.isBlank()) return fallback
        val value = Component.translatable(key).string
        return if (value == key) fallback else value
    }

    private fun trim(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

    private fun pretty(value: String): String = value
        .substringAfterLast(':')
        .replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
