package io.github.aristheg201.svhub.client.nativeui

import io.github.aristheg201.svhub.ui.SceneCameraFraming
import io.github.aristheg201.svhub.ui.SceneCameras
import io.github.aristheg201.svhub.ui.SceneVec3
import io.github.aristheg201.svhub.ui.UiRect
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Headless/CI visual smoke harness for the embedded TFT scene.
 *
 * This never inserts blocks/entities into ClientLevel. It opens a normal Screen,
 * renders the same retained SVHub scene path used by TFT, captures the main
 * framebuffer, then exits the client so GitHub Actions can inspect the images.
 */
object VisualSmokeHarness {
    private const val ENV = "SVHUB_VISUAL_SMOKE"
    private val arenas = listOf("monster_island", "gotham_rooftops", "sector_2814", "kanto_stadium")

    private var enabled = false
    private var arenaIndex = 0
    private var stableTicks = 0
    private var bootTicks = 0
    private var finalWaitTicks = 0
    private var capturedCurrent = false

    fun register() {
        if (System.getenv(ENV) != "1") return
        enabled = true
        System.out.println("[SVHub Visual Smoke] enabled")
        ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }
    }

    private fun tick(client: Minecraft) {
        if (!enabled) return
        bootTicks++

        if (arenaIndex >= arenas.size) {
            finalWaitTicks++
            if (finalWaitTicks >= 40) {
                System.out.println("[SVHub Visual Smoke] completed ${arenas.size} captures; stopping client")
                enabled = false
                client.stop()
            }
            return
        }

        val arenaId = arenas[arenaIndex]
        val arena = MinecraftArenaRegistry.definition(arenaId)
        if (arena == null) {
            if (bootTicks > 1200) {
                throw IllegalStateException("SVHub visual smoke timed out waiting for arena definition: $arenaId")
            }
            return
        }

        val active = client.screen as? ArenaVisualSmokeScreen
        if (active?.arenaId != arenaId) {
            stableTicks = 0
            capturedCurrent = false
            client.setScreen(ArenaVisualSmokeScreen(arenaId))
            System.out.println("[SVHub Visual Smoke] opened $arenaId")
            return
        }

        stableTicks++
        if (!capturedCurrent && stableTicks >= 30) {
            capturedCurrent = true
            val fileName = "svhub-tft-$arenaId.png"
            Screenshot.grab(client.gameDirectory, fileName, client.mainRenderTarget) { message ->
                System.out.println("[SVHub Visual Smoke] captured $fileName :: ${message.string}")
            }
        }

        if (capturedCurrent && stableTicks >= 50) {
            arenaIndex++
            stableTicks = 0
            capturedCurrent = false
        }
    }

    private class ArenaVisualSmokeScreen(val arenaId: String) : Screen(Component.literal("SVHub Visual Smoke")) {
        private val scene = PokemonSceneState()

        override fun isPauseScreen(): Boolean = false

        override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val arena = MinecraftArenaRegistry.definition(arenaId)
            if (arena == null) {
                gui.fill(0, 0, width, height, 0xFF300000.toInt())
                gui.drawCenteredString(font, "Missing arena: $arenaId", width / 2, height / 2, 0xFFFFFFFF.toInt())
                return
            }

            val area = UiRect(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
            val authored = arena.camera(ArenaCameraRole.PREPARATION, SceneCameras.TFT)
            val framed = SceneCameraFraming.board(
                authored,
                area,
                SceneVec3(arena.boardOrigin.x.toDouble(), arena.boardOrigin.y.toDouble(), arena.boardOrigin.z.toDouble()),
                arena.boardColumns,
                arena.boardRows,
                arena.benchAnchors.map { SceneVec3(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) },
                cellSize = SceneVec3(arena.cellSize.x.toDouble(), arena.cellSize.y.toDouble(), arena.cellSize.z.toDouble())
            )

            PokemonScene3D.render(
                gui = gui,
                font = font,
                area = area,
                columns = arena.boardColumns,
                rows = arena.boardRows,
                entities = emptyList(),
                state = scene,
                camera = framed,
                arenaId = arenaId,
                arenaSeed = "visual-smoke:$arenaId"
            )

            gui.fill(6, 6, 178, 23, 0xB0000000.toInt())
            gui.drawString(font, "SVHub TFT · $arenaId", 11, 11, 0xFFFFFFFF.toInt(), true)
        }
    }
}
