package io.github.aristheg201.svarcade.mixin.client

import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.client.nativeui.TftExclusivePresentation
import io.github.aristheg201.svarcade.client.nativeui.TftGameRenderer
import io.github.aristheg201.svarcade.client.nativeui.TftUiState
import io.github.aristheg201.svarcade.ui.UiDensity
import io.github.aristheg201.svarcade.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** One fail-closed boundary covers both the live screen and visual smoke callers. */
@Mixin(value = [TftGameRenderer::class], remap = false)
abstract class TftPresentationOwnershipMixin {
    @Inject(method = ["render"], at = [At("HEAD")], cancellable = true, require = 1, remap = false)
    private fun svarcadeOwnExclusivePresentation(
        gui: GuiGraphics, font: Font, area: UiRect, density: UiDensity,
        view: JsonObject, ui: TftUiState, mouseX: Int, mouseY: Int,
        hooks: TftGameRenderer.Hooks, sceneOnly: Boolean, callback: CallbackInfo
    ) {
        if (TftExclusivePresentation.renderIfOwned(gui, font, area, density, view, ui, mouseX, mouseY, hooks, sceneOnly)) {
            callback.cancel()
        }
    }
}
