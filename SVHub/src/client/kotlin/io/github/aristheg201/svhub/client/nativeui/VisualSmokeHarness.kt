package io.github.aristheg201.svhub.client.nativeui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.game.CardDuelSession
import io.github.aristheg201.svhub.native.game.ChessSession
import io.github.aristheg201.svhub.native.game.NativeGameView
import io.github.aristheg201.svhub.native.game.NativeSeat
import io.github.aristheg201.svhub.native.game.TowerDefenseDefinitions
import io.github.aristheg201.svhub.native.game.TowerDefenseSession
import io.github.aristheg201.svhub.native.game.UnoSession
import io.github.aristheg201.svhub.native.game.XiangqiSession
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.ui.SceneCameraFraming
import io.github.aristheg201.svhub.ui.SceneCameras
import io.github.aristheg201.svhub.ui.SceneVec3
import io.github.aristheg201.svhub.ui.UiDensity
import io.github.aristheg201.svhub.ui.UiRect
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Headless/CI visual acceptance harness for SVHub.
 *
 * TFT, every non-TFT gameplay surface, result screens, skins and store previews
 * are captured with real Cobblemon actors. Missing gameplay evidence is a failure.
 */
object VisualSmokeHarness {
    private const val ENV = "SVHUB_VISUAL_SMOKE"
    private const val EXPECTED_ACTORS = 12
    private val gson = Gson()
    private val arenas = listOf("monster_island", "gotham_rooftops", "sector_2814", "kanto_stadium", "dragon_shrine", "distortion_rift", "ultra_lab", "ancient_ruins")
    private val uiScenarios = listOf("planning", "carousel", "augment", "pve", "pve_loot", "boss", "tactician_move")
    private val gameplayScenarios = listOf("chess", "xiangqi", "tower_defense", "ludo", "uno", "pokecards")
    private val resultScenarios = listOf("chess", "tower_defense", "tft")
    private val storeScenarios = listOf("arena_preview", "arena_owned", "arena_equipped", "tactician_preview", "tactician_owned", "tactician_equipped")
    private val smokeSpecies = listOf(
        "cobblemon:bulbasaur", "cobblemon:pikachu", "cobblemon:gengar", "cobblemon:machamp",
        "cobblemon:charmander", "cobblemon:snorlax", "cobblemon:onix", "cobblemon:vaporeon",
        "cobblemon:eevee", "cobblemon:lucario", "cobblemon:charizard", "cobblemon:lapras",
        "cobblemon:mewtwo", "cobblemon:squirtle", "cobblemon:rapidash", "cobblemon:gardevoir",
        "cobblemon:aggron", "cobblemon:milotic", "cobblemon:arcanine", "cobblemon:gallade",
        "cobblemon:donphan", "cobblemon:mudsdale", "cobblemon:magnezone", "cobblemon:pawniard",
        "cobblemon:rattata", "cobblemon:zubat", "cobblemon:geodude", "cobblemon:gastly",
        "cobblemon:blastoise", "cobblemon:venusaur", "cobblemon:greninja", "cobblemon:garchomp",
        "cobblemon:tyranitar", "cobblemon:dragonite", "cobblemon:metagross", "cobblemon:sylveon",
        "cobblemon:mamoswine", "cobblemon:excadrill", "cobblemon:zoroark", "cobblemon:roserade",
        "cobblemon:electivire", "cobblemon:rayquaza", "cobblemon:zacian", "cobblemon:kyogre",
        "cobblemon:hooh", "cobblemon:chiyu"
    )

    private var enabled = false
    private var arenaIndex = 0
    private var uiScenarioIndex = 0
    private var gameplayScenarioIndex = 0
    private var resultScenarioIndex = 0
    private var storeScenarioIndex = 0
    private var showcaseCaptured = false
    private var stableTicks = 0
    private var bootTicks = 0
    private var finalWaitTicks = 0
    private var capturedCurrent = false
    private var syntheticSpeciesReady = false
    private var rendererReady = false

