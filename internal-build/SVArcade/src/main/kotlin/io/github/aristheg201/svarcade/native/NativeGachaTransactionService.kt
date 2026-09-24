package io.github.aristheg201.svarcade.native

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.SVArcade
import io.github.aristheg201.svarcade.SVArcadeRuntime
import io.github.aristheg201.svarcade.native.network.NativePlatformNetwork
import io.github.aristheg201.svarcade.util.AtomicFiles
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

object NativeGachaTransactionService {
    private data class Journal(
        val schema: Int = 1,
        val requestId: String,
        val playerId: String,
        val bannerId: String,
        val winnerId: String,
        val winnerName: String,
        val species: String,
        val aspect: String,
        val source: String,
        val rarity: String,
        val perfectIvs: Int,
        val bonusPokemonUuid: String? = null,
        val bonusPokemonSpecies: String? = null,
        val ticketCost: Int,
        val newPity: Int,
        val beforeQuantity: Int,
        val seed: Long,
        val createdAtEpochMs: Long = System.currentTimeMillis(),
        val stage: String = PREPARED
    )

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val pending = ConcurrentHashMap<String, Journal>()
    private val inFlight = ConcurrentHashMap.newKeySet<UUID>()
    private val threadCounter = AtomicInteger()
    @Volatile private var executor: ThreadPoolExecutor? = null
    private lateinit var journalRoot: Path

    fun start(root: Path) {
        journalRoot = root.resolve("journal")
        Files.createDirectories(journalRoot)
        if (executor == null) synchronized(this) {
            if (executor == null) {
                executor = ThreadPoolExecutor(
                    1, 1, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(128),
                    { task -> Thread(task, "SVArcade-Gacha-" + threadCounter.incrementAndGet()).apply {
                        isDaemon = true
                        priority = Thread.NORM_PRIORITY - 1
                    } },
                    ThreadPoolExecutor.AbortPolicy()
                )
            }
        }
        loadPending()
    }

    fun shutdown() {
        val pool = synchronized(this) { val p = executor; executor = null; p }
        pool?.shutdown()
        runCatching { pool?.awaitTermination(5, TimeUnit.SECONDS) }
        pool?.shutdownNow()
        pending.clear()
        inFlight.clear()
    }

    fun activeRequest(playerId: UUID): String? =
        pending.values.firstOrNull { it.playerId == playerId.toString() && !refunded(it) }?.requestId
            ?: if (playerId in inFlight) "processing" else null

