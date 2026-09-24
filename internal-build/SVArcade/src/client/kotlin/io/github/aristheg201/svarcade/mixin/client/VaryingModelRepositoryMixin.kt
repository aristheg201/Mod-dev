package io.github.aristheg201.svarcade.mixin.client

import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import io.github.aristheg201.svarcade.SVArcade
import io.github.aristheg201.svarcade.compat.CobblemonPoseSanitizer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyArg

@Mixin(value = [VaryingModelRepository::class], remap = false)
abstract class VaryingModelRepositoryMixin {
    @ModifyArg(
        method = ["loadJsonPoser"],
        at = At(
            value = "INVOKE",
            target = "Lcom/google/gson/Gson;fromJson(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
            remap = false
        ),
        index = 0,
        require = 1
    )
    private fun svarcadeSanitizePoserJson(json: String): String {
        val result = CobblemonPoseSanitizer.sanitizeWithStats(json)
        if (result.changed) {
            SVArcade.LOGGER.warn(
                "Sanitized malformed Cobblemon poser: animations={}, namedAnimations={}, transitions={}, quirkAnimations={}",
                result.removedAnimations,
                result.removedNamedAnimations,
                result.removedTransitions,
                result.removedQuirkAnimations
            )
        }
        return result.json
    }
}
