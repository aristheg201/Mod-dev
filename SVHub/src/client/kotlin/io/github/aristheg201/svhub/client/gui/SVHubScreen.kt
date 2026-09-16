package io.github.aristheg201.svhub.client.gui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Base screen for every SVHub UI.
 *
 * Minecraft 1.21.1 Screen.render() calls renderBackground() before rendering
 * widgets. The vanilla implementation applies the menu blur post-process and
 * darkening layer. SVHub already draws its own full-screen pixel background, so
 * allowing the vanilla pass here would blur/darken the Hub itself every frame.
 */
abstract class SVHubScreen(title: Component) : Screen(title) {
    final override fun renderBackground(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Intentionally empty: SVHub screens own their complete background pass.
    }

    final override fun isPauseScreen(): Boolean = false
}
