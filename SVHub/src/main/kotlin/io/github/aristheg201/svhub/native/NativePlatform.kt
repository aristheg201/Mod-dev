package io.github.aristheg201.svhub.native

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.companion.CompanionArena
import io.github.aristheg201.svhub.companion.VanillaCompanionService
import io.github.aristheg201.svhub.native.network.NativePlatformNetwork
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object NativePlatform {
    private var tickCounter = 0
    private val pendingOpen = ConcurrentHashMap<UUID, String>()
    fun start(root: Path) {
        NativeProfileStore.start(root.resolve("profiles"))
        NativeCosmeticService.start(root.resolve("cosmetic-store.json"))
        NativeArcadeService.start(root.resolve("arcade"))
        NativeGachaTransactionService.start(root.resolve("gacha"))
    }
    fun onJoin(player: ServerPlayer) {
        SkiesSkinsBridge.invalidate()
        NativeProfileStore.onJoin(player) { live ->
            NativeArcadeService.onReconnect(live)
            NativeCosmeticService.recover(live.uuid) { NativeRewardService.recoverPlayer(live.uuid) }
            NativeGachaService.recoverPlayer(live)
            pendingOpen.remove(live.uuid)?.let { requested -> open(live, requested) }
        }
    }
    fun onDisconnect(player: ServerPlayer) {
        pendingOpen.remove(player.uuid)
        NativePlatformNetwork.close(player.uuid)
        NativeArcadeService.onDisconnect(player)
        NativeProfileStore.onDisconnect(player)
    }
    fun tick(server: MinecraftServer) {
        tickCounter++
        NativeProfileStore.tick()
        val messages = NativeArcadeService.tick(server)
        server.playerList.players.forEach { p ->
            if (NativePlatformNetwork.currentModule(p.uuid) != "game") return@forEach
            val state = gameState(p)
            if (state.get("empty")?.asBoolean == true) {
                NativePlatformNetwork.sendOpen(p, "arcade", NativeArcadeService.lobbyState(p))
            } else {
                val message = messages[p.uuid].orEmpty()
                if (message.isNotBlank() || tickCounter % 20 == 0) NativePlatformNetwork.sendState(p, "game", state, message)
            }
        }
    }
    fun shutdown() {
        pendingOpen.clear()
        NativeGachaTransactionService.shutdown()
        NativeArcadeService.shutdown()
        NativeProfileStore.shutdown()
    }
    fun open(player: ServerPlayer, requested: String): Boolean {
        val module = requested.lowercase().trim().takeIf { it in MODULES } ?: "dashboard"
        if (!NativeProfileStore.isLoaded(player.uuid)) {
            pendingOpen[player.uuid] = module
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.svhub.profile_loading"))
            return false
        }
        pendingOpen.remove(player.uuid)
        if (module == "arcade" && NativeArcadeService.hasActiveSession(player)) {
            NativePlatformNetwork.sendOpen(player, "game", gameState(player))
        } else {
            NativePlatformNetwork.sendOpen(player, module, state(player, module))
        }
        return true
    }
    fun handleIntent(player: ServerPlayer, module: String, action: String, data: JsonObject): String {
        if (!NativeProfileStore.isLoaded(player.uuid)) return "gui.svhub.profile.loading"
        if (action == "open") { open(player, data.string("module", "dashboard")); return "" }
        return when (module) {
            "gacha" -> handleGacha(player, action, data)
            "skins" -> handleSkins(player, action, data)
            "store" -> NativeCosmeticService.handle(player, action, data)
            "arcade" -> handleArcade(player, action, data)
            "game" -> handleGame(player, action, data)
            "companions" -> handleCompanions(player, action, data)
            "wallet", "dashboard" -> when (action) {
                "open" -> { open(player, data.string("module", "dashboard")); "" }
                "close" -> { NativePlatformNetwork.close(player.uuid); "" }
                else -> "gui.svhub.error.no_action"
            }
            else -> "gui.svhub.error.invalid_module"
        }
    }
    fun state(player: ServerPlayer, module: String): JsonObject = when (module) {
        "gacha" -> NativeGachaService.state(player); "skins" -> NativeSkinService.state(player); "arcade" -> NativeArcadeService.lobbyState(player)
        "store" -> NativeCosmeticService.state(player)
        "game" -> gameState(player); "companions" -> companionState(player); "wallet" -> walletState(player); else -> dashboardState(player)
    }
    fun refresh(player: ServerPlayer, module: String) = NativePlatformNetwork.sendState(player, module, state(player, module))

    private fun handleGacha(player: ServerPlayer, action: String, data: JsonObject) = when (action) {
        "roll" -> NativeGachaService.requestRoll(
            player,
            data.string("banner", "hunter"),
            data.string("requestId")
        ).let { r ->
            NativePlatformNetwork.sendState(
                player,
                "gacha",
                r.state ?: NativeGachaService.state(player, data.string("banner", "hunter")),
                r.message
            )
            r.message
        }
        "select" -> {
            NativeGachaService.recoverPlayer(player)
            NativePlatformNetwork.sendState(player, "gacha", NativeGachaService.state(player, data.string("banner", "hunter")))
            ""
        }
        else -> "gui.svhub.error.invalid_action"
    }

    private fun handleSkins(player: ServerPlayer, action: String, data: JsonObject) = when (action) {
        "page", "source" -> {
            val source = data.string("source", "all").takeIf { it in setOf("all", "dbz", "naruto", "pokelegends", "hunter") } ?: "all"
            NativePlatformNetwork.sendState(player, "skins", NativeSkinService.state(player, data.int("page", 0), source)); ""
        }
        "buy", "shop" -> NativeSkinService.purchase(player, data.string("skin")).message
        "inventory" -> NativeSkinService.openInventory(player).message
        "equip" -> NativeSkinService.equip(player, data.string("skin"), data.int("slot")).let { r -> NativePlatformNetwork.sendState(player, "skins", NativeSkinService.state(player, data.int("page", 0), data.string("source", "all")), r.message); r.message }
        "unequip" -> NativeSkinService.unequip(player, data.int("slot")).let { r -> NativePlatformNetwork.sendState(player, "skins", NativeSkinService.state(player, data.int("page", 0), data.string("source", "all")), r.message); r.message }
        else -> "gui.svhub.error.invalid_action"
    }

    private fun handleArcade(player: ServerPlayer, action: String, data: JsonObject) = when (action) {
        "start" -> NativeArcadeService.start(player, data.string("game"), data.string("mode", "bot_normal")).let { r ->
            if (r.changedPlayers.isEmpty()) {
                NativePlatformNetwork.sendState(player, "arcade", NativeArcadeService.lobbyState(player), r.message)
            } else {
                r.changedPlayers.forEach { id ->
                    player.server.playerList.getPlayer(id)?.let { p ->
                        val activeState = NativeArcadeService.gameState(p)
                        if (activeState.get("empty")?.asBoolean == false) NativePlatformNetwork.sendOpen(p, "game", activeState)
                        else NativePlatformNetwork.sendState(p, "arcade", NativeArcadeService.lobbyState(p), r.message)
                    }
                }
            }
            r.message
        }
        "cancel_queue" -> NativeArcadeService.cancelQueue(player).let { r -> NativePlatformNetwork.sendState(player, "arcade", NativeArcadeService.lobbyState(player), r.message); r.message }
        "resume" -> { val s = gameState(player); if (s.get("empty")?.asBoolean == false) NativePlatformNetwork.sendOpen(player, "game", s) else NativePlatformNetwork.sendState(player, "arcade", NativeArcadeService.lobbyState(player), "gui.svhub.arcade.no_active"); "" }
        "leave_active" -> NativeArcadeService.leave(player).let { r -> NativePlatformNetwork.sendState(player, "arcade", NativeArcadeService.lobbyState(player), r.message); r.message }
        else -> "gui.svhub.error.invalid_action"
    }

    private fun handleGame(player: ServerPlayer, action: String, data: JsonObject): String {
        if (action == "leave") { val r = NativeArcadeService.leave(player); NativePlatformNetwork.sendOpen(player, "arcade", NativeArcadeService.lobbyState(player)); return r.message }
        if (action == "rematch") {
            val r=NativeArcadeService.rematch(player)
            val next=gameState(player)
            if(next.get("empty")?.asBoolean==false) NativePlatformNetwork.sendOpen(player,"game",next)
            else NativePlatformNetwork.sendOpen(player,"arcade",NativeArcadeService.lobbyState(player))
            return r.message
        }
        if (action != "act") return "gui.svhub.error.invalid_action"
        val args = linkedMapOf<String, String>()
        data.getAsJsonObject("args")?.entrySet()?.forEach { (k, v) -> if (k.length <= 32) args[k] = runCatching { v.asString }.getOrDefault("").take(128) }
        val r = NativeArcadeService.act(player, data.string("gameAction"), args)
        r.changedPlayers.forEach { id -> player.server.playerList.getPlayer(id)?.let { NativePlatformNetwork.sendState(it, "game", gameState(it), r.message) } }
        if (!r.ok && r.changedPlayers.isEmpty()) NativePlatformNetwork.sendState(player, "game", gameState(player), r.message)
        return r.message
    }

    private fun handleCompanions(player: ServerPlayer, action: String, data: JsonObject): String {
        val msg = when (action) {
            "select" -> { val entity = data.string("entity"); if (VanillaCompanionService.select(player, entity)) "gui.svhub.companion.selected" else "gui.svhub.companion.invalid" }
            "arena_start" -> {
                val selected = VanillaCompanionService.selectedFor(player.uuid)
                if (selected.isNullOrBlank()) "gui.svhub.arena.select_first"
                else { CompanionArena.start(player.uuid, selected); "gui.svhub.arena.started" }
            }
            "arena_act" -> { CompanionArena.act(player.uuid, data.string("move")); "gui.svhub.arena.updated" }
            else -> "gui.svhub.error.invalid_action"
        }
        NativePlatformNetwork.sendState(player, "companions", companionState(player), msg); return msg
    }

    private fun dashboardState(player: ServerPlayer) = JsonObject().apply {
        addProperty("module", "dashboard"); addProperty("skinBackend", "SkiesSkins"); addProperty("skinBackendReady", SkiesSkinsBridge.available())
        add("wallet", playerWallet(player)); addProperty("ownedSkins", SkiesSkinsBridge.ownedCount(player)); addProperty("skinTotal", NativeSkinCatalog.all.size); addProperty("gameCount", NativeArcadeService.games.size)
        add("features", JsonArray().also { a -> listOf("gacha", "skins", "companions", "arcade", "store", "wallet").forEach(a::add) })
    }
    private fun playerWallet(player: ServerPlayer) = NativeSkinService.walletJson(NativeProfileStore.get(player.uuid) ?: NativeProfile()).apply {
        NativeCosmeticService.balances(player.uuid).entrySet().forEach { (key, value) -> add(key, value) }
    }
    private fun walletState(player: ServerPlayer) = JsonObject().apply { addProperty("module", "wallet"); add("wallet", playerWallet(player)); addProperty("ownedSkins", SkiesSkinsBridge.ownedCount(player)); addProperty("skinBackend", "SkiesSkins") }
    private fun companionState(player: ServerPlayer) = JsonObject().apply {
        addProperty("module", "companions"); addProperty("selected", VanillaCompanionService.selectedFor(player.uuid).orEmpty())
        add("companions", JsonArray().also { a -> COMPANIONS.forEach { (id, name) -> a.add(JsonObject().apply { addProperty("id", id); addProperty("name", name); CompanionArena.roster[id]?.let { fighter -> addProperty("hp", fighter.maxHp); addProperty("power", fighter.power); addProperty("guard", fighter.guard); addProperty("speed", fighter.speed) } }) } })
        CompanionArena.appendState(player.uuid, this)
    }
    private fun gameState(player: ServerPlayer) = NativeArcadeService.gameState(player)
    private val MODULES = setOf("dashboard", "gacha", "skins", "store", "companions", "arcade", "game", "wallet")
    private val COMPANIONS = linkedMapOf("allay" to "Allay", "axolotl" to "Axolotl", "bee" to "Bee", "cat" to "Cat", "fox" to "Fox", "frog" to "Frog", "parrot" to "Parrot", "rabbit" to "Rabbit", "wolf" to "Wolf", "armadillo" to "Armadillo", "sniffer" to "Sniffer")
}