    fun register() {
        if (System.getenv(ENV) != "1") return
        enabled = true
        System.out.println("[SVHub Visual Smoke] enabled")
        ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }
    }

    private fun tick(client: Minecraft) {
        if (!enabled) return
        bootTicks++
        if (bootTicks == 1) { client.options.guiScale().set(2); client.resizeDisplay() }

        prepareSyntheticSpecies()
        if (!syntheticSpeciesReady) {
            if (bootTicks > 1200) {
                throw IllegalStateException("SVHub visual smoke could not seed title-screen Cobblemon species")
            }
            return
        }

        if (!rendererReady) {
            val probe = smokeView(smokeSpecies.first())
            val outcome = probe?.let(PokemonModelRenderer::diagnostics)?.outcome
            rendererReady = outcome != null && outcome != "REJECTED" && outcome != "FALLBACK"
            if (!rendererReady) return
            System.out.println("[SVHub Visual Smoke] Cobblemon model repository ready for embedded actors")
        }

        if (arenaIndex >= arenas.size) {
            tickUiScenario(client)
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
        if (!capturedCurrent && stableTicks >= 70 && active.fixtureReady && resolvedActors >= EXPECTED_ACTORS) {
            capturedCurrent = true
            val fileName = "svhub-tft-$arenaId.png"
            Screenshot.grab(client.gameDirectory, fileName, client.mainRenderTarget) { message ->
                System.out.println("[SVHub Visual Smoke] captured $fileName with $resolvedActors actors :: ${message.string}")
            }
        }

        if (!capturedCurrent && stableTicks > 600) {
            throw IllegalStateException(
                "SVHub visual smoke timed out waiting for Cobblemon actors in $arenaId: " +
                    "$resolvedActors/$EXPECTED_ACTORS resolved; fixtureReady=${active.fixtureReady}"
            )
        }

        if (capturedCurrent && stableTicks >= 95) {
            arenaIndex++
            stableTicks = 0
            capturedCurrent = false
        }
    }

    private fun tickUiScenario(client: Minecraft) {
        if (uiScenarioIndex >= uiScenarios.size) {
            tickGameplayScenario(client)
            return
        }

        val scenario = uiScenarios[uiScenarioIndex]
        val active = client.screen as? TftUiVisualSmokeScreen
        if (active?.scenario != scenario) {
            stableTicks = 0
            capturedCurrent = false
            client.setScreen(TftUiVisualSmokeScreen(scenario))
            System.out.println("[SVHub Visual Smoke] opened full TFT UI scenario " + scenario)
            return
        }

        stableTicks++
        val diagnostics=PokemonModelRenderer.sceneSizingDiagnostics()
        val resolvedActors = if(scenario=="carousel") diagnostics.count { it.instanceId.startsWith("carousel:") }
            else diagnostics.count { it.instanceId.startsWith("tft:visual-ui-" + scenario + ":") }
        val requiredActors=if(scenario=="carousel")8 else EXPECTED_ACTORS
        val captureAt=if(scenario=="tactician_move")8 else 70
        if (!capturedCurrent && stableTicks >= captureAt && active.fixtureReady && resolvedActors >= requiredActors) {
            capturedCurrent = true
            val fileName = "svhub-tft-ui-" + scenario + ".png"
            Screenshot.grab(client.gameDirectory, fileName, client.mainRenderTarget) { message ->
                System.out.println("[SVHub Visual Smoke] captured " + fileName + " with " + resolvedActors + " actors :: " + message.string)
            }
        }

        if (!capturedCurrent && stableTicks > 600) {
            throw IllegalStateException(
                "SVHub full TFT UI smoke timed out in " + scenario + ": " +
                    resolvedActors + "/" + EXPECTED_ACTORS + " resolved; fixtureReady=" + active.fixtureReady
            )
        }

        if (capturedCurrent && stableTicks >= 95) {
            uiScenarioIndex++
            stableTicks = 0
            capturedCurrent = false
        }
    }


    private fun tickGameplayScenario(client: Minecraft) {
        if (gameplayScenarioIndex >= gameplayScenarios.size) {
            tickResultScenario(client)
            return
        }
        val scenario = gameplayScenarios[gameplayScenarioIndex]
        val active = client.screen as? NativeGameVisualSmokeScreen
        if (active?.scenario != scenario) {
            stableTicks = 0
            capturedCurrent = false
            client.setScreen(NativeGameVisualSmokeScreen(scenario))
            System.out.println("[SVHub Visual Smoke] opened gameplay scenario " + scenario)
            return
        }
        stableTicks++
        val resolvedActors = active.resolvedActors()
        val requiredActors = active.requiredActors
        if (!capturedCurrent && stableTicks >= 70 && active.fixtureReady && resolvedActors >= requiredActors) {
            capturedCurrent = true
            val fileName = "svhub-gameplay-" + scenario + ".png"
            Screenshot.grab(client.gameDirectory, fileName, client.mainRenderTarget) { message ->
                System.out.println("[SVHub Visual Smoke] captured " + fileName + " with " + resolvedActors + " actors :: " + message.string)
            }
        }
        if (!capturedCurrent && stableTicks > 600) {
            throw IllegalStateException(
                "SVHub gameplay smoke timed out in " + scenario + ": " +
                    resolvedActors + "/" + requiredActors + " actors; fixtureReady=" + active.fixtureReady
            )
        }
        if (capturedCurrent && stableTicks >= 95) {
            gameplayScenarioIndex++
            stableTicks = 0
            capturedCurrent = false
        }
    }

    private fun tickResultScenario(client:Minecraft) {
        if(resultScenarioIndex>=resultScenarios.size) {
            tickSkinShowcase(client)
            return
        }
        val scenario=resultScenarios[resultScenarioIndex]
        val active=client.screen as? ResultVisualSmokeScreen
        if(active?.scenario!=scenario) {
            stableTicks=0
            capturedCurrent=false
            client.setScreen(ResultVisualSmokeScreen(scenario))
            System.out.println("[SVHub Visual Smoke] opened result scenario "+scenario)
            return
        }
        stableTicks++
        if(!capturedCurrent && stableTicks>=60 && active.fixtureReady) {
            capturedCurrent=true
            val fileName="svhub-result-"+scenario+".png"
            Screenshot.grab(client.gameDirectory,fileName,client.mainRenderTarget){message->
                System.out.println("[SVHub Visual Smoke] captured "+fileName+" :: "+message.string)
            }
        }
        if(!capturedCurrent && stableTicks>500) throw IllegalStateException("SVHub result smoke timed out in "+scenario)
        if(capturedCurrent && stableTicks>=85) {
            resultScenarioIndex++
            stableTicks=0
            capturedCurrent=false
        }
    }

    private fun tickSkinShowcase(client:Minecraft) {
        if(showcaseCaptured) {
            tickStoreScenario(client)
            return
        }
        val active=client.screen as? SkinShowcaseVisualSmokeScreen
        if(active==null) {
            stableTicks=0
            client.setScreen(SkinShowcaseVisualSmokeScreen())
            System.out.println("[SVHub Visual Smoke] opened skin showcase")
            return
        }
        stableTicks++
        val resolved=PokemonModelRenderer.previewResolved("skin-showcase:visual-skin")
        if(stableTicks>=60 && active.fixtureReady && resolved) {
            showcaseCaptured=true
            Screenshot.grab(client.gameDirectory,"svhub-skin-showcase.png",client.mainRenderTarget){message->
                System.out.println("[SVHub Visual Smoke] captured svhub-skin-showcase.png :: "+message.string)
            }
            stableTicks=0
        } else if(stableTicks>600) {
            throw IllegalStateException("SVHub skin showcase smoke timed out: resolved="+resolved+" fixtureReady="+active.fixtureReady)
        }
    }

    private fun tickStoreScenario(client:Minecraft) {
        if(storeScenarioIndex>=storeScenarios.size) {
            finalWaitTicks++
            if(finalWaitTicks>=40) {
                val total=arenas.size+uiScenarios.size+gameplayScenarios.size+resultScenarios.size+1+storeScenarios.size
                System.out.println("[SVHub Visual Smoke] completed "+total+" captures; stopping client")
                enabled=false
                client.stop()
            }
            return
        }
        val scenario=storeScenarios[storeScenarioIndex]
        val active=client.screen as? StoreVisualSmokeScreen
        if(active?.scenario!=scenario) {
            stableTicks=0
            capturedCurrent=false
            client.setScreen(StoreVisualSmokeScreen(scenario))
            System.out.println("[SVHub Visual Smoke] opened store scenario "+scenario)
            return
        }
        stableTicks++
        val expectedActor=if(scenario.startsWith("tactician"))"store:svhub:shiny_mewtwo" else "store:svhub:pikachu"
        val resolved=if(scenario.startsWith("tactician")) {
            PokemonModelRenderer.previewResolved(expectedActor)
        } else {
            PokemonModelRenderer.sceneSizingDiagnostics().any { it.instanceId==expectedActor }
        }
        if(!capturedCurrent && stableTicks>=55 && active.fixtureReady && resolved) {
            capturedCurrent=true
            val fileName="svhub-store-"+scenario+".png"
            Screenshot.grab(client.gameDirectory,fileName,client.mainRenderTarget){message->
                System.out.println("[SVHub Visual Smoke] captured "+fileName+" :: "+message.string)
            }
        }
        if(!capturedCurrent && stableTicks>600) {
            throw IllegalStateException("SVHub store smoke timed out in "+scenario+": actor="+expectedActor+" resolved="+resolved)
        }
        if(capturedCurrent && stableTicks>=80) {
            storeScenarioIndex++
            stableTicks=0
            capturedCurrent=false
        }
    }

    /**
     * The normal client receives PokemonSpecies from server-data synchronization.
     * CI intentionally stays on the title screen, so seed the species required
     * by all gameplay fixtures while continuing to use Cobblemon's real assets.
     * This path is unreachable unless SVHUB_VISUAL_SMOKE=1.
     */
    private fun prepareSyntheticSpecies() {
        if (syntheticSpeciesReady) return
        runCatching {
            val merged = PokemonSpecies.species.associateBy { it.resourceIdentifier }.toMutableMap()
            var added = 0
            smokeSpecies.forEachIndexed { index, raw ->
                val id = ResourceLocation.tryParse(raw) ?: return@forEachIndexed
                if (merged.containsKey(id)) return@forEachIndexed
                val synthetic = Species().apply {
                    resourceIdentifier = id
                    name = id.path.replace("_", " ").split(" ").joinToString("") { token ->
                        token.replaceFirstChar(Char::uppercase)
                    }
                    nationalPokedexNumber = 10000 + index
                    baseScale = 1f
                    implemented = true
                }
                merged[id] = synthetic
                added++
            }
            if (added > 0) {
                PokemonSpecies.reload(merged)
                System.out.println("[SVHub Visual Smoke] seeded $added title-screen Pokemon species")
            }
            syntheticSpeciesReady = smokeSpecies.all { raw ->
                ResourceLocation.tryParse(raw)?.let(PokemonSpecies::getByIdentifier) != null
            }
        }
    }

    private fun smokeView(speciesId: String): PokemonView? {
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
    private class TftUiVisualSmokeScreen(val scenario: String) : Screen(Component.literal("SVHub TFT UI Visual Smoke")) {
        private val ui = TftUiState().also { if(scenario=="tactician_move") it.tacticianDestination=.92 to .25 }
        private val view = fixtureView(scenario)
        private var rendered = false
        private var movementObserved = false
        val fixtureReady get() = rendered && (scenario!="tactician_move" || movementObserved)

        override fun isPauseScreen(): Boolean = false

        override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val area = UiRect(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
            TftGameRenderer.render(
                gui = gui,
                font = font,
                area = area,
                density = io.github.aristheg201.svhub.ui.NativeLayout.resolve(width,height).density,
                view = view,
                ui = ui,
                mouseX = mouseX,
                mouseY = mouseY,
                hooks = TftGameRenderer.Hooks(
                    control = { rect, label, enabled, _ -> NativeControlRenderer.draw(gui,font,rect,label,mouseX,mouseY,enabled=enabled) },
                    hit = { _, _ -> },
                    sceneInput = { _ -> },
                    dropInput = { _ -> },
                    action = { action, args ->
                        if(scenario=="tactician_move" && action=="tactician_move") {
                            view.getAsJsonObject("fields")?.addProperty("tacticianPosition",args.getValue("u")+","+args.getValue("v"))
                        }
                    },
                    back = {}
                )
            )
            movementObserved = movementObserved || ui.tacticianState() in setOf(TacticianPresentationState.WALK,TacticianPresentationState.RUN)
            rendered = true
        }

        companion object {
            fun fixtureView(scenario: String): JsonObject {
                val pve = scenario == "pve" || scenario == "boss"
                val boss = scenario == "boss"
                val loot = scenario == "pve_loot"
                val augment = scenario == "augment"
                val carousel = scenario == "carousel"
                val moving = scenario == "tactician_move"
                val fields = JsonObject().apply {
                    addProperty("set", "visual_smoke")
                    addProperty("participantId", "visual")
                    addProperty("boardColumns", "7")
                    addProperty("boardRows", "4")
                    addProperty("shopSlots", "5")
                    addProperty("benchSlots", "9")
                    addProperty("arenaId", "kanto_stadium")
                    addProperty("tacticianEntity", "")
                    addProperty("tacticianSpecies", if (pve) "cobblemon:mewtwo" else "cobblemon:pikachu")
                    addProperty("tacticianAspects", if (pve) "shiny" else "")
                    addProperty("tacticianId", if (pve) "svhub:shiny_mewtwo" else "svhub:pikachu")
                    addProperty("tacticianScale", if (pve) "0.72" else "0.70")
                    addProperty("tacticianState", when { carousel->"carousel_movement";pve->"round_start";else->"idle" })
                    addProperty("tacticianPosition", if(moving) "0.08,0.75" else "0.5,0.5")
                    addProperty("tacticianCanMove", (!pve&&!carousel&&!augment).toString())
                    addProperty("scouting", "false")
                    addProperty("round", when { boss->"5-3";loot->"1-1";carousel->"2-4";augment->"2-1";pve->"1-1";else->"2-2" })
                    addProperty("roundType", when { boss->"boss";carousel->"carousel";augment->"augment";pve||loot->"pve";else->"pvp" })
                    addProperty("pveActive", pve.toString())
                    addProperty("pveRound", if (pve) if(boss)"5-3" else "1-1" else "")
                    addProperty("pveComponentDrops", if (pve) "2" else "0")
                    addProperty("pveLootTable", if (pve) "opening_cache" else "")
                    addProperty("bossRound", boss.toString())
                    addProperty("pveLoot", if(loot)"sword,rod,loot:gold,loot:xp,full:rapid_fire" else "")
                    addProperty("pveLootSerial", if(loot)"1" else "0")
                    addProperty("roundIndex", if (pve) "0" else "4")
                    addProperty("phaseEndsAt", (System.currentTimeMillis() + 120_000L).toString())
                    addProperty("gold", "36")
                    addProperty("hp", "87")
                    addProperty("level", "6")
                    addProperty("xp", "14")
                    addProperty("xpNext", "36")
                    addProperty("lastInterest", "3")
                    addProperty("streak", "2")
                    addProperty("bench", benchPayload(scenario))
                    addProperty("players", "visual~Aris~87~6~0~0;rival~Rival~73~6~0~0;third~Third~52~5~0~0;fourth~Fourth~31~5~0~0")
                    addProperty("traits", "guardian~Guardian~4~4~6~Defense active;storm~Storm~2~2~4~Speed active;arcane~Arcane~1~0~2~")
                    addProperty("unitCatalog", unitCatalog())
                    addProperty("traitCatalog", traitCatalog())
                    addProperty("itemBench", "sword,rod,tear,vest,full:rapid_fire")
                    addProperty("itemCatalog", itemCatalog())
                    addProperty("lastItemEvent", "")
                    addProperty("itemEventSerial", "0")
                    addProperty("selectedAugments", if(augment)"[]" else selectedAugments())
                    addProperty("augmentChoices", if(augment)"power_surge~Power Surge~Gain attack power;swift_steps~Swift Steps~Gain attack speed;second_wind~Second Wind~Heal after combat" else "")
                    addProperty("draft", if(carousel)draftPayload() else "")
                    addProperty("carouselArenaId","carousel_convergence")
                    addProperty("carouselPosition","0.0,0.0")
                    addProperty("carouselMaxMove","0.8")
                    addProperty("carouselMovementRadius","5.6")
                    addProperty("carouselUnlockAt",(System.currentTimeMillis()-1000L).toString())
                    addProperty("carouselPickupRadius","0.72")
                    addProperty("carouselPicked","false")
                    addProperty("carouselCenterDecoration","minecraft:beacon")
                    addProperty("opponent", if (pve) "Wild Pokémon" else "Rival")
                    addProperty("canEditBoard", (!pve).toString())
                    addProperty("capabilities", if (pve)
                        "CAN_BUY_UNIT,CAN_REROLL,CAN_BUY_XP,CAN_SELL,CAN_SCOUT,CAN_EMOTE,CAN_OPEN_SHOP"
                    else
                        "CAN_BUY_UNIT,CAN_REROLL,CAN_BUY_XP,CAN_SELL,CAN_MOVE_BOARD_UNIT,CAN_MOVE_BENCH_UNIT,CAN_EQUIP_ITEM,CAN_COMBINE_ITEM,CAN_SCOUT,CAN_EMOTE,CAN_OPEN_SHOP,CAN_INTERACT_BENCH")
                }
                return JsonObject().apply {
                    addProperty("sessionId", "visual-ui-" + scenario)
                    addProperty("gameId", "tft")
                    addProperty("phase", when { carousel->"draft";pve->"combat";loot->"post";else->"planning" })
                    addProperty("status", when { carousel->"2-4 • Shared Draft";boss->"5-3 • Boss";pve->"1-1 • PvE";loot->"1-1 • Loot";augment->"2-1 • Augment";else->"2-2 • Planning" })
                    add("board", boardPayload(scenario))
                    add("cards", shopCards())
                    add("actions", JsonArray().apply {
                        add(JsonObject().apply { addProperty("id", "refresh"); addProperty("enabled", true) })
                        add(JsonObject().apply { addProperty("id", "buy_xp"); addProperty("enabled", true) })
                    })
                    add("fields", fields)
                    addProperty("revision", 1L)
                    addProperty("finished", false)
                }
            }

            private fun draftPayload():String =
                (0 until 8).joinToString(";") { index ->
                    val angle=2.0*Math.PI*index/8.0
                    val x=kotlin.math.cos(angle).toFloat()*3.0f
                    val y=kotlin.math.sin(angle).toFloat()*3.0f
                    listOf(index,"unit_"+index,VisualSmokeHarness.smokeSpecies[index],
                        listOf("sword","rod","tear","vest")[index%4],"",1,1+index%5,"guardian",x,y,"",1.0).joinToString("~")
                }

            private fun boardPayload(scenario: String): JsonArray {
                val pve = scenario == "pve"
                val cells = MutableList(56) { "" }
                val ownSlots = listOf(28, 29, 30, 31, 35, 36, 37, 38)
                val enemySlots = if (pve) listOf(7, 8, 9, 14, 15, 16) else emptyList()
                ownSlots.forEachIndexed { index, cell ->
                    val species=when(index){
                        0->"cobblemon:ho_oh"
                        1->"cobblemon:chi_yu"
                        else->VisualSmokeHarness.smokeSpecies[index]
                    }
                    cells[cell] = token(scenario, index, species, 0, if (index == 1) 3 else 1)
                }
                enemySlots.forEachIndexed { index, cell ->
                    cells[cell] = token(scenario, index + 6, VisualSmokeHarness.smokeSpecies[index + 6], 1, if (index == 2) 2 else 1)
                }
                return JsonArray().apply { cells.forEach { add(it) } }
            }

            private fun token(scenario: String, index: Int, species: String, team: Int, star: Int): String {
                val scale = when (index % 4) { 0 -> 0.85; 1 -> 1.0; 2 -> 1.15; else -> 1.3 }
                val items = if (team == 0 && index == 1) "sword,rod" else ""
                return listOf(
                    "visual-ui-" + scenario + ":board:" + index,
                    "unit_" + index,
                    species,
                    star,
                    720 - index * 12,
                    800,
                    35 + index,
                    100,
                    team,
                    "",
                    items,
                    1 + index % 5,
                    if (team == 0) "fighter" else "enemy",
                    "",
                    0,
                    0L,
                    0L,
                    1,
                    scale
                ).joinToString("~")
            }

            private fun benchPayload(scenario: String): String =
                (0 until 4).joinToString(";") { index ->
                    val species = VisualSmokeHarness.smokeSpecies[index + 8]
                    listOf(
                        index + 2,
                        "visual-ui-" + scenario + ":bench:" + index,
                        "bench_" + index,
                        species,
                        1,
                        "",
                        if (index == 0) "tear" else "",
                        1 + index,
                        "reserve",
                        0.9 + index * 0.1
                    ).joinToString("~")
                }

            private fun unitCatalog(): String = JsonObject().apply {
                VisualSmokeHarness.smokeSpecies.forEachIndexed { index, species ->
                    add("unit_" + index, unitInfo(species, index))
                    if (index >= 8) add("bench_" + (index - 8), unitInfo(species, index))
                }
            }.toString()

            private fun unitInfo(species: String, index: Int) = JsonObject().apply {
                addProperty("name", species.substringAfter(':').replace('_', ' ').replaceFirstChar(Char::uppercase))
                addProperty("species", species)
                addProperty("scale", when (index % 4) { 0 -> 0.85; 1 -> 1.0; 2 -> 1.15; else -> 1.3 })
                addProperty("cost", 1 + index % 5)
                addProperty("role", "fighter")
                addProperty("traits", if (index % 2 == 0) "guardian,storm" else "guardian,arcane")
                addProperty("hp", 800)
                addProperty("attackDamage", 65 + index)
                addProperty("defense", 35)
                addProperty("specialDefense", 35)
                addProperty("attackSpeed", 0.75)
                addProperty("range", 1)
                addProperty("manaStart", 20)
                addProperty("manaMax", 100)
                addProperty("abilityId", "visual_cast")
                addProperty("abilityName", "Visual Cast")
                addProperty("abilityTarget", "current")
                addProperty("damageType", "magic")
                addProperty("damage", 180)
            }

            private fun traitCatalog(): String = JsonObject().apply {
                add("guardian", traitInfo("Guardian", 2, 4, 6))
                add("storm", traitInfo("Storm", 2, 4))
                add("arcane", traitInfo("Arcane", 2, 3))
            }.toString()

            private fun traitInfo(name: String, vararg thresholds: Int) = JsonObject().apply {
                addProperty("name", name)
                add("tiers", JsonArray().apply {
                    thresholds.forEach { threshold ->
                        add(JsonObject().apply {
                            addProperty("threshold", threshold)
                            addProperty("description", name + " tier " + threshold)
                            addProperty("effects", "power=" + threshold)
                            addProperty("teamEffects", "team_power=" + threshold)
                        })
                    }
                })
            }

            private fun itemCatalog(): String = JsonObject().apply {
                add("sword", item("component", "Sword", "minecraft:iron_sword", "attack_damage=10"))
                add("rod", item("component", "Rod", "minecraft:blaze_rod", "ability_power=10"))
                add("tear", item("component", "Tear", "minecraft:lapis_lazuli", "mana=15"))
                add("vest", item("component", "Vest", "minecraft:iron_chestplate", "defense=20"))
                add("rapid_fire", item("full", "Rapid Fire", "minecraft:diamond_sword", "attack_speed=25", "sword,rod"))
            }.toString()

            private fun item(kind: String, name: String, stack: String, effects: String, components: String = "") =
                JsonObject().apply {
                    addProperty("kind", kind)
                    addProperty("name", name)
                    addProperty("stack", stack)
                    addProperty("effects", effects)
                    if (components.isNotBlank()) addProperty("components", components)
                }

            private fun selectedAugments(): String = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("id", "visual_aug")
                    addProperty("name", "Battle Ready")
                    addProperty("tier", "Gold")
                    addProperty("description", "Visual smoke augment")
                    addProperty("mechanic", "attack damage + 10")
                })
            }.toString()

            private fun shopCards(): JsonArray = JsonArray().apply {
                repeat(5) { index ->
                    val species = VisualSmokeHarness.smokeSpecies[index]
                    add(JsonObject().apply {
                        addProperty("id", "shop:" + index)
                        addProperty("label", "unit_" + index)
                        addProperty("value", index + 1)
                        add("meta", JsonObject().apply {
                            addProperty("species", species)
                            addProperty("aspects", "")
                            addProperty("enabled", "true")
                        })
                    })
                }
            }
        }
    }

    private class NativeGameVisualSmokeScreen(val scenario:String):Screen(Component.literal("SVHub Gameplay Visual Smoke")) {
        private val boardUi=NativeBoardSceneUiState()
        private val view=fixtureView(scenario)
        private var rendered=false
        val fixtureReady get()=rendered
        val requiredActors:Int get()=when(scenario){
            "chess"->32
            "xiangqi"->32
            "tower_defense"->10
            "ludo"->8
            "pokecards"->5
            else->0
        }

        override fun isPauseScreen():Boolean=false

        fun resolvedActors():Int {
            val diagnostics=PokemonModelRenderer.sceneSizingDiagnostics()
            return when(scenario){
                "chess"->diagnostics.count{it.instanceId.startsWith("chess:")}
                "xiangqi"->diagnostics.count{it.instanceId.startsWith("xiangqi:")}
                "tower_defense"->diagnostics.count{it.instanceId.startsWith("td:tower:")||it.instanceId.startsWith("td:enemy:")}
                "ludo"->diagnostics.count{it.instanceId.startsWith("ludo:")}
                "pokecards"->(0 until 5).count{ index ->
                    PokemonModelRenderer.sceneResolved("pokecards:"+index+":"+index)
                }
                else->0
            }
        }

        override fun render(gui:GuiGraphics,mouseX:Int,mouseY:Int,partialTick:Float) {
            gui.fill(0,0,width,height,0xFF050A0E.toInt())
            val boardW=runCatching{view.get("boardWidth")?.asInt?:0}.getOrDefault(0)
            val boardH=runCatching{view.get("boardHeight")?.asInt?:0}.getOrDefault(0)
            val suffix=if(boardW>0&&boardH>0)"  •  "+boardW+"×"+boardH else ""
            gui.drawString(font,"GAMEPLAY  •  "+scenario.uppercase()+suffix,14,10,0xFFF2F6F4.toInt(),true)
            val area=UiRect(10,28,(width-20).coerceAtLeast(120),(height-38).coerceAtLeast(90))
            if(CardTable3DRenderer.supports(scenario)) {
                CardTable3DRenderer.render(gui,font,area,view,mouseX,mouseY){_,_->}
            } else {
                NativeBoardSceneRenderer.render(gui,font,area,view,boardUi,null)
            }
            rendered=true
        }

        companion object {
            fun fixtureView(scenario:String):JsonObject=when(scenario){
                "chess"->{
                    val game=ChessSession(listOf(NativeSeat("visual","Aris"),NativeSeat("rival","Rival")),seed=11L)
                    check(game.act("visual","move",mapOf("from" to "e2","to" to "e4")).accepted)
                    check(game.act("rival","move",mapOf("from" to "e7","to" to "e5")).accepted)
                    nativeView(game.viewFor("visual"))
                }
                "xiangqi"->{
                    val game=XiangqiSession(listOf(NativeSeat("visual","Aris"),NativeSeat("rival","Rival")),seed=12L)
                    check(game.act("visual","move",mapOf("from" to "a6","to" to "a5")).accepted)
                    check(game.act("rival","move",mapOf("from" to "a3","to" to "a4")).accepted)
                    nativeView(game.viewFor("visual"))
                }
                "tower_defense"->{
                    val game=TowerDefenseSession(listOf(NativeSeat("visual","Aris")),seed=13L)
                    check(game.act("visual","start_wave",emptyMap()).accepted)
                    var now=System.currentTimeMillis()
                    repeat(8){now+=1_000L;game.tick(now)}
                    listOf("charmander" to 4,"squirtle" to 59,"bulbasaur" to 104).forEach{(type,slot)->
                        check(game.act("visual","deploy",mapOf("type" to type,"slot" to slot.toString())).accepted)
                    }
                    nativeView(game.viewFor("visual"))
                }
                "ludo"->ludoFixture()
                "uno"->nativeView(UnoSession(listOf(
                    NativeSeat("visual","Aris"),NativeSeat("rival","Rival"),
                    NativeSeat("third","Third"),NativeSeat("fourth","Fourth")
                ),seed=14L).viewFor("visual"))
                "pokecards"->nativeView(CardDuelSession(
                    listOf(NativeSeat("visual","Aris"),NativeSeat("rival","Rival")),seed=15L
                ).viewFor("visual"))
                else->JsonObject()
            }

            private fun nativeView(view:NativeGameView):JsonObject = gson.toJsonTree(view).asJsonObject

            private fun ludoFixture():JsonObject {
                val cells=MutableList(52){""}
                val placements=listOf(
                    0 to "1:1",4 to "1:2",13 to "2:1",17 to "2:2",
                    26 to "3:1",30 to "3:2",39 to "4:1",43 to "4:2"
                )
                placements.forEach{(slot,token)->cells[slot]=token}
                return JsonObject().apply {
                    addProperty("sessionId","visual-ludo")
                    addProperty("gameId","ludo")
                    addProperty("title","Cờ Cá Ngựa")
                    addProperty("phase","move")
                    addProperty("turn","Aris")
                    addProperty("status","Gameplay visual acceptance")
                    addProperty("boardWidth",13)
                    addProperty("boardHeight",4)
                    add("board",JsonArray().apply{cells.forEach(::add)})
                    add("cards",JsonArray())
                    add("actions",JsonArray())
                    add("fields",JsonObject().apply{
                        addProperty("you","0")
                        addProperty("rolled","6")
                        addProperty("arenaId","ludo")
                        addProperty("safeSquares","0,8,13,21,26,34,39,47")
                    })
                    addProperty("revision",3L)
                    addProperty("finished",false)
                }
            }
        }
    }

    private class ResultVisualSmokeScreen(val scenario:String):Screen(Component.literal("SVHub Result Visual Smoke")) {
        private val tftUi=TftUiState()
        private val boardUi=NativeBoardSceneUiState()
        private val view=resultFixture(scenario)
        private var rendered=false
        val fixtureReady get()=rendered

        override fun isPauseScreen():Boolean=false

        override fun render(gui:GuiGraphics,mouseX:Int,mouseY:Int,partialTick:Float) {
            val area=UiRect(0,0,width.coerceAtLeast(1),height.coerceAtLeast(1))
            var drawnControls=0
            ArcadeResultRenderer.render(
                gui=gui,font=font,area=area,view=view,
                hooks=ArcadeResultRenderer.Hooks(
                    control={rect,label,enabled,_->NativeControlRenderer.draw(gui,font,rect,label,mouseX,mouseY,enabled=enabled);drawnControls++},continueAction={},rematch={},exit={}
                )
            ){scene->
                when(scenario) {
                    "tft"->TftGameRenderer.render(
                        gui=gui,font=font,area=scene,density=UiDensity.WIDE,view=view,ui=tftUi,mouseX=-10,mouseY=-10,
                        hooks=TftGameRenderer.Hooks(control={_,_,_,_->},hit={_,_->},sceneInput={_->},dropInput={_->},action={_,_->},back={}),
                        sceneOnly=true
                    )
                    else->NativeBoardSceneRenderer.render(gui,font,scene.inset(4),view,boardUi,null)
                }
            }
            check(drawnControls==3){"Result smoke must render Continue, Rematch and Exit"}
            rendered=true
        }

        companion object {
            private fun resultFixture(gameId:String):JsonObject {
                if(gameId=="tft") {
                    val view=TftUiVisualSmokeScreen.fixtureView("planning")
                    view.addProperty("finished",true)
                    view.addProperty("phase","finished")
                    view.addProperty("winner","Aris")
                    view.getAsJsonObject("fields").apply {
                        addProperty("result","Victory")
                        addProperty("tacticianState","victory")
                        addProperty("canEditBoard","false")
                    }
                    view.add("resultPresentation",presentation("victory","first","tft",
                        listOf("placement" to "1","level" to "8","health" to "42","gold" to "51"),
                        listOf("match_reward" to ""),
                        listOf("placement" to "1")))
                    return view
                }

                if(gameId=="tower_defense") {
                    val view=NativeGameVisualSmokeScreen.fixtureView("tower_defense")
                    view.addProperty("phase","finished")
                    view.addProperty("status","Victory")
                    view.addProperty("finished",true)
                    view.addProperty("winner","Aris")
                    view.add("resultPresentation",presentation("victory","defense_complete","tower_defense",
                        listOf("wave" to "20","lives" to "7","gold" to "132","towers" to "3"),
                        listOf("loot_count" to "5"),listOf("waves_cleared" to "20")))
                    return view
                }

                val chess=gameId=="chess"
                val board=chessBoard()
                val fields=JsonObject().apply {
                    if(chess) {
                        addProperty("you","white");addProperty("white","Aris");addProperty("black","Rival")
                        addProperty("whiteClockMs","82100");addProperty("blackClockMs","0")
                        addProperty("legalMoves","");addProperty("lastMoveFrom","g7");addProperty("lastMoveTo","g8")
                    } else {
                        addProperty("gold","132");addProperty("lives","7");addProperty("wave","20");addProperty("running","false")
                        addProperty("path","0,1,2,3,11,19,27,35,43,51,59,60,61,62,63")
                    }
                }
                return JsonObject().apply {
                    addProperty("sessionId","visual-result-"+gameId)
                    addProperty("gameId",gameId)
                    addProperty("title",if(chess)"Pokémon Chess" else "Pokémon Tower Defense")
                    addProperty("phase","finished")
                    addProperty("status",if(chess)"Checkmate" else "Victory")
                    addProperty("finished",true)
                    addProperty("winner","Aris")
                    addProperty("boardWidth",8);addProperty("boardHeight",8)
                    add("board",board);add("cards",JsonArray());add("actions",JsonArray());add("fields",fields)
                    add("resultPresentation",presentation("victory","checkmate","chess",
                        listOf("moves" to "38","white_clock" to "82","black_clock" to "0"),
                        listOf("match_reward" to ""),listOf("match_complete" to "")))
                }
            }

            private fun presentation(outcome:String,reason:String,backdrop:String,stats:List<Pair<String,String>>,rewards:List<Pair<String,String>>,progress:List<Pair<String,String>>)=JsonObject().apply {
                addProperty("outcome",outcome);addProperty("reason",reason);addProperty("backdrop",backdrop);addProperty("canRematch",true)
                fun lines(values:List<Pair<String,String>>)=JsonArray().apply { values.forEach { (key,value)->add(JsonObject().apply{addProperty("key",key);addProperty("value",value)}) } }
                add("stats",lines(stats));add("rewards",lines(rewards));add("progression",lines(progress))
            }

            private fun chessBoard()=JsonArray().apply {
                val cells=MutableList(64){""}
                mapOf(4 to "k",6 to "R",7 to "K",52 to "P",60 to "r").forEach{(i,p)->cells[i]=p}
                cells.forEach{add(it)}
            }
        }
    }

    private class SkinShowcaseVisualSmokeScreen:Screen(Component.literal("SVHub Skin Showcase Visual Smoke")) {
        private var rendered=false
        val fixtureReady get()=rendered
        override fun isPauseScreen():Boolean=false
        override fun render(gui:GuiGraphics,mouseX:Int,mouseY:Int,partialTick:Float) {
            gui.fill(0,0,width,height,0xFF060D11.toInt())
            val rect=UiRect(10,10,(width-20).coerceAtLeast(140),(height-20).coerceAtLeast(100))
            SkinShowcaseRenderer.render(
                gui,font,rect,
                SkinShowcaseRenderer.Skin(
                    id="visual-skin",
                    name="Shiny Mewtwo",
                    species="cobblemon:mewtwo",
                    aspect="shiny",
                    source="Showcase",
                    rarity="Legendary",
                    owned=true
                )
            )
            rendered=true
        }
    }

    private class StoreVisualSmokeScreen(val scenario:String):Screen(Component.literal("SVHub Store Visual Smoke")) {
        private val ui=CosmeticStoreUi().also { state ->
            state.kind=if(scenario.startsWith("tactician"))"TACTICIAN" else "ARENA"
            state.selected=if(state.kind=="TACTICIAN")"svhub:shiny_mewtwo" else "dragon_shrine"
        }
        private val state=fixture(scenario)
        private var rendered=false
        val fixtureReady get()=rendered

        override fun isPauseScreen():Boolean=false

        override fun render(gui:GuiGraphics,mouseX:Int,mouseY:Int,partialTick:Float) {
            gui.fill(0,0,width,height,0xFF060D11.toInt())
            val area=UiRect(8,8,(width-16).coerceAtLeast(220),(height-16).coerceAtLeast(150))
            var drawnControls=0
            CosmeticStoreRenderer.render(
                gui,font,area,state,ui,
                CosmeticStoreRenderer.Hooks(
                    control={rect,label,enabled,active,_->NativeControlRenderer.draw(gui,font,rect,label,mouseX,mouseY,active=active,enabled=enabled);drawnControls++},
                    intent={_,_->}
                )
            )
            check(drawnControls>=6){"Store smoke must render navigation, tabs, offers and purchase/equip controls"}
            rendered=true
        }

        companion object {
            private fun fixture(scenario:String)=JsonObject().apply {
                val tactician=scenario.startsWith("tactician")
                val owned=scenario.endsWith("owned")||scenario.endsWith("equipped")
                val equipped=scenario.endsWith("equipped")
                addProperty("module","store")
                addProperty("arena",if(!tactician&&equipped)"dragon_shrine" else "kanto_stadium")
                addProperty("tactician",if(tactician&&equipped)"svhub:shiny_mewtwo" else "svhub:pikachu")
                add("balances",JsonObject().apply {
                    addProperty("BeastCoin","2000")
                    addProperty("HunterCoin","2000")
                })
                add("offers",JsonArray().apply {
                    add(storeOffer("ARENA","kanto_stadium","0","BeastCoin",true,!tactician&&!equipped))
                    add(storeOffer("ARENA","dragon_shrine","1000","BeastCoin",!tactician&&owned,!tactician&&equipped))
                    add(storeOffer("TACTICIAN","svhub:pikachu","0","HunterCoin",true,tactician&&!equipped,
                        name="Pikachu",species="cobblemon:pikachu",scale=.70))
                    add(storeOffer("TACTICIAN","svhub:shiny_mewtwo","300","HunterCoin",tactician&&owned,tactician&&equipped,
                        name="Shiny Mewtwo",species="cobblemon:mewtwo",aspects="shiny",scale=.72))
                })
            }

            private fun storeOffer(
                kind:String,id:String,price:String,currency:String,owned:Boolean,equipped:Boolean,
                name:String="",species:String="",aspects:String="",scale:Double=1.0
            )=JsonObject().apply {
                addProperty("kind",kind);addProperty("id",id);addProperty("price",price);addProperty("currency",currency)
                addProperty("owned",owned);addProperty("equipped",equipped)
                if(name.isNotBlank())addProperty("name",name)
                addProperty("entity","")
                addProperty("species",species);addProperty("aspects",aspects);addProperty("scale",scale);addProperty("vfx","")
            }
        }
    }

    private class ArenaVisualSmokeScreen(val arenaId: String) : Screen(Component.literal("SVHub Visual Smoke")) {
        private val scene = PokemonSceneState()
        private var cachedEntities: List<PokemonSceneEntity>? = null
        val fixtureReady get() = cachedEntities?.size == EXPECTED_ACTORS

        override fun isPauseScreen(): Boolean = false

        override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val arena = MinecraftArenaRegistry.definition(arenaId)
            if (arena == null) {
                gui.fill(0, 0, width, height, 0xFF300000.toInt())
                gui.drawCenteredString(font, "Missing arena: $arenaId", width / 2, height / 2, 0xFFFFFFFF.toInt())
                return
            }

            val entities = cachedEntities ?: smokeEntities(arena).takeIf { it.size == EXPECTED_ACTORS }?.also {
                cachedEntities = it
                System.out.println("[SVHub Visual Smoke] fixture ready $arenaId with ${it.size} Pokemon")
            }.orEmpty()
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
                "SVHub TFT · $arenaId · actors ${PokemonModelRenderer.sceneSizingDiagnostics().count { it.instanceId.startsWith("visual:$arenaId:") }}/$EXPECTED_ACTORS",
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

        private fun pokemonView(speciesId: String): PokemonView? = smokeView(speciesId)
    }
}
