package io.github.aristheg201.svarcade.native.game.tft

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import net.minecraft.resources.ResourceLocation

data class PokemonPresentationReport(
    val canonicalSpecies: String,
    val providerCanonicalForm: String,
    val effectiveAspects: Set<String>,
    val cosmeticAspects: Set<String>,
    val features: Map<String, String>,
    val shiny: Boolean,
    val gender: String?,
    val scale: Double,
    val providerFormAspects: Set<String>,
    val resolverOutcome: String,
    val rejectionReason: String? = null
) {
    fun describe(): String = buildString {
        append("species=").append(canonicalSpecies)
        append(" form=").append(providerCanonicalForm)
        append(" aspects=").append(effectiveAspects.sorted())
        append(" cosmetic=").append(cosmeticAspects.sorted())
        append(" features=").append(features.toSortedMap())
        append(" shiny=").append(shiny).append(" gender=").append(gender ?: "provider-default")
        append(" scale=").append(scale).append(" providerFormAspects=").append(providerFormAspects.sorted())
        append(" providerVisuals=UNOBSERVABLE(server command source has no client render repository; request client preview for model/poser/texture/layers)")
        append(" outcome=").append(resolverOutcome)
        rejectionReason?.let { append(" reason=").append(it) }
    }
}

object PokemonPresentationDiagnostics {
    fun resolve(identity: PokemonPresentationIdentity): PokemonPresentationReport {
        val id = ResourceLocation.tryParse(identity.species)
            ?: return rejected(identity, "invalid species resource location")
        val species = PokemonSpecies.getByIdentifier(id)
            ?: return rejected(identity, "species is absent from the Cobblemon runtime registry")
        val requested = identity.resolverAspects()
        val form = runCatching { species.getForm(requested) }.getOrElse {
            return rejected(identity, "provider form resolver rejected aspects: ${it.message}")
        }
        val providerAspects = form.aspects.filter(String::isNotBlank).toSet()
        val canonicalForm = form.name.ifBlank { "Normal" }
        val requestedForm = identity.form?.lowercase()
        if (requestedForm != null && requestedForm !in providerAspects.map(String::lowercase) &&
            !canonicalForm.equals(identity.form, ignoreCase = true)
        ) return rejected(identity, "requested form '${identity.form}' resolved to '$canonicalForm' / $providerAspects")
        return PokemonPresentationReport(identity.species, canonicalForm, requested, identity.cosmeticAspects,
            identity.features, identity.shiny, identity.gender, identity.scale, providerAspects, "resolved")
    }

    private fun rejected(identity: PokemonPresentationIdentity, reason: String) = PokemonPresentationReport(
        identity.species, identity.form ?: "Normal", identity.resolverAspects(), identity.cosmeticAspects,
        identity.features, identity.shiny, identity.gender, identity.scale, emptySet(), "rejected", reason
    )
}