    fun request(player: ServerPlayer, bannerId: String, requestId: String): NativeGachaService.RollResult {
        val parsed = runCatching { UUID.fromString(requestId) }.getOrNull()
            ?: return fail(player, "gui.svarcade.gacha.invalid_request")
        if (parsed.toString() != requestId.lowercase()) return fail(player, "gui.svarcade.gacha.invalid_request")
        if (!SkiesSkinsBridge.available()) return fail(player, "gui.svarcade.gacha.backend_unavailable")
        val banner = NativeGachaService.banners.firstOrNull { it.id == bannerId }
            ?: return fail(player, "gui.svarcade.gacha.invalid_banner")
        val profile = NativeProfileStore.get(player.uuid)
            ?: return fail(player, "gui.svarcade.gacha.profile_loading")
        if (profile.gachaTickets < banner.costTickets) return fail(player, "gui.svarcade.gacha.not_enough_tickets")

        if (
            pending.containsKey(requestId) ||
            NativeProfileStore.hasTransaction(player.uuid, debitTx(requestId)) ||
            NativeProfileStore.hasTransaction(player.uuid, finalTx(requestId)) ||
            NativeProfileStore.hasTransaction(player.uuid, refundTx(requestId))
        ) return fail(player, "gui.svarcade.gacha.duplicate_request")

        if (pending.values.any { it.playerId == player.uuid.toString() } || !inFlight.add(player.uuid)) {
            return fail(player, "gui.svarcade.gacha.busy")
        }

        val pool = NativeGachaService.poolFor(banner)
        if (pool.isEmpty()) {
            inFlight.remove(player.uuid)
            return fail(player, "gui.svarcade.gacha.empty_banner")
        }

        val oldPity = profile.pity[banner.id] ?: 0
        val seed = java.util.concurrent.ThreadLocalRandom.current().nextLong()
        val winner = NativeGachaService.pickForRoll(pool, oldPity + 1 >= banner.pity, Random(seed))
        val bonus = NativeSkinService.prepareBonusPokemon(requestId, seed, winner.perfectIvs)
        if (winner.perfectIvs > 0 && bonus == null) {
            inFlight.remove(player.uuid)
            return fail(player, "gui.svarcade.gacha.bonus_unavailable")
        }
        val record = Journal(
            requestId = requestId,
            playerId = player.uuid.toString(),
            bannerId = banner.id,
            winnerId = winner.id,
            winnerName = winner.name,
            species = winner.species,
            aspect = winner.aspect,
            source = winner.source,
            rarity = winner.rarity,
            perfectIvs = winner.perfectIvs,
            bonusPokemonUuid = bonus?.uuid,
            bonusPokemonSpecies = bonus?.species,
            ticketCost = banner.costTickets,
            newPity = if (NativeGachaService.isPremium(winner.rarity)) 0 else oldPity + 1,
            beforeQuantity = SkiesSkinsBridge.ownedQuantity(player, winner.id),
            seed = seed
        )

        if (!persist(record) { ok ->
                if (ok) begin(record) else {
                    inFlight.remove(player.uuid)
                    pushFailure(player, "gui.svarcade.gacha.persist_failed")
                }
            }) {
            inFlight.remove(player.uuid)
            return fail(player, "gui.svarcade.gacha.busy")
        }

        return NativeGachaService.RollResult(
            true, "",
            NativeGachaService.state(player, banner.id).apply {
                addProperty("rolling", true)
                addProperty("requestId", requestId)
            }
        )
    }

    fun recoverPlayer(player: ServerPlayer) {
        val records = pending.values
            .filter { it.playerId == player.uuid.toString() }
            .sortedBy { it.createdAtEpochMs }
        records.filter(::refunded).forEach(::cleanup)
        val active = records.firstOrNull { !refunded(it) } ?: return
        if (inFlight.add(player.uuid)) begin(active)
    }

    private fun begin(record: Journal) {
        val id = playerId(record) ?: return
        if (refunded(record)) { cleanup(record); inFlight.remove(id); return }
        val player = SVArcadeRuntime.server?.playerList?.getPlayer(id) ?: run { inFlight.remove(id); return }
        if (finalized(record)) { completeSuccess(record, player); return }
        val accepted = NativeProfileStore.mutateDurableOnce(
            id, debitTx(record.requestId),
            mutation = { profile ->
                if (profile.gachaTickets < record.ticketCost) false
                else profile.debit("ticket", record.ticketCost.toLong())
            }
        ) { result ->
            when (result) {
                DurableMutationResult.APPLIED, DurableMutationResult.ALREADY_APPLIED -> afterDebit(record)
                DurableMutationResult.REJECTED -> abort(record, player, "gui.svarcade.gacha.not_enough_tickets")
            }
        }
        if (!accepted) inFlight.remove(id)
    }

    private fun afterDebit(record: Journal) {
        when (record.stage) {
            PREPARED -> {
                val granting = record.copy(stage = GRANTING)
                if (!persist(granting) { ok -> if (ok) grantOrRecover(granting) else release(record) }) release(record)
            }
            GRANTING -> grantOrRecover(record)
            GRANTED -> finalizeWin(record)
            REFUNDING -> refund(record)
            else -> release(record)
        }
    }

    private fun grantOrRecover(record: Journal) {
        val id = playerId(record) ?: return
        val player = SVArcadeRuntime.server?.playerList?.getPlayer(id) ?: run { inFlight.remove(id); return }
        if (SkiesSkinsBridge.ownedQuantity(player, record.winnerId) > record.beforeQuantity) {
            persistGranted(record)
            return
        }
        if (!SkiesSkinsBridge.grant(player, record.winnerId, 1)) {
            val refunding = record.copy(stage = REFUNDING)
            if (!persist(refunding) { ok -> if (ok) refund(refunding) else release(record) }) release(record)
            return
        }
        persistGranted(record)
    }

