package io.github.aristheg201.svhub.client.nativeui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
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
import net.minecraft.resources.ResourceLocation

/**
 * Headless/CI visual smoke harness for the embedded TFT scene.
 *
 * The smoke scene intentionally contains real Cobblemon actors on both board and
 * bench. A screenshot without resolved actors is a failed smoke test, not a pass.
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
        val resolvedActors = PokemonModelRenderer.sceneSizingDiagnostics()
            .count { it.instanceId.startsWith("visual:$arenaId:") }
        if (!capturedCurrent && stableTicks >= 70 && resolvedActors >= active.expectedActors) {
            capturedCurrent = true
            val fileName = "svhub-tft-$arenaId.png"
            Screenshot.grab(client.gameDirectory, fileName, client.mainRenderTarget) { message ->
                System.out.println("[SVHub Visual Smoke] captured $fileName with $resolvedActors actors :: ${message.string}")
            }
        }

        if (!capturedCurrent && stableTicks > 240) {
            throw IllegalStateException(
                "SVHub visual smoke timed out waiting for Cobblemon actors in $arenaId: " +
                    "$resolvedActors/${active.expectedActors} resolved"
            )
        }

        if (capturedCurrent && stableTicks >= 95) {
            arenaIndex++
            stableTicks = 0
            capturedCurrent = false
        }
    }

    private class ArenaVisualSmokeScreen(val arenaId: String) : Screen(Component.literal("SVHub Visual Smoke")) {
        private val scene = PokemonSceneState()
        private var cachedEntities: List<PokemonSceneEntity>? = null
        val expectedActors get() = cachedEntities?.size ?: 0

        override fun isPauseScreen(): Boolean = false

        override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val arena = MinecraftArenaRegistry.definition(arenaId)
            if (arena == null) {
                gui.fill(0, 0, width, height, 0xFF300000.toInt())
                gui.drawCenteredString(font, "Missing arena: $arenaId", width / 2, height / 2, 0xFFFFFFFF.toInt())
                return
            }

            val entities = cachedEntities ?: smokeEntities(arena).also { cachedEntities = it }
            gui.fill(0, 0, width, height, arena.backgroundColor)
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
                entities = entities,
                state = scene,
                teamSplitRow = arena.boardRows / 2,
                camera = framed,
                arenaId = arenaId,
                arenaSeed = "visual-smoke:$arenaId"
            )

            gui.fill(6, 6, 250, 25, 0xC0000000.toInt())
            gui.drawString(
                font,
                "SVHub TFT · $arenaId · actors ${PokemonModelRenderer.sceneSizingDiagnostics().count { it.instanceId.startsWith("visual:$arenaId:") }}/${entities.size}",
                11,
                11,
                0xFFFFFFFF.toInt(),
                true
            )
        }

        private fun smokeEntities(arena: MinecraftArenaDefinition): List<PokemonSceneEntity> {
            val boardSpecies = listOf(
                "cobblemon:bulbasaur",
                "cobblemon:pikachu",
                "cobblemon:gengar",
                "cobblemon:machamp",
                "cobblemon:charmander",
                "cobblemon:snorlax",
                "cobblemon:onix",
                "cobblemon:vaporeon"
            )
            val rows = listOf(1, 1, 2, 2, arena.boardRows - 3, arena.boardRows - 3, arena.boardRows - 2, arena.boardRows - 2)
            val cols = listOf(1, arena.boardColumns - 2, 2, arena.boardColumns - 3, 1, arena.boardColumns - 2, 2, arena.boardColumns - 3)
            val board = boardSpecies.mapIndexedNotNull { index, speciesId ->
                val cell = rows[index].coerceIn(0, arena.boardRows - 1) * arena.boardColumns +
                    cols[index].coerceIn(0, arena.boardColumns - 1)
                val anchor = arena.boardAnchor(cell)
                val view = pokemonView(speciesId) ?: return@mapIndexedNotNull null
                val team = if (cell / arena.boardColumns < arena.boardRows / 2) 1 else 0
                PokemonSceneEntity(
                    id = "visual:$arenaId:board:$index",
                    view = view,
                    label = view.displayName,
                    boardX = anchor.x,
                    boardY = anchor.y,
                    elevation = anchor.z,
                    team = team,
                    yaw = if (team == 0) 180f else 0f,
                    hp = 82,
                    maxHp = 100,
                    mana = 38,
                    maxMana = 100,
                    star = if (index == 1 || index == 5) 3 else 1
                )
            }

            val benchSpecies = listOf("cobblemon:eevee", "cobblemon:lucario", "cobblemon:charizard", "cobblemon:lapras")
            val bench = benchSpecies.mapIndexedNotNull { index, speciesId ->
                val anchor = arena.benchAnchor(index + 2)
                val view = pokemonView(speciesId) ?: return@mapIndexedNotNull null
                PokemonSceneEntity(
                    id = "visual:$arenaId:bench:$index",
                    view = view,
                    label = view.displayName,
                    boardX = anchor.x,
                    boardY = anchor.y,
                    elevation = anchor.z,
                    yaw = 180f,
                    scale = .65f,
                    star = 1
                )
            }
            return board + bench
        }

        private fun pokemonView(speciesId: String): PokemonView? {
            val id = ResourceLocation.tryParse(speciesId) ?: return null
            val species = PokemonSpecies.getByIdentifier(id) ?: return null
            return PokemonView(
                key = speciesId,
                route = "",
                speciesId = speciesId,
                aspects = emptySet(),
                displayName = species.translatedName.string,
                dexNumber = species.nationalPokedexNumber,
                fakemon = false
            )
        }
    }
}
