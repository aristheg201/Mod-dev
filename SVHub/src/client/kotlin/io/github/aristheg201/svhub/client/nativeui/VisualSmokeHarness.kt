package io.github.aristheg201.svhub.client.nativeui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
 * Headless/CI visual smoke harness for the embedded TFT scene.
 *
 * The smoke scene intentionally contains real Cobblemon actors on both board and
 * bench. A screenshot without resolved actors is a failed smoke test, not a pass.
 */
object VisualSmokeHarness {
    private const val ENV = "SVHUB_VISUAL_SMOKE"
    private const val EXPECTED_ACTORS = 12
    private val arenas = listOf("monster_island", "gotham_rooftops", "sector_2814", "kanto_stadium")
    private val uiScenarios = listOf("planning", "pve")
    private val resultScenarios = listOf("chess", "tower_defense", "tft")
    private val smokeSpecies = listOf(
        "cobblemon:bulbasaur", "cobblemon:pikachu", "cobblemon:gengar", "cobblemon:machamp",
        "cobblemon:charmander", "cobblemon:snorlax", "cobblemon:onix", "cobblemon:vaporeon",
        "cobblemon:eevee", "cobblemon:lucario", "cobblemon:charizard", "cobblemon:lapras",
        "cobblemon:mewtwo"
    )

    private var enabled = false
    private var arenaIndex = 0
    private var uiScenarioIndex = 0
    private var resultScenarioIndex = 0
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
            tickResultScenario(client)
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
        val prefix = "tft:visual-ui-" + scenario + ":"
        val resolvedActors = PokemonModelRenderer.sceneSizingDiagnostics()
            .count { it.instanceId.startsWith(prefix) }
        if (!capturedCurrent && stableTicks >= 70 && active.fixtureReady && resolvedActors >= EXPECTED_ACTORS) {
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

    private fun tickResultScenario(client:Minecraft) {
        if(resultScenarioIndex>=resultScenarios.size) {
            finalWaitTicks++
            if(finalWaitTicks>=40) {
                val total=arenas.size+uiScenarios.size+resultScenarios.size
                System.out.println("[SVHub Visual Smoke] completed "+total+" captures; stopping client")
                enabled=false
                client.stop()
            }
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

    /**
     * The normal client receives PokemonSpecies from server-data synchronization.
     * CI intentionally stays on the title screen, so seed only the twelve smoke
     * species while continuing to use Cobblemon's real model/poser/texture assets.
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
        private val ui = TftUiState()
        private val view = fixtureView(scenario)
        private var rendered = false
        val fixtureReady get() = rendered

        override fun isPauseScreen(): Boolean = false

        override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val area = UiRect(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
            TftGameRenderer.render(
                gui = gui,
                font = font,
                area = area,
                density = UiDensity.WIDE,
                view = view,
                ui = ui,
                mouseX = mouseX,
                mouseY = mouseY,
                hooks = TftGameRenderer.Hooks(
                    control = { _, _, _, _ -> },
                    hit = { _, _ -> },
                    sceneInput = { _ -> },
                    dropInput = { _ -> },
                    action = { _, _ -> },
                    back = {}
                )
            )
            rendered = true
        }

        companion object {
            fun fixtureView(scenario: String): JsonObject {
                val pve = scenario == "pve"
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
                    addProperty("tacticianAspects", if (pve) "greenlantern" else "")
                    addProperty("tacticianId", if (pve) "svhub:green_lantern_mewtwo" else "svhub:pikachu")
                    addProperty("tacticianScale", if (pve) "0.72" else "0.70")
                    addProperty("tacticianState", if (pve) "round_start" else "idle")
                    addProperty("scouting", "false")
                    addProperty("round", if (pve) "1-1" else "2-2")
                    addProperty("roundType", if (pve) "pve" else "pvp")
                    addProperty("pveActive", pve.toString())
                    addProperty("pveRound", if (pve) "1-1" else "")
                    addProperty("pveComponentDrops", if (pve) "2" else "0")
                    addProperty("pveLootTable", if (pve) "opening_cache" else "")
                    addProperty("bossRound", "false")
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
                    addProperty("selectedAugments", selectedAugments())
                    addProperty("augmentChoices", "")
                    addProperty("draft", "")
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
                    addProperty("phase", if (pve) "combat" else "planning")
                    addProperty("status", if (pve) "1-1 • PvE" else "2-2 • Planning")
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

            private fun boardPayload(scenario: String): JsonArray {
                val pve = scenario == "pve"
                val cells = MutableList(56) { "" }
                val ownSlots = listOf(28, 29, 30, 31, 35, 36, 37, 38)
                val enemySlots = if (pve) listOf(7, 8, 9, 14, 15, 16) else emptyList()
                ownSlots.forEachIndexed { index, cell ->
                    cells[cell] = token(scenario, index, VisualSmokeHarness.smokeSpecies[index], 0, if (index == 1) 3 else 1)
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

    private class ResultVisualSmokeScreen(val scenario:String):Screen(Component.literal("SVHub Result Visual Smoke")) {
        private val tftUi=TftUiState()
        private val boardUi=NativeBoardSceneUiState()
        private val view=resultFixture(scenario)
        private var rendered=false
        val fixtureReady get()=rendered

        override fun isPauseScreen():Boolean=false

        override fun render(gui:GuiGraphics,mouseX:Int,mouseY:Int,partialTick:Float) {
            val area=UiRect(0,0,width.coerceAtLeast(1),height.coerceAtLeast(1))
            ArcadeResultRenderer.render(
                gui=gui,font=font,area=area,view=view,
                hooks=ArcadeResultRenderer.Hooks(
                    control={_,_,_,_->},continueAction={},rematch={},exit={}
                )
            ){scene->
                when(scenario) {
                    "tft"->TftGameRenderer.render(
                        gui=gui,font=font,area=scene,density=UiDensity.WIDE,view=view,ui=tftUi,mouseX=-10,mouseY=-10,
                        hooks=TftGameRenderer.Hooks(control={_,_,_,_->},hit={_,_->},sceneInput={_->},dropInput={_->},action={_,_->},back={})
                    )
                    else->NativeBoardSceneRenderer.render(gui,font,scene.inset(4),view,boardUi,null)
                }
            }
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

                val chess=gameId=="chess"
                val board=if(chess) chessBoard() else tdBoard()
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
                    add("resultPresentation",if(chess)
                        presentation("victory","checkmate","chess",
                            listOf("moves" to "38","white_clock" to "82","black_clock" to "0"),
                            listOf("match_reward" to ""),listOf("match_complete" to ""))
                        else presentation("victory","defense_complete","tower_defense",
                            listOf("wave" to "20","lives" to "7","gold" to "132","towers" to "8"),
                            listOf("loot_count" to "5"),listOf("waves_cleared" to "20")))
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
            private fun tdBoard()=JsonArray().apply {
                val cells=MutableList(64){""}
                listOf(0,1,2,3,11,19,27,35,43,51,59,60,61,62,63).forEachIndexed{i,slot->cells[slot]=when(i){0->"path:start";14->"path:goal";else->"path"}}
                cells[18]="tower:pikachu:3:4:-1:thunderbolt:ATTACK_SPECIAL"
                cells[26]="tower:charizard:2:3:-1:flamethrower:ATTACK_SPECIAL"
                cells.forEach{add(it)}
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