    private fun persistGranted(record: Journal) {
        if (record.stage == GRANTED) { finalizeWin(record); return }
        val granted = record.copy(stage = GRANTED)
        if (!persist(granted) { ok -> if (ok) finalizeWin(granted) else release(record) }) release(record)
    }

    private fun finalizeWin(record: Journal) {
        val id = playerId(record) ?: return
        val accepted = NativeProfileStore.mutateDurableOnce(
            id, finalTx(record.requestId),
            mutation = { profile ->
                if (profile.appliedTransactions.containsKey(refundTx(record.requestId))) false
                else { profile.pity[record.bannerId] = record.newPity; true }
            }
        ) { result ->
            when (result) {
                DurableMutationResult.APPLIED, DurableMutationResult.ALREADY_APPLIED ->
                    completeSuccess(record, SVArcadeRuntime.server?.playerList?.getPlayer(id))
                DurableMutationResult.REJECTED -> release(record)
            }
        }
        if (!accepted) release(record)
    }

    private fun refund(record: Journal) {
        val id = playerId(record) ?: return
        val accepted = NativeProfileStore.mutateDurableOnce(
            id, refundTx(record.requestId),
            mutation = { profile ->
                if (profile.appliedTransactions.containsKey(finalTx(record.requestId))) false
                else { profile.credit("ticket", record.ticketCost.toLong()); true }
            }
        ) { result ->
            when (result) {
                DurableMutationResult.APPLIED, DurableMutationResult.ALREADY_APPLIED ->
                    completeFailure(record, SVArcadeRuntime.server?.playerList?.getPlayer(id), "gui.svarcade.gacha.grant_failed")
                DurableMutationResult.REJECTED -> release(record)
            }
        }
        if (!accepted) release(record)
    }

    private fun completeSuccess(record: Journal, player: ServerPlayer?) {
        val id = playerId(record) ?: return
        if (player == null) { inFlight.remove(id); return }

        if (record.perfectIvs > 0) {
            val bonus = bonusSpec(record)
            if (bonus == null) {
                inFlight.remove(id)
                pushFailure(player, "gui.svarcade.gacha.bonus_pending")
                return
            }
            when (NativeSkinService.ensureBonusPokemon(player, bonus)) {
                NativeSkinService.BonusDeliveryResult.RETRY -> {
                    inFlight.remove(id)
                    pushFailure(player, "gui.svarcade.gacha.bonus_pending")
                    return
                }
                NativeSkinService.BonusDeliveryResult.ALREADY_PRESENT,
                NativeSkinService.BonusDeliveryResult.DELIVERED -> Unit
            }
        }

        deleteRecord(record)
        inFlight.remove(id)
        val winner = NativeSkin(
            record.winnerId, record.winnerName, record.species, record.aspect, record.source,
            "", 0L, record.perfectIvs, record.rarity, true
        )
        val banner = NativeGachaService.banners.firstOrNull { it.id == record.bannerId } ?: NativeGachaService.banners.first()
        val result = NativeGachaService.state(player, record.bannerId).apply {
            add("lastRoll", JsonObject().apply {
                addProperty("requestId", record.requestId)
                addProperty("banner", record.bannerId)
                addProperty("winnerId", record.winnerId)
                addProperty("winnerName", record.winnerName)
                addProperty("species", record.species)
                addProperty("aspect", record.aspect)
                addProperty("source", record.source)
                addProperty("rarity", record.rarity)
                addProperty("pity", record.newPity)
                addProperty("seed", record.seed)
            })
            add("strip", NativeGachaService.buildStrip(NativeGachaService.poolFor(banner), winner, record.seed))
            addProperty("rolling", false)
        }
        if (NativePlatformNetwork.currentModule(player.uuid) == "gacha") {
            NativePlatformNetwork.sendState(player, "gacha", result, "")
        }
    }

    private fun completeFailure(record: Journal, player: ServerPlayer?, key: String) {
        deleteRecord(record)
        playerId(record)?.let(inFlight::remove)
        if (player != null) pushFailure(player, key)
    }

