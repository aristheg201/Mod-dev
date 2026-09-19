package io.github.aristheg201.svhub.native.game

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.game.tft.TftCombatEngine
import io.github.aristheg201.svhub.native.game.tft.TftCombatSnapshot
import io.github.aristheg201.svhub.native.game.tft.TftCombatUnit
import io.github.aristheg201.svhub.native.game.tft.TftDefinitionValidator
import io.github.aristheg201.svhub.native.game.tft.TftOwnedUnit
import io.github.aristheg201.svhub.native.game.tft.TftPveRoundDefinition
import io.github.aristheg201.svhub.native.game.tft.TftSetDefinition
import io.github.aristheg201.svhub.native.game.tft.TftSetRegistry
import io.github.aristheg201.svhub.native.game.tft.TftTraitTier
import io.github.aristheg201.svhub.native.game.tft.TftProgression
import io.github.aristheg201.svhub.native.game.tft.TftProgressionDefinition
import io.github.aristheg201.svhub.native.game.tft.TftPlayerModifier
import io.github.aristheg201.svhub.native.game.tft.TftPlayerModifierSet
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** Server-authoritative TFT rules engine for Cobblemon. */
class TftSession(
    override val seats: List<NativeSeat>,
    seed: Long = Random.nextLong(),
    override val sessionId: String = NativeIds.session("tft"),
    definition: TftSetDefinition = TftSetRegistry.active(),
    private val restoreState: JsonObject? = null,
    tacticianSelections: Map<String, String> = emptyMap()
) : NativeGameSession {
    override val gameId: String = "tft"
    private val set = TftDefinitionValidator.validate(TftSetRegistry.migrateDefinition(definition))
    private val progression = set.progression ?: TftProgressionDefinition(maxLevel = set.maxLevel, xpToNextByLevel = set.xpToNextByLevel)
    private val rng = NativeStatefulRandom(seed)
    private val unitDefs = set.units.associateBy { it.id }
    private val traitDefs = set.traits.associateBy { it.id }
    private val augmentDefs = set.augments.associateBy { it.id }
    private val itemRecipes = set.fullItems.associateBy { it.components.sorted().joinToString("+") }
    private val pool = SharedPool(set, rng)
    private val shopSlots = set.rules.shopSlots
    private val benchSlots = set.rules.benchSlots
    private val formationCells = set.rules.formationCells
    private val players = linkedMapOf<String, PlayerState>()
    private val combats = linkedMapOf<String, MatchCombat>()
    private val scoutTargets = mutableMapOf<String, String>()
    private val draftOffers = mutableListOf<DraftOffer>()
    private val log = ArrayDeque<String>()
    private var phase: Phase = Phase.PLANNING
    private var roundIndex = 0
    private var phaseEndsAt = 0L
    private var lastTickAt = System.currentTimeMillis()
    private var nextUnitSerial = 1L
    private var revision = 0L
    private var result: String? = null
    private var winner: String? = null

    init {
        require(seats.size in 2..8) { "Pokémon TFT requires 2-8 trainers" }
        if (restoreState == null) {
            seats.forEach { seat -> players[seat.id] = PlayerState(seat.id, seat.name, bench = MutableList(benchSlots) { null }, shop = MutableList(shopSlots) { null }, tactician = resolveTactician(tacticianSelections[seat.id])) }
            startPlanning(System.currentTimeMillis(), firstRound = true)
        } else {
            restoreSnapshot(restoreState)
        }
    }

    override val finished: Boolean get() = result != null
    override val winnerSeatId: String? get() = winner

    override fun snapshotState(nowMillis: Long): JsonObject = NativeGamePersistence.toJson(
        Snapshot(
            schema = 2,
            setDefinition = set,
            phase = phase.name,
            roundIndex = roundIndex,
            phaseRemainingMs = (phaseEndsAt - nowMillis).coerceAtLeast(0L),
            nextUnitSerial = nextUnitSerial,
            revision = revision,
            result = result,
            winner = winner,
            rngState = rng.state,
            poolCounts = pool.snapshotCounts(),
            players = players.values.map { player ->
                PlayerSnapshot(
                    id = player.id,
                    name = player.name,
                    hp = player.hp,
                    gold = player.gold,
                    level = player.level,
                    xp = player.xp,
                    streak = player.streak,
                    lastOutcome = player.lastOutcome,
                    eliminated = player.eliminated,
                    placement = player.placement,
                    lastOpponentId = player.lastOpponentId,
                    bench = player.bench.map { owned -> owned?.copy(items = owned.items.toMutableList()) },
                    board = player.board.entries.sortedBy { it.key }.map { entry ->
                        BoardSnapshot(entry.key, entry.value.copy(items = entry.value.items.toMutableList()))
                    },
                    shop = player.shop.toList(),
                    itemBench = player.itemBench.toList(),
                    augments = player.augments.toList(),
                    augmentChoices = player.augmentChoices.toList(),
                    freeRerolls = player.freeRerolls,
                    draftPicked = player.draftPicked,
                    draftUnlockRemainingMs = (player.draftUnlockAt - nowMillis).coerceAtLeast(0L),
                    lastIncome = player.lastIncome,
                    lastInterest = player.lastInterest,
                    lastStreakGold = player.lastStreakGold,
                    lastSettledRound = player.lastSettledRound,
                    lastXpGranted = player.lastXpGranted,
                    lastLevelsGained = player.lastLevelsGained,
                    legacyIncomePending = player.legacyIncomePending,
                    tactician = player.tactician
                )
            },
            draftOffers = draftOffers.map { offer ->
                DraftSnapshot(offer.index, offer.unitId, offer.itemId, offer.takenBy)
            },
            combats = combats.values.distinctBy { System.identityHashCode(it) }.map { match ->
                MatchSnapshot(
                    aId = match.aId,
                    bId = match.bId,
                    opponentLabel = match.opponentLabel,
                    pveRound = match.pve?.round,
                    ghostOwnerId = match.ghostOwnerId,
                    resolved = match.resolved,
                    combat = match.engine.snapshotState()
                )
            },
            log = log.toList()
        )
    )

    private fun restoreSnapshot(state: JsonObject) {
        val saved = NativeGamePersistence.fromJson(state, Snapshot::class.java)
        require(saved.schema in 1..2) { "Unsupported TFT session snapshot schema " + saved.schema }
        require(saved.setDefinition.id == set.id) { "TFT set mismatch during recovery" }
        require(saved.players.map { it.id }.toSet() == seats.map { it.id }.toSet()) {
            "TFT recovery seat mismatch"
        }
        val now = System.currentTimeMillis()
        val seatById = seats.associateBy { it.id }

        phase = Phase.entries.firstOrNull { it.name == saved.phase }
            ?: error("Unknown TFT recovery phase " + saved.phase)
        roundIndex = saved.roundIndex.coerceAtLeast(0)
        phaseEndsAt = if (phase == Phase.FINISHED) 0L
            else now + saved.phaseRemainingMs.coerceIn(0L, 600_000L)
        nextUnitSerial = saved.nextUnitSerial.coerceAtLeast(1L)
        revision = saved.revision.coerceAtLeast(0L)
        result = saved.result
        winner = saved.winner?.takeIf(seatById::containsKey)
        rng.restore(saved.rngState)
        pool.restoreCounts(saved.poolCounts)

        players.clear()
        saved.players.forEach { p ->
            val seat = seatById.getValue(p.id)
            val bench = p.bench.take(benchSlots).map { owned ->
                owned?.takeIf { unit -> unit.unitId in unitDefs }?.let { unit -> unit.copy(items = unit.items.toMutableList()) }
            }.toMutableList()
            while (bench.size < benchSlots) bench.add(null)

            val board = linkedMapOf<Int, TftOwnedUnit>()
            p.board.filter { it.slot in 0 until formationCells }
                .distinctBy { it.slot }
                .forEach { entry ->
                    if (entry.unit.unitId in unitDefs) {
                        board[entry.slot] = entry.unit.copy(items = entry.unit.items.toMutableList())
                    }
                }

            val shop = p.shop.take(shopSlots).toMutableList()
            while (shop.size < shopSlots) shop.add(null)
            shop.indices.forEach { index ->
                val id = shop[index]
                if (id != null && id !in unitDefs) shop[index] = null
            }

            players[p.id] = PlayerState(
                id = p.id,
                name = seat.name,
                hp = p.hp.coerceIn(-10_000, 100),
                gold = p.gold.coerceIn(0, MAX_GOLD),
                level = p.level.coerceIn(2, progression.maxLevel),
                xp = if (p.level >= progression.maxLevel) 0 else p.xp.coerceAtLeast(0),
                streak = p.streak,
                lastOutcome = p.lastOutcome.coerceIn(-1, 1),
                eliminated = p.eliminated,
                placement = p.placement?.coerceIn(1, seats.size),
                lastOpponentId = p.lastOpponentId?.takeIf(seatById::containsKey),
                bench = bench,
                board = board,
                shop = shop,
                itemBench = p.itemBench.take(128).toMutableList(),
                augments = p.augments.filter(augmentDefs::containsKey).toMutableList(),
                augmentChoices = p.augmentChoices.filter(augmentDefs::containsKey).toMutableList(),
                freeRerolls = p.freeRerolls.coerceIn(0, 100),
                draftPicked = p.draftPicked,
                draftUnlockAt = now + p.draftUnlockRemainingMs.coerceIn(0L, DRAFT_TOTAL_MS),
                lastIncome = p.lastIncome.coerceAtLeast(0),
                lastInterest = p.lastInterest.coerceAtLeast(0),
                lastStreakGold = p.lastStreakGold.coerceAtLeast(0),
                lastSettledRound = if (saved.schema >= 2) p.lastSettledRound else if (phase == Phase.POST_COMBAT || phase == Phase.FINISHED) roundIndex else roundIndex - 1,
                lastXpGranted = if (saved.schema >= 2) p.lastXpGranted else 0,
                lastLevelsGained = if (saved.schema >= 2) p.lastLevelsGained else 0,
                legacyIncomePending = if (saved.schema >= 2) p.legacyIncomePending else phase == Phase.POST_COMBAT,
                tactician = resolveTactician(p.tactician)
            )
        }

        draftOffers.clear()
        saved.draftOffers.take(32)
            .filter { it.unitId in unitDefs }
            .forEach { offer ->
                draftOffers += DraftOffer(
                    offer.index,
                    offer.unitId,
                    offer.itemId,
                    offer.takenBy?.takeIf(players::containsKey)
                )
            }

        combats.clear()
        if (phase == Phase.COMBAT) {
            saved.combats.take(8).forEach { savedMatch ->
                require(savedMatch.aId in players) {
                    "TFT recovery combat references missing player " + savedMatch.aId
                }
                require(savedMatch.bId == null || savedMatch.bId in players) {
                    "TFT recovery combat references missing opponent"
                }
                val pve = savedMatch.pveRound?.let { round ->
                    set.pveRounds.firstOrNull { it.round == round }
                }
                val match = MatchCombat(
                    aId = savedMatch.aId,
                    bId = savedMatch.bId,
                    opponentLabel = savedMatch.opponentLabel,
                    engine = TftCombatEngine(set, savedMatch.combat),
                    pve = pve,
                    ghostOwnerId = savedMatch.ghostOwnerId?.takeIf(players::containsKey),
                    resolved = savedMatch.resolved
                )
                combats[savedMatch.aId] = match
                savedMatch.bId?.let { id -> combats[id] = match }
            }
        }

        log.clear()
        saved.log.takeLast(40).forEach(log::add)
        lastTickAt = now
    }

    override fun viewFor(viewerId: String): NativeGameView {
        val player = players[viewerId] ?: players.values.first()
        val observed = scoutTargets[viewerId]?.let(players::get) ?: player
        val scouting = observed.id != player.id
        val board = buildBoardView(observed)
        val shopEnabled = phase == Phase.PLANNING && !player.eliminated
        val cards = player.shop.mapIndexedNotNull { index, unitId ->
            val def = unitId?.let(unitDefs::get) ?: return@mapIndexedNotNull null
            NativeCardView(
                id = "shop:$index",
                label = def.id,
                subtitle = "${def.cost}g • ${def.traits.take(3).joinToString(" /")}",
                accent = "cost${def.cost}",
                value = def.cost,
                meta = mapOf(
                    "unit" to def.id,
                    "species" to def.presentation.species,
                    "aspects" to def.presentation.resolverAspects().joinToString(","),
                    "traits" to def.traits.joinToString(","),
                    "role" to def.role,
                    "enabled" to shopEnabled.toString()
                )
            )
        }
        val actions = buildList {
            val refreshGold = refreshCost(player)
            val xpGold = playerModifiers(player).apply(TftPlayerModifier.XP_PURCHASE_COST, progression.buyXp.goldCost.toDouble()).toInt().coerceAtLeast(0)
            add(NativeActionView("refresh", "Refresh", "${refreshGold}g", shopEnabled && player.gold >= refreshGold))
            add(NativeActionView("buy_xp", "Buy XP", "${xpGold}g", shopEnabled && player.gold >= xpGold && player.level < progression.maxLevel))
            add(NativeActionView("resign", "Resign", "", !finished && !player.eliminated))
        }
        val active = combatFor(player.id)
        val opponent = active?.opponentNameFor(player.id).orEmpty()
        val status = when {
            finished -> result.orEmpty()
            player.eliminated -> "Eliminated • #${player.placement ?: "?"}"
            phase == Phase.DRAFT -> "Shared Draft • ${secondsLeft()}s"
            phase == Phase.PLANNING -> "${roundLabel()} • Planning ${secondsLeft()}s"
            phase == Phase.COMBAT -> "${roundLabel()} • ${if (opponent.isBlank()) "PvE" else "vs $opponent"}"
            else -> "${roundLabel()} • Results"
        }
        return NativeGameView(
            sessionId = sessionId,
            gameId = gameId,
            title = "Pokémon TFT • ${set.name}",
            phase = phase.id,
            turn = "",
            status = status,
            boardWidth = 7,
            boardHeight = 8,
            board = board,
            cards = cards,
            actions = actions,
            fields = linkedMapOf(
                "set" to set.id,
                "tacticianEntity" to set.tacticians.firstOrNull { it.id == observed.tactician }?.entity.orEmpty(),
                "tacticianId" to observed.tactician,
                "scouting" to scouting.toString(),
                "scoutTarget" to observed.id,
                "scoutName" to observed.name,
                "round" to roundLabel(),
                "roundIndex" to roundIndex.toString(),
                "phaseEndsAt" to phaseEndsAt.toString(),
                "gold" to player.gold.toString(),
                "hp" to player.hp.toString(),
                "level" to player.level.toString(),
                "xp" to player.xp.toString(),
                "xpNext" to xpToNext(player.level).toString(),
                "xpGranted" to player.lastXpGranted.toString(),
                "levelsGained" to player.lastLevelsGained.toString(),
                "settledRound" to player.lastSettledRound.toString(),
                "shopOdds" to set.shopOdds.first { it.level == player.level }.odds.joinToString(","),
                "buyXpCost" to progression.buyXp.goldCost.toString(),
                "buyXpAmount" to progression.buyXp.xpGranted.toString(),
                "unitCap" to unitCap(player).toString(),
                "boardCount" to player.board.size.toString(),
                "streak" to player.streak.toString(),
                "placement" to (player.placement ?: 0).toString(),
                "eliminated" to player.eliminated.toString(),
                "bench" to encodeBench(observed),
                "players" to encodePlayers(),
                "traits" to encodeTraits(observed),
                "unitCatalog" to encodeUnitCatalog(observed, includeShop = !scouting),
                "traitCatalog" to encodeTraitCatalog(observed, includeShop = !scouting),
                "itemBench" to player.itemBench.joinToString(","),
                "augments" to player.augments.joinToString(","),
                "selectedAugments" to encodeSelectedAugments(observed),
                "augmentChoices" to encodeAugmentChoices(player),
                "draft" to encodeDraft(player),
                "opponent" to opponent,
                "lastIncome" to player.lastIncome.toString(),
                "lastInterest" to player.lastInterest.toString(),
                "lastStreakGold" to player.lastStreakGold.toString(),
                "freeRerolls" to player.freeRerolls.toString(),
                "canEditBoard" to (phase == Phase.PLANNING && !player.eliminated && !scouting).toString()
            ),
            log = log.toList().takeLast(12),
            revision = revision,
            finished = finished,
            winner = winner?.let { id -> seats.firstOrNull { it.id == id }?.name }
        )
    }

    override fun act(viewerId: String, action: String, args: Map<String, String>): NativeGameResult {
        if (action == "scout") return scout(viewerId, args["target"])
        if (finished) return NativeGameResult(false, message = "Game finished")
        val player = players[viewerId] ?: return NativeGameResult(false, message = "Spectator")
        if (player.eliminated) return NativeGameResult(false, message = "Trainer eliminated")
        return when (action) {
            "buy" -> buy(player, args["index"]?.toIntOrNull())
            "refresh" -> refresh(player)
            "buy_xp" -> buyXp(player)
            "deploy" -> deploy(player, args["bench"]?.toIntOrNull(), args["slot"]?.toIntOrNull())
            "move" -> move(player, args["from"]?.toIntOrNull(), args["to"]?.toIntOrNull())
            "bench" -> bench(player, args["slot"]?.toIntOrNull(), args["bench"]?.toIntOrNull())
            "swap_bench" -> swapBench(player, args["from"]?.toIntOrNull(), args["to"]?.toIntOrNull())
            "sell" -> sell(player, args)
            "equip_item" -> equipItem(player, args)
            "choose_augment" -> chooseAugment(player, args["id"])
            "draft_pick" -> draftPick(player, args["index"]?.toIntOrNull())
            "resign" -> resign(player)
            else -> NativeGameResult(false, message = "Unknown TFT action")
        }
    }

    private fun scout(viewerId: String, target: String?): NativeGameResult {
        if (viewerId !in players) return reject("Spectator")
        val ids = players.keys.toList()
        val current = scoutTargets[viewerId] ?: viewerId
        val id = when (target) {
            "home" -> viewerId
            "previous" -> ids[Math.floorMod(ids.indexOf(current) - 1, ids.size)]
            "next" -> ids[(ids.indexOf(current) + 1) % ids.size]
            else -> target
        }
        if (id !in players) return reject("Unknown scouting target")
        if (id == viewerId) scoutTargets.remove(viewerId) else scoutTargets[viewerId] = id!!
        revision++
        return accept("Scouting ${players.getValue(id!!).name}")
    }

    override fun tick(nowMillis: Long): Boolean {
        if (finished) return false
        val elapsed = (nowMillis - lastTickAt).coerceIn(0L, 1_000L)
        lastTickAt = nowMillis
        var changed = false
        when (phase) {
            Phase.DRAFT -> if (nowMillis >= phaseEndsAt) { autoResolveDraft(); startPlanning(nowMillis, false); changed = true }
            Phase.PLANNING -> if (nowMillis >= phaseEndsAt) { autoChooseAugments(); startCombat(nowMillis); changed = true }
            Phase.COMBAT -> {
                val unique = combats.values.distinctBy { System.identityHashCode(it) }
                unique.forEach { match -> if (!match.resolved) match.engine.step(elapsed.coerceAtLeast(50L)) }
                if (unique.isNotEmpty() && unique.all { it.engine.finished }) { resolveCombats(nowMillis); changed = true }
                else if (elapsed > 0) changed = true
            }
            Phase.POST_COMBAT -> if (nowMillis >= phaseEndsAt) {
                roundIndex++
                if (!finished) {
                    if (isDraftRound(roundLabel())) startDraft(nowMillis) else startPlanning(nowMillis, false)
                    changed = true
                }
            }
            Phase.FINISHED -> Unit
        }
        return changed
    }

    private fun buy(player: PlayerState, index: Int?): NativeGameResult {
        if (phase != Phase.PLANNING) return reject("Shop is closed during combat")
        val i = index ?: return reject("Missing shop slot")
        val unitId = player.shop.getOrNull(i) ?: return reject("Shop slot empty")
        val def = unitDefs[unitId] ?: return reject("Unit definition missing")
        if (player.gold < def.cost) return reject("Not enough gold")
        val unit = newOwned(unitId)
        if (!addToBenchOrBoard(player, unit)) return reject("Bench is full")
        player.gold -= def.cost
        player.shop[i] = null
        combineCopies(player, unitId)
        bump("${player.name} bought ${def.id}")
        return accept("Bought ${def.id}")
    }

    private fun refresh(player: PlayerState): NativeGameResult {
        if (phase != Phase.PLANNING) return reject("Shop is closed")
        val cost = refreshCost(player)
        if (player.gold < cost) return reject("Not enough gold")
        player.gold -= cost
        if (player.freeRerolls > 0) player.freeRerolls--
        rerollShop(player)
        bump("${player.name} refreshed shop")
        return accept("Shop refreshed")
    }

    private fun buyXp(player: PlayerState): NativeGameResult {
        if (phase != Phase.PLANNING || player.level >= progression.maxLevel) return reject("Cannot buy XP")
        val modifiers = playerModifiers(player)
        val goldCost = modifiers.apply(TftPlayerModifier.XP_PURCHASE_COST, progression.buyXp.goldCost.toDouble()).toInt().coerceAtLeast(0)
        if (player.gold < goldCost) return reject("Not enough gold")
        val amount = modifiers.apply(TftPlayerModifier.XP_PURCHASE_AMOUNT, progression.buyXp.xpGranted.toDouble()).toInt()
        grantXp(player, amount)
        player.gold -= goldCost
        bump("${player.name} bought XP")
        return accept("XP purchased")
    }

    private fun deploy(player: PlayerState, benchIndex: Int?, slot: Int?): NativeGameResult {
        if (!canEditBoard(player)) return reject("Board is locked")
        val bi = benchIndex ?: return reject("Missing bench slot")
        val target = slot ?: return reject("Missing board slot")
        if (target !in 0 until formationCells) return reject("Invalid board slot")
        val unit = player.bench.getOrNull(bi) ?: return reject("Bench slot empty")
        val occupied = player.board[target]
        if (occupied == null && player.board.size >= unitCap(player)) return reject("Team size limit reached")
        player.bench[bi] = occupied
        player.board[target] = unit
        bump("${player.name} deployed ${unit.unitId}")
        return accept("Unit deployed")
    }

    private fun move(player: PlayerState, from: Int?, to: Int?): NativeGameResult {
        if (!canEditBoard(player)) return reject("Board is locked")
        val a = from ?: return reject("Missing source")
        val b = to ?: return reject("Missing destination")
        if (a !in 0 until formationCells || b !in 0 until formationCells) return reject("Invalid board slot")
        val first = player.board[a] ?: return reject("Source slot empty")
        val second = player.board[b]
        player.board[b] = first
        if (second == null) player.board.remove(a) else player.board[a] = second
        bump("${player.name} repositioned ${first.unitId}")
        return accept("Unit moved")
    }

    private fun bench(player: PlayerState, slot: Int?, destination: Int?): NativeGameResult {
        if (!canEditBoard(player)) return reject("Board is locked")
        val source = slot ?: return reject("Missing board slot")
        val unit = player.board[source] ?: return reject("Board slot empty")
        val empty = destination ?: player.bench.indexOfFirst { it == null }
        if (empty !in player.bench.indices) return reject("Invalid or full bench")
        val swapped = player.bench[empty]
        if (swapped == null) player.board.remove(source) else player.board[source] = swapped
        player.bench[empty] = unit
        bump("${player.name} returned ${unit.unitId} to bench")
        return accept("Unit returned to bench")
    }

    private fun swapBench(player: PlayerState, from: Int?, to: Int?): NativeGameResult {
        if (!canEditBoard(player)) return reject("Board is locked")
        val a = from ?: return reject("Missing source")
        val b = to ?: return reject("Missing destination")
        if (a !in player.bench.indices || b !in player.bench.indices) return reject("Invalid bench slot")
        val unit = player.bench[a] ?: return reject("Bench slot empty")
        player.bench[a] = player.bench[b]; player.bench[b] = unit
        bump("${player.name} repositioned bench")
        return accept("Bench unit moved")
    }

    private fun sell(player: PlayerState, args: Map<String, String>): NativeGameResult {
        if (!canEditBoard(player)) return reject("Cannot sell during combat")
        val origin = args["origin"] ?: return reject("Missing origin")
        val index = args["index"]?.toIntOrNull() ?: return reject("Missing unit slot")
        val unit = when (origin) {
            "bench" -> player.bench.getOrNull(index)?.also { player.bench[index] = null }
            "board" -> player.board.remove(index)
            else -> null
        } ?: return reject("Unit not found")
        val def = unitDefs[unit.unitId] ?: return reject("Unit definition missing")
        val copies = copiesForStar(unit.star)
        player.gold = (player.gold + def.cost * copies).coerceAtMost(MAX_GOLD)
        pool.returnCopies(unit.unitId, copies)
        player.itemBench += unit.items.flatMap(::unpackItem)
        bump("${player.name} sold ${unit.unitId}")
        return accept("Unit sold")
    }

    private fun equipItem(player: PlayerState, args: Map<String, String>): NativeGameResult {
        if (!canEditBoard(player)) return reject("Cannot equip during combat")
        val itemIndex = args["item"]?.toIntOrNull() ?: return reject("Missing item")
        val item = player.itemBench.getOrNull(itemIndex) ?: return reject("Item not found")
        val origin = args["origin"] ?: return reject("Missing target origin")
        val unitIndex = args["index"]?.toIntOrNull() ?: return reject("Missing target")
        val unit = when (origin) { "bench" -> player.bench.getOrNull(unitIndex); "board" -> player.board[unitIndex]; else -> null } ?: return reject("Target unit not found")
        if (unit.items.count { it.startsWith("combo:") || it.startsWith("full:") } >= 3) return reject("Unit already has 3 completed items")
        player.itemBench.removeAt(itemIndex)
        val loose = unit.items.indexOfFirst { !it.startsWith("combo:") && !it.startsWith("full:") }
        if (loose >= 0) {
            val previous = unit.items.removeAt(loose)
            val key = listOf(previous, item).sorted().joinToString("+")
            val recipe = itemRecipes[key]
            unit.items += if (recipe != null) "full:${recipe.id}" else "combo:$key"
        } else if (unit.items.size < 3) unit.items += item
        else { player.itemBench.add(itemIndex.coerceAtMost(player.itemBench.size), item); return reject("Unit item slots are full") }
        bump("${player.name} equipped $item on ${unit.unitId}")
        return accept("Item equipped")
    }

    private fun chooseAugment(player: PlayerState, id: String?): NativeGameResult {
        if (phase != Phase.PLANNING || player.augmentChoices.isEmpty()) return reject("No augment choice")
        val chosen = id?.takeIf(player.augmentChoices::contains) ?: return reject("Invalid augment")
        player.augments += chosen; player.augmentChoices.clear(); bump("${player.name} chose $chosen")
        return accept("Augment selected")
    }

    private fun draftPick(player: PlayerState, index: Int?): NativeGameResult {
        if (phase != Phase.DRAFT) return reject("Draft is not active")
        if (player.draftPicked) return reject("Already drafted")
        if (System.currentTimeMillis() < player.draftUnlockAt) return reject("Draft pick is not unlocked yet")
        val offer = index?.let { draftOffers.getOrNull(it) } ?: return reject("Invalid draft offer")
        if (offer.takenBy != null) return reject("Offer already taken")
        val unit = newOwned(offer.unitId).also { it.items += offer.itemId }
        if (!addToBenchOrBoard(player, unit)) return reject("Bench is full")
        offer.takenBy = player.id; player.draftPicked = true; combineCopies(player, offer.unitId)
        bump("${player.name} drafted ${offer.unitId}")
        return accept("Draft pick secured")
    }

    private fun resign(player: PlayerState): NativeGameResult {
        if (player.eliminated) return reject("Already eliminated")
        player.hp = 0; eliminatePlayers(listOf(player)); bump("${player.name} resigned"); checkWinner()
        return accept("Resigned")
    }

    private fun startDraft(now: Long) {
        phase = Phase.DRAFT; combats.clear(); draftOffers.clear()
        val alive = alivePlayers().sortedBy { it.hp }
        alive.forEachIndexed { index, p -> p.draftPicked = false; p.draftUnlockAt = now + (index / 2) * DRAFT_WAVE_MS }
        val offerCount = max(9, alive.size + 1)
        repeat(offerCount) { idx ->
            val unitId = pool.reserveForLevel(7) ?: return@repeat
            val item = set.components.randomOrNull(rng)?.id ?: ""
            draftOffers += DraftOffer(idx, unitId, item)
        }
        phaseEndsAt = now + DRAFT_TOTAL_MS
        bump("${roundLabel()} shared draft")
    }

    private fun autoResolveDraft() {
        alivePlayers().filterNot { it.draftPicked }.sortedBy { it.hp }.forEach { player ->
            val offer = draftOffers.firstOrNull { it.takenBy == null } ?: return@forEach
            val unit = newOwned(offer.unitId).also { if (offer.itemId.isNotBlank()) it.items += offer.itemId }
            if (addToBenchOrBoard(player, unit)) { offer.takenBy = player.id; player.draftPicked = true; combineCopies(player, offer.unitId) }
        }
        draftOffers.filter { it.takenBy == null }.forEach { pool.returnCopies(it.unitId, 1) }
        draftOffers.clear()
    }

    private fun startPlanning(now: Long, firstRound: Boolean) {
        phase = Phase.PLANNING; combats.clear()
        alivePlayers().forEach { player ->
            if (player.legacyIncomePending) { grantIncome(player); player.legacyIncomePending = false }
            if (isAugmentRound(roundLabel()) && player.augmentChoices.isEmpty()) {
                val owned = player.augments.toSet()
                player.augmentChoices += set.augments.filterNot { it.id in owned }.shuffled(rng).take(3).map { it.id }
            }
            player.freeRerolls = playerModifiers(player).value(TftPlayerModifier.FREE_REFRESH_COUNT).toInt().coerceAtLeast(0)
            rerollShop(player)
        }
        phaseEndsAt = now + set.planningSeconds.coerceIn(10, 90) * 1_000L
        bump("${roundLabel()} planning")
    }

    private fun startCombat(now: Long) {
        phase = Phase.COMBAT; combats.clear(); autoChooseAugments(); alivePlayers().forEach(::ensureFormation)
        val pve = pveDefinition(roundLabel()); if (pve != null) startPveCombats(pve) else startPvpCombats()
        phaseEndsAt = now + set.combatSeconds.coerceIn(15, 90) * 1_000L
        bump("${roundLabel()} combat")
    }

    private fun startPveCombats(pve: TftPveRoundDefinition) {
        alivePlayers().forEach { player ->
            val enemyBoard = linkedMapOf<Int, TftOwnedUnit>()
            pve.enemies.forEachIndexed { index, enemy -> if (enemy.unit in unitDefs) enemyBoard[enemy.slot.coerceIn(0, formationCells - 1)] = TftOwnedUnit("pve:${roundIndex}:$index:${player.id}", enemy.unit, enemy.star.coerceIn(1, 3)) }
            val engine = TftCombatEngine(set, player.id, snapshotBoard(player), player.augments, "pve", enemyBoard, emptyList(), rng.nextLong())
            combats[player.id] = MatchCombat(player.id, null, "PvE", engine, pve = pve)
        }
    }

    private fun startPvpCombats() {
        val alive = alivePlayers().shuffled(rng).toMutableList()
        if (alive.size >= 4 && alive[0].lastOpponentId == alive[1].id) { val tmp = alive[1]; alive[1] = alive[2]; alive[2] = tmp }
        var i = 0
        while (i + 1 < alive.size) {
            val a = alive[i]; val b = alive[i + 1]
            val engine = TftCombatEngine(set, a.id, snapshotBoard(a), a.augments, b.id, snapshotBoard(b), b.augments, rng.nextLong())
            val match = MatchCombat(a.id, b.id, b.name, engine)
            combats[a.id] = match; combats[b.id] = match; a.lastOpponentId = b.id; b.lastOpponentId = a.id; i += 2
        }
        if (i < alive.size) {
            val solo = alive[i]
            val ghost = alive.filter { it.id != solo.id }.maxByOrNull { it.board.size * 100 + it.level }
            if (ghost != null) {
                val engine = TftCombatEngine(set, solo.id, snapshotBoard(solo), solo.augments, "ghost:${ghost.id}", snapshotBoard(ghost), ghost.augments, rng.nextLong())
                combats[solo.id] = MatchCombat(solo.id, null, "Ghost ${ghost.name}", engine, ghostOwnerId = ghost.id); solo.lastOpponentId = ghost.id
            }
        }
    }

    private fun resolveCombats(now: Long) {
        val unique = combats.values.distinctBy { System.identityHashCode(it) }
        val eliminated = mutableListOf<PlayerState>()
        unique.forEach { match ->
            if (match.resolved) return@forEach
            val outcome = match.engine.result ?: return@forEach
            match.resolved = true
            val a = players[match.aId] ?: return@forEach
            if (match.pve != null) {
                if (outcome.winnerTeam == 0) {
                    onWin(a)
                    val drops = playerModifiers(a).apply(TftPlayerModifier.PVE_DROP_COUNT, match.pve.componentDrops.toDouble()).toInt()
                    repeat(drops.coerceIn(0, 6)) { set.components.randomOrNull(rng)?.id?.let(a.itemBench::add) }
                } else { onLoss(a); a.hp -= PVE_LOSS_DAMAGE }
                healFromModifiers(a); if (a.hp <= 0) eliminated += a; return@forEach
            }
            val b = match.bId?.let(players::get)
            if (b == null) {
                when (outcome.winnerTeam) { 0 -> onWin(a); 1 -> { onLoss(a); a.hp -= playerDamage(stageNumber(), outcome.survivingTeam1) }; else -> { a.streak = 0; a.hp -= DRAW_DAMAGE } }
                healFromModifiers(a); if (a.hp <= 0) eliminated += a; return@forEach
            }
            when (outcome.winnerTeam) {
                0 -> { onWin(a); onLoss(b); b.hp -= playerDamage(stageNumber(), outcome.survivingTeam0) }
                1 -> { onWin(b); onLoss(a); a.hp -= playerDamage(stageNumber(), outcome.survivingTeam1) }
                else -> { a.streak = 0; b.streak = 0; a.hp -= DRAW_DAMAGE; b.hp -= DRAW_DAMAGE }
            }
            healFromModifiers(a); healFromModifiers(b); if (a.hp <= 0) eliminated += a; if (b.hp <= 0) eliminated += b
        }
        eliminatePlayers(eliminated.distinctBy { it.id })
        // Result, damage and elimination precede all economic grants. Commit the
        // round marker with the same snapshot as gold/XP before any next-round shop.
        val participants = unique.flatMap { listOfNotNull(it.aId, it.bId) }.toSet()
        val roundType = if (unique.any { it.pve != null }) "pve" else "pvp"
        alivePlayers().filter { it.id in participants }.forEach { settleRound(it, roundType) }
        combats.clear(); checkWinner()
        if (!finished) { phase = Phase.POST_COMBAT; phaseEndsAt = now + set.postCombatSeconds.coerceIn(2, 10) * 1_000L; bump("${roundLabel()} resolved") }
    }

    private fun eliminatePlayers(list: List<PlayerState>) {
        if (list.isEmpty()) return
        var placement = alivePlayers().size
        list.filterNot { it.eliminated }.sortedBy { it.hp }.forEach { player ->
            player.eliminated = true; player.placement = placement.coerceAtLeast(2); placement--; releasePlayerPool(player); bump("${player.name} eliminated #${player.placement}")
        }
    }

    private fun checkWinner() {
        val alive = alivePlayers(); if (alive.size > 1) return
        phase = Phase.FINISHED
        val champion = alive.firstOrNull(); champion?.placement = 1; winner = champion?.id
        result = champion?.let { "${it.name} wins Pokémon TFT" } ?: "Pokémon TFT ended"; bump(result.orEmpty())
    }

    private fun grantIncome(player: PlayerState) {
        val cap = playerModifiers(player).apply(TftPlayerModifier.INTEREST_CAP, 5.0).toInt()
        val interest = (player.gold / 10).coerceAtMost(cap)
        val streakGold = streakGold(player.streak)
        val winGold = if (player.lastOutcome > 0) 1 else 0
        val modifiers = playerModifiers(player)
        val incomeBeforeMultiplier = modifiers.apply(TftPlayerModifier.INCOME_FLAT, (5 + interest + streakGold + winGold).toDouble())
        val income = (incomeBeforeMultiplier * (1.0 + modifiers.value(TftPlayerModifier.INCOME_MULTIPLIER))).toInt().coerceAtLeast(0)
        player.gold = (player.gold + income).coerceAtMost(MAX_GOLD)
        player.lastIncome = income; player.lastInterest = interest; player.lastStreakGold = streakGold
    }

    private fun onWin(player: PlayerState) { player.lastOutcome = 1; player.streak = if (player.streak >= 0) player.streak + 1 else 1 }
    private fun onLoss(player: PlayerState) { player.lastOutcome = -1; player.streak = if (player.streak <= 0) player.streak - 1 else -1 }
    private fun healFromModifiers(player: PlayerState) { val heal = playerModifiers(player).value(TftPlayerModifier.POST_ROUND_HEAL).toInt(); if (heal > 0) player.hp = (player.hp + heal).coerceAtMost(100) }

    private fun rerollShop(player: PlayerState) {
        player.shop.forEach { unit -> if (unit != null) pool.returnCopies(unit, 1) }
        for (i in player.shop.indices) player.shop[i] = pool.reserveForLevel(player.level)
    }

    private fun ensureFormation(player: PlayerState) {
        val cap = unitCap(player)
        while (player.board.size < cap) {
            val benchIndex = player.bench.indexOfFirst { it != null }; if (benchIndex < 0) break
            val slot = (0 until formationCells).firstOrNull { it !in player.board } ?: break
            player.board[slot] = player.bench[benchIndex]!!; player.bench[benchIndex] = null
        }
    }

    private fun combineCopies(player: PlayerState, unitId: String) {
        for (star in 1..2) {
            while (true) {
                val refs = unitLocations(player).filter { it.unit.unitId == unitId && it.unit.star == star }.take(3)
                if (refs.size < 3) break
                val primary = refs.firstOrNull { it.origin == "board" } ?: refs.first()
                val keptItems = primary.unit.items.toMutableList()
                refs.drop(1).flatMap { it.unit.items }.forEach { item -> if (keptItems.size < 3) keptItems += item else player.itemBench += unpackItem(item) }
                refs.forEach { removeLocation(player, it) }
                val upgraded = TftOwnedUnit(primary.unit.instanceId, unitId, star + 1, keptItems)
                if (primary.origin == "board" && primary.index !in player.board) player.board[primary.index] = upgraded
                else {
                    val empty = player.bench.indexOfFirst { it == null }
                    if (empty >= 0) player.bench[empty] = upgraded else {
                        val slot = (0 until formationCells).firstOrNull { it !in player.board }
                        if (slot != null) player.board[slot] = upgraded else player.bench[0] = upgraded
                    }
                }
                bump("${player.name}: $unitId -> ${star + 1}★")
            }
        }
    }

    private fun addToBenchOrBoard(player: PlayerState, unit: TftOwnedUnit): Boolean {
        val empty = player.bench.indexOfFirst { it == null }
        if (empty >= 0) { player.bench[empty] = unit; return true }
        return false
    }

    private fun releasePlayerPool(player: PlayerState) {
        player.shop.forEach { it?.let { id -> pool.returnCopies(id, 1) } }; player.shop.indices.forEach { player.shop[it] = null }
        unitLocations(player).forEach { ref -> pool.returnCopies(ref.unit.unitId, copiesForStar(ref.unit.star)); player.itemBench += ref.unit.items.flatMap(::unpackItem) }
        player.board.clear(); player.bench.indices.forEach { player.bench[it] = null }
    }

    private fun buildBoardView(player: PlayerState): List<String> {
        val cells = MutableList(56) { "" }
        val match = combatFor(player.id)
        if (phase == Phase.COMBAT && match != null) {
            val viewerTeam = if (match.aId == player.id) 0 else 1
            match.engine.units.forEach { unit ->
                val cell = if (viewerTeam == 0) unit.cell else rotateCell(unit.cell)
                val team = if (unit.team == viewerTeam) 0 else 1
                if (cell in cells.indices) cells[cell] = encodeCombatUnit(unit, team)
            }
            return cells
        }
        player.board.forEach { (slot, unit) ->
            val def = unitDefs[unit.unitId] ?: return@forEach
            val cell = 28 + slot
            if (cell in cells.indices) cells[cell] = listOf(unit.instanceId, unit.unitId, def.presentation.species, unit.star, "-1", "-1", "0", "0", "0", def.presentation.resolverAspects().joinToString(","), unit.items.joinToString(","), def.cost, def.role, "", 0, 0L, 0L).joinToString("~")
        }
        return cells
    }

    private fun encodeCombatUnit(unit: TftCombatUnit, relativeTeam: Int): String = listOf(
        unit.instanceId, unit.definition.id, unit.definition.presentation.species, unit.star, unit.hp, unit.maxHp,
        unit.mana, unit.maxMana, relativeTeam, unit.definition.presentation.resolverAspects().joinToString(","), unit.items.joinToString(","),
        unit.definition.cost, unit.definition.role, unit.targetId.orEmpty(), unit.casts, unit.damageDone, unit.healingDone,
        if (unit.alive) 1 else 0
    ).joinToString("~")

    private fun encodeBench(player: PlayerState): String = player.bench.mapIndexedNotNull { index, unit ->
        unit ?: return@mapIndexedNotNull null
        val def = unitDefs[unit.unitId] ?: return@mapIndexedNotNull null
        listOf(index, unit.instanceId, unit.unitId, def.presentation.species, unit.star, def.presentation.resolverAspects().joinToString(","), unit.items.joinToString(","), def.cost, def.role).joinToString("~")
    }.joinToString(";")

    private fun encodePlayers(): String = players.values.sortedWith(compareBy<PlayerState> { it.eliminated }.thenByDescending { it.hp }).joinToString(";") { p ->
        listOf(p.id, p.name, p.hp, p.level, p.placement ?: 0, if (p.eliminated) 1 else 0).joinToString("~")
    }

    private fun encodeTraits(player: PlayerState): String {
        val counts = traitCounts(player)
        return counts.entries.sortedByDescending { it.value }.joinToString(";") { (id, count) ->
            val def = traitDefs[id]
            val active = def?.tiers?.filter { count >= it.threshold }?.maxByOrNull { it.threshold }
            val next = def?.tiers?.firstOrNull { count < it.threshold }
            listOf(id, def?.name ?: id, count, active?.threshold ?: 0, next?.threshold ?: 0, active?.description ?: "").joinToString("~")
        }
    }

    private fun encodeUnitCatalog(player: PlayerState, includeShop: Boolean = true): String {
        val ids = linkedSetOf<String>()
        player.board.values.forEach { ids += it.unitId }
        player.bench.forEach { unit -> if (unit != null) ids += unit.unitId }
        if (includeShop) player.shop.forEach { unitId -> if (unitId != null) ids += unitId }
        combatFor(player.id)?.engine?.units?.forEach { ids += it.definition.id }
        if (phase == Phase.DRAFT) draftOffers.forEach { ids += it.unitId }
        return JsonObject().apply {
            ids.take(64).forEach { id ->
                val def = unitDefs[id] ?: return@forEach
                add(def.id, JsonObject().apply {
                    addProperty("name", def.id.replace('_', ' ').replaceFirstChar { it.uppercase() }.take(96))
                    addProperty("species", def.presentation.species.take(160))
                    addProperty("cost", def.cost)
                    addProperty("role", def.role.take(64))
                    addProperty("traits", def.traits.joinToString(",").take(512))
                    addProperty("hp", def.stats.hp)
                    addProperty("attackDamage", def.stats.attackDamage)
                    addProperty("defense", def.stats.defense)
                    addProperty("specialDefense", def.stats.specialDefense)
                    addProperty("attackSpeed", def.stats.attackSpeed)
                    addProperty("range", def.stats.range)
                    addProperty("manaStart", def.stats.manaStart)
                    addProperty("manaMax", def.stats.manaMax)
                    addProperty("abilityId", def.ability.id.take(96))
                    addProperty("abilityName", def.ability.name.take(96))
                    addProperty("abilityTarget", def.ability.target.take(64))
                    addProperty("damageType", def.ability.damageType.take(32))
                    addProperty("damage", def.ability.damage)
                    addProperty("heal", def.ability.heal)
                    addProperty("shield", def.ability.shield)
                    addProperty("radius", def.ability.radius)
                    addProperty("stunMs", def.ability.stunMs)
                    addProperty("dash", def.ability.dash)
                    addProperty(
                        "effects",
                        def.ability.effects.entries.joinToString(",") { (key, value) -> "$key=$value" }.take(512)
                    )
                })
            }
        }.toString()
    }

    private fun encodeTraitCatalog(player: PlayerState, includeShop: Boolean = true): String {
        val ids = linkedSetOf<String>()
        traitCounts(player).keys.forEach(ids::add)
        player.board.values.forEach { unit -> unitDefs[unit.unitId]?.traits?.forEach(ids::add) }
        player.bench.forEach { unit -> unit?.let { unitDefs[it.unitId]?.traits?.forEach(ids::add) } }
        if (includeShop) player.shop.forEach { unitId -> unitId?.let { unitDefs[it]?.traits?.forEach(ids::add) } }
        return JsonObject().apply {
            ids.take(64).forEach { id ->
                val def = traitDefs[id] ?: return@forEach
                add(def.id, JsonObject().apply {
                    addProperty("name", def.name.take(96))
                    add("tiers", com.google.gson.JsonArray().also { tiers ->
                        def.tiers.forEach { tier ->
                            tiers.add(JsonObject().apply {
                                addProperty("threshold", tier.threshold)
                                addProperty("description", tier.description.take(512))
                                addProperty(
                                    "effects",
                                    tier.effects.entries.joinToString(",") { (key, value) -> "$key=$value" }.take(512)
                                )
                                addProperty(
                                    "teamEffects",
                                    tier.teamEffects.entries.joinToString(",") { (key, value) -> "$key=$value" }.take(512)
                                )
                            })
                        }
                    })
                })
            }
        }.toString()
    }

    private fun encodeSelectedAugments(player: PlayerState): String = com.google.gson.JsonArray().apply {
        player.augments.forEach { id ->
            val def = augmentDefs.getValue(id)
            add(JsonObject().apply {
                addProperty("id", id); addProperty("name", def.name); addProperty("tier", def.tier)
                addProperty("description", def.description)
                addProperty("mechanic", (def.effects.entries.map { (key, value) -> "$key: $value" } +
                    def.playerModifiers.entries.map { (key, value) -> "${key.name.lowercase()}: $value" }).joinToString(" • "))
            })
        }
    }.toString()

    private fun encodeAugmentChoices(player: PlayerState): String = player.augmentChoices.joinToString(";") { id ->
        val def = augmentDefs[id]; listOf(id, def?.name ?: id, def?.description ?: "", def?.aiWeight ?: 50).joinToString("~")
    }

    private fun encodeDraft(player: PlayerState): String {
        if (phase != Phase.DRAFT) return ""
        val unlocked = System.currentTimeMillis() >= player.draftUnlockAt
        return draftOffers.joinToString(";") { offer ->
            val def = unitDefs[offer.unitId]
            listOf(offer.index, offer.unitId, def?.presentation?.species ?: "", offer.itemId, offer.takenBy ?: "", if (unlocked) 1 else 0, def?.cost ?: 1, def?.traits?.joinToString(",") ?: "").joinToString("~")
        }
    }

    private fun traitCounts(player: PlayerState): Map<String, Int> = player.board.values.distinctBy { it.unitId }.mapNotNull { unitDefs[it.unitId] }.flatMap { it.traits }.groupingBy { it }.eachCount()
    private fun playerModifiers(player: PlayerState): TftPlayerModifierSet = TftPlayerModifierSet.compile(
        player.augments.mapNotNull(augmentDefs::get).map { it.playerModifiers }
    )
    private fun refreshCost(player: PlayerState) = if (player.freeRerolls > 0) 0 else
        playerModifiers(player).apply(TftPlayerModifier.SHOP_REFRESH_COST, 2.0).toInt().coerceAtLeast(0)
    private fun canEditBoard(player: PlayerState) = phase == Phase.PLANNING && !player.eliminated
    private fun unitCap(player: PlayerState) = playerModifiers(player)
        .apply(TftPlayerModifier.BOARD_CAPACITY, player.level.toDouble()).toInt().coerceIn(1, set.rules.maxBoardCapacity)

    private fun settleRound(player: PlayerState, roundType: String) {
        if (player.lastSettledRound >= roundIndex) return
        val modifiers = playerModifiers(player)
        val amount = TftProgression.passiveAmount(progression, roundType,
            modifiers.value(TftPlayerModifier.XP_GAIN_FLAT), modifiers.value(TftPlayerModifier.XP_GAIN_MULTIPLIER))
        // Validate arithmetic before mutating currency. No callbacks or I/O may
        // observe half a settlement on the session actor.
        val xp = TftProgression.grant(progression, player.level, player.xp, amount)
        grantIncome(player)
        player.level = xp.level; player.xp = xp.xp
        player.lastXpGranted = xp.granted; player.lastLevelsGained = xp.levelsGained
        player.lastSettledRound = roundIndex
    }

    private fun grantXp(player: PlayerState, amount: Int) {
        val xp = TftProgression.grant(progression, player.level, player.xp, amount)
        player.level = xp.level; player.xp = xp.xp
        player.lastXpGranted = xp.granted; player.lastLevelsGained = xp.levelsGained
    }

    private fun xpToNext(level: Int): Int = if (level >= progression.maxLevel) 0
        else progression.xpToNextByLevel.getValue(level.toString())
    private fun playerDamage(stage: Int, survivors: List<TftCombatUnit>): Int {
        val base = when (stage) { 1 -> 0; 2 -> 2; 3 -> 5; 4 -> 8; 5 -> 10; 6 -> 13; else -> 15 + (stage - 7) * 2 }
        val units = survivors.fold(0) { acc, unit -> acc + when (unit.star) { 3 -> 3; 2 -> 2; else -> 1 } }
        return (base + units).coerceAtLeast(1)
    }
    private fun streakGold(streak: Int): Int = when (abs(streak)) { in 0..1 -> 0; 2, 3 -> 1; 4 -> 2; else -> 3 }
    private fun autoChooseAugments() { alivePlayers().forEach { player -> if (player.augmentChoices.isNotEmpty()) { player.augments += player.augmentChoices.random(rng); player.augmentChoices.clear() } } }
    private fun roundDefinition() = set.roundSchedule[roundIndex.coerceAtMost(set.roundSchedule.lastIndex)]
    private fun pveDefinition(label: String): TftPveRoundDefinition? = roundDefinition().pve?.let { ref -> set.pveRounds.firstOrNull { it.round == ref } } ?: set.pveRounds.firstOrNull { it.round == label }
    private fun isAugmentRound(label: String) = roundDefinition().type == "augment"
    private fun isDraftRound(label: String) = roundDefinition().type == "carousel"
    private fun roundLabel(): String = roundDefinition().label
    private fun stageNumber() = roundLabel().substringBefore('-').toIntOrNull() ?: 1
    private fun secondsLeft(): Long = ((phaseEndsAt - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1_000L
    private fun alivePlayers() = players.values.filterNot { it.eliminated }
    private fun combatFor(id: String) = combats[id]
    private fun snapshotBoard(player: PlayerState) = player.board.mapValues { (_, unit) -> unit.copy(items = unit.items.toMutableList()) }
    private fun resolveTactician(selection: String?): String {
        val requestedEntity = selection?.let { if (':' in it) it else "minecraft:$it" }
        return set.tacticians.firstOrNull { it.id == selection || it.entity == requestedEntity }?.id ?: set.defaultTactician
    }
    private fun newOwned(unitId: String) = TftOwnedUnit("u${nextUnitSerial++}", unitId)
    private fun copiesForStar(star: Int) = when (star) { 2 -> 3; 3 -> 9; else -> 1 }
    private fun unpackItem(item: String): List<String> = when { item.startsWith("combo:") -> item.removePrefix("combo:").split('+').filter(String::isNotBlank); item.startsWith("full:") -> set.fullItems.firstOrNull { it.id == item.removePrefix("full:") }?.components.orEmpty(); else -> listOf(item) }
    private fun rotateCell(cell: Int): Int = 55 - cell
    private fun accept(message: String) = NativeGameResult(true, true, message)
    private fun reject(message: String) = NativeGameResult(false, false, message)
    private fun bump(message: String) { revision++; if (message.isNotBlank()) log += message; while (log.size > 40) log.removeFirst() }
    private fun unitLocations(player: PlayerState): List<UnitLocation> = buildList { player.board.forEach { (slot, unit) -> add(UnitLocation("board", slot, unit)) }; player.bench.forEachIndexed { index, unit -> if (unit != null) add(UnitLocation("bench", index, unit)) } }
    private fun removeLocation(player: PlayerState, ref: UnitLocation) { if (ref.origin == "board") player.board.remove(ref.index) else if (ref.index in player.bench.indices) player.bench[ref.index] = null }

    private data class Snapshot(
        val schema: Int,
        val setDefinition: TftSetDefinition,
        val phase: String,
        val roundIndex: Int,
        val phaseRemainingMs: Long,
        val nextUnitSerial: Long,
        val revision: Long,
        val result: String?,
        val winner: String?,
        val rngState: Long,
        val poolCounts: Map<String, Int>,
        val players: List<PlayerSnapshot>,
        val draftOffers: List<DraftSnapshot>,
        val combats: List<MatchSnapshot>,
        val log: List<String>
    )
    private data class PlayerSnapshot(
        val id: String,
        val name: String,
        val hp: Int,
        val gold: Int,
        val level: Int,
        val xp: Int,
        val streak: Int,
        val lastOutcome: Int,
        val eliminated: Boolean,
        val placement: Int?,
        val lastOpponentId: String?,
        val bench: List<TftOwnedUnit?>,
        val board: List<BoardSnapshot>,
        val shop: List<String?>,
        val itemBench: List<String>,
        val augments: List<String>,
        val augmentChoices: List<String>,
        val freeRerolls: Int,
        val draftPicked: Boolean,
        val draftUnlockRemainingMs: Long,
        val lastIncome: Int,
        val lastInterest: Int,
        val lastStreakGold: Int,
        val lastSettledRound: Int,
        val lastXpGranted: Int,
        val lastLevelsGained: Int,
        val legacyIncomePending: Boolean,
        val tactician: String?
    )
    private data class BoardSnapshot(val slot: Int, val unit: TftOwnedUnit)
    private data class DraftSnapshot(
        val index: Int,
        val unitId: String,
        val itemId: String,
        val takenBy: String?
    )
    private data class MatchSnapshot(
        val aId: String,
        val bId: String?,
        val opponentLabel: String,
        val pveRound: String?,
        val ghostOwnerId: String?,
        val resolved: Boolean,
        val combat: TftCombatSnapshot
    )

    private data class UnitLocation(val origin: String, val index: Int, val unit: TftOwnedUnit)
    private data class PlayerState(
        val id: String, val name: String, var hp: Int = 100, var gold: Int = 5, var level: Int = 2, var xp: Int = 0,
        var streak: Int = 0, var lastOutcome: Int = 0, var eliminated: Boolean = false, var placement: Int? = null,
        var lastOpponentId: String? = null, val bench: MutableList<TftOwnedUnit?>,
        val board: MutableMap<Int, TftOwnedUnit> = linkedMapOf(), val shop: MutableList<String?>,
        val itemBench: MutableList<String> = mutableListOf(), val augments: MutableList<String> = mutableListOf(),
        val augmentChoices: MutableList<String> = mutableListOf(), var freeRerolls: Int = 0, var draftPicked: Boolean = false,
        var draftUnlockAt: Long = 0L, var lastIncome: Int = 0, var lastInterest: Int = 0, var lastStreakGold: Int = 0,
        var lastSettledRound: Int = -1, var lastXpGranted: Int = 0, var lastLevelsGained: Int = 0, var legacyIncomePending: Boolean = false, var tactician: String = ""
    )
    private data class DraftOffer(val index: Int, val unitId: String, val itemId: String, var takenBy: String? = null)
    private data class MatchCombat(val aId:String,val bId:String?,val opponentLabel:String,val engine:TftCombatEngine,val pve:TftPveRoundDefinition?=null,val ghostOwnerId:String?=null,var resolved:Boolean=false){fun opponentNameFor(viewer:String):String=when{pve!=null->"PvE";viewer==aId->opponentLabel;bId!=null&&viewer==bId->"Opponent";else->opponentLabel}}
    private enum class Phase(val id:String){DRAFT("draft"),PLANNING("planning"),COMBAT("combat"),POST_COMBAT("post"),FINISHED("finished")}

    private class SharedPool(private val set:TftSetDefinition,private val rng:Random){
        private val defs=set.units.associateBy{it.id};private val counts=linkedMapOf<String,Int>();private val initial=linkedMapOf<String,Int>();private val odds=set.shopOdds.associateBy{it.level}
        init{set.units.forEach{unit->val amount=set.poolSizeByCost[unit.cost.toString()]?:error("Missing TFT pool size for cost ${unit.cost}");counts[unit.id]=amount;initial[unit.id]=amount}}
        fun reserveForLevel(level:Int):String?{val row=odds[level]?:odds.values.minByOrNull{abs(it.level-level)}?:return null;repeat(7){val cost=rollCost(row.odds);reserveCost(cost)?.let{return it}};return counts.entries.filter{it.value>0}.weightedByCount()?.also{counts[it]=counts.getValue(it)-1}}
        fun returnCopies(unitId:String,amount:Int){if(amount<=0||unitId !in counts)return;counts[unitId]=(counts.getValue(unitId)+amount).coerceAtMost(initial.getValue(unitId))}
        fun snapshotCounts(): Map<String, Int> = counts.toMap()
        fun restoreCounts(saved: Map<String, Int>) {
            require(saved.keys == counts.keys) { "TFT recovery pool keys do not match set units" }
            counts.keys.forEach { id ->
                counts[id] = (saved[id] ?: 0).coerceIn(0, initial.getValue(id))
            }
        }
        private fun reserveCost(cost:Int):String?{val candidates=counts.entries.filter{it.value>0&&defs[it.key]?.cost==cost};val chosen=candidates.weightedByCount()?:return null;counts[chosen]=counts.getValue(chosen)-1;return chosen}
        private fun rollCost(values:List<Int>):Int{val roll=rng.nextInt(100);var acc=0;values.forEachIndexed{index,chance->acc+=chance;if(roll<acc)return index+1};return 1}
        private fun List<Map.Entry<String,Int>>.weightedByCount():String?{val total=sumOf{it.value};if(total<=0)return null;var roll=rng.nextInt(total);for(entry in this){roll-=entry.value;if(roll<0)return entry.key};return lastOrNull()?.key}
    }

    companion object {
        private const val MAX_GOLD=999
        private const val PVE_LOSS_DAMAGE=5;private const val DRAW_DAMAGE=2;private const val DRAFT_WAVE_MS=1_500L;private const val DRAFT_TOTAL_MS=10_000L
    }
}
