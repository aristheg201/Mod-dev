package io.github.aristheg201.svhub.client.cobblemon

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import com.mojang.math.Axis
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.concurrent.ConcurrentHashMap

object PokemonModelRenderer {
    private val stacks = ConcurrentHashMap<String, ItemStack>()

    fun render(gui: GuiGraphics, view: PokemonView, centerX: Int, centerY: Int, size: Int, yaw: Float = 0f, zoom: Float = 1f): Boolean {
        val stack = stack(view) ?: return false
        val scale = (size / 16f) * zoom.coerceIn(0.5f, 2.5f)
        val pose = gui.pose()
        pose.pushPose()
        pose.translate(centerX.toFloat(), centerY.toFloat(), 200f)
        pose.mulPose(Axis.YP.rotationDegrees(yaw))
        pose.scale(scale, scale, scale)
        pose.translate(-8f, -8f, 0f)
        gui.renderItem(stack, 0, 0)
        pose.popPose()
        return true
    }

    private fun stack(view: PokemonView): ItemStack? = stacks.computeIfAbsent("${view.speciesId}|${view.aspects.sorted().joinToString(",")}") {
        val id = ResourceLocation.tryParse(view.speciesId) ?: return@computeIfAbsent ItemStack.EMPTY
        val species = PokemonSpecies.getByIdentifier(id) ?: return@computeIfAbsent ItemStack.EMPTY
        PokemonItem.from(species, view.aspects)
    }.takeUnless { it.isEmpty }

    fun clear() = stacks.clear()
}