    private fun abort(record: Journal, player: ServerPlayer?, key: String) {
        deleteRecord(record)
        playerId(record)?.let(inFlight::remove)
        if (player != null) pushFailure(player, key)
    }

    private fun pushFailure(player: ServerPlayer, key: String) {
        if (NativePlatformNetwork.currentModule(player.uuid) == "gacha") {
            NativePlatformNetwork.sendState(
                player, "gacha", NativeGachaService.state(player).apply { addProperty("errorKey", key) }, ""
            )
        }
    }

    private fun fail(player: ServerPlayer, key: String) =
        NativeGachaService.RollResult(false, "", NativeGachaService.state(player).apply { addProperty("errorKey", key) })

    private fun release(record: Journal) { playerId(record)?.let(inFlight::remove) }

    private fun persist(record: Journal, done: (Boolean) -> Unit): Boolean {
        val pool = executor ?: return false
        return try {
            pool.execute {
                val ok = runCatching {
                    AtomicFiles.writeUtf8(journalPath(record.requestId), gson.toJson(record))
                    true
                }.onFailure { SVArcade.LOGGER.error("Unable to persist gacha journal {}", record.requestId, it) }
                    .getOrDefault(false)
                if (ok) pending[record.requestId] = record
                SVArcadeRuntime.server?.execute { done(ok) }
            }
            true
        } catch (_: RejectedExecutionException) { false }
    }

    private fun deleteRecord(record: Journal) {
        pending.remove(record.requestId)
        val pool = executor ?: return
        try {
            pool.execute {
                runCatching { Files.deleteIfExists(journalPath(record.requestId)) }
                    .onFailure { SVArcade.LOGGER.warn("Unable to delete gacha journal {}", record.requestId, it) }
            }
        } catch (_: RejectedExecutionException) { }
    }

    private fun cleanup(record: Journal) { deleteRecord(record); playerId(record)?.let(inFlight::remove) }

    private fun finalized(record: Journal): Boolean {
        val id = playerId(record) ?: return false
        return NativeProfileStore.isTransactionDurable(id, finalTx(record.requestId))
    }

    private fun refunded(record: Journal): Boolean {
        val id = playerId(record) ?: return false
        return NativeProfileStore.isTransactionDurable(id, refundTx(record.requestId))
    }

    private fun bonusSpec(record: Journal): NativeSkinService.BonusPokemonSpec? {
        if (record.perfectIvs <= 0) return null
        val uuid = record.bonusPokemonUuid?.takeIf { it.isNotBlank() }
        val species = record.bonusPokemonSpecies?.takeIf { it.isNotBlank() }
        return if (uuid != null && species != null) {
            NativeSkinService.BonusPokemonSpec(uuid, species, record.perfectIvs.coerceIn(1, 6))
        } else {
            NativeSkinService.prepareBonusPokemon(record.requestId, record.seed, record.perfectIvs)
        }
    }

    private fun loadPending() {
        if (!::journalRoot.isInitialized || !Files.isDirectory(journalRoot)) return
        runCatching {
            Files.list(journalRoot).use { files ->
                files.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }.forEach { file ->
                    runCatching { gson.fromJson(Files.readString(file), Journal::class.java) }
                        .onFailure { SVArcade.LOGGER.warn("Unable to read gacha journal {}", file, it) }
                        .getOrNull()?.takeIf { it.requestId.isNotBlank() && it.playerId.isNotBlank() }
                        ?.let { pending[it.requestId] = it }
                }
            }
        }.onFailure { SVArcade.LOGGER.warn("Unable to scan gacha journal", it) }
    }

    private fun playerId(record: Journal) = runCatching { UUID.fromString(record.playerId) }.getOrNull()
    private fun journalPath(requestId: String) = journalRoot.resolve(requestId + ".json")
    private fun debitTx(requestId: String) = "gacha:" + requestId + ":debit"
    private fun finalTx(requestId: String) = "gacha:" + requestId + ":final"
    private fun refundTx(requestId: String) = "gacha:" + requestId + ":refund"

    private const val PREPARED = "PREPARED"
    private const val GRANTING = "GRANTING"
    private const val GRANTED = "GRANTED"
    private const val REFUNDING = "REFUNDING"
}
