package io.github.aristheg201.svhub.native.game

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.game.tft.TftUnitDefinition
import io.github.aristheg201.svhub.native.game.tft.TftAcquisition
import io.github.aristheg201.svhub.native.game.tft.TftPermanentEvolution
import io.github.aristheg201.svhub.native.game.tft.poolSourceUnitId
import io.github.aristheg201.svhub.native.game.tft.reservedCopies
import io.github.aristheg201.svhub.native.game.tft.TftCombatEngine
import io.github.aristheg201.svhub.native.game.tft.TftCombatSnapshot
import io.github.aristheg201.svhub.native.game.tft.TftCombatUnit
import io.github.aristheg201.svhub.native.game.tft.TftCapability
import io.github.aristheg201.svhub.native.game.tft.TftDefinitionValidator
import io.github.aristheg201.svhub.native.game.tft.TftOwnedUnit
import io.github.aristheg201.svhub.native.game.tft.TftPveRoundDefinition
import io.github.aristheg201.svhub.native.game.tft.TftSetDefinition
import io.github.aristheg201.svhub.native.game.tft.TftSetRegistry
import io.github.aristheg201.svhub.native.game.tft.TftProgression
import io.github.aristheg201.svhub.native.game.tft.TftProgressionDefinition
import io.github.aristheg201.svhub.native.game.tft.TftPlayerModifier
import io.github.aristheg201.svhub.native.game.tft.TftPlayerModifierSet
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Server-authoritative TFT rules engine for Cobblemon. */
class TftSession(
    override val seats: List<NativeSeat>,
    seed: Long = Random.nextLong(),
    override val sessionId: String = NativeIds.session("tft"),
    definition: TftSetDefinition = TftSetRegistry.active(),
    private val restoreState: JsonObject? = null,
    tacticianSelections: Map<String, String> = emptyMap(),
    arenaSelections: Map<String, String> = emptyMap()
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
            seats.forEach { seat ->
                players[seat.id] = PlayerState(
                    seat.id,
                    seat.name,
                    bench = MutableList(benchSlots) { null },
                    shop = MutableList(shopSlots) { null },
                    tactician = resolveTactician(tacticianSelections[seat.id]),
                    arena = arenaSelections[seat.id]?.takeIf { it in set.rules.arenas } ?: set.rules.defaultArena
                )
            }
            startPlanning(System.currentTimeMillis(), firstRound = true)
        } else {
            restoreSnapshot(restoreState)
        }
    }

    override val finished: Boolean get() = result != null
    override val winnerSeatId: String? get() = winner

    override fun snapshotState(nowMillis: Long): JsonObject = NativeGamePersistence.toJson(
        Snapshot(
            schema = 4,
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
                    pendingItems = player.pendingItems.toList(),
                    shopLocked = player.shopLocked,
                    shopGeneration = player.shopGeneration,
                    acquisitionSerial = player.acquisitionSerial,
                    acquisitionEvent = player.acquisitionEvent,
                    augments = player.augments.toList(),
                    augmentChoices = player.augmentChoices.toList(),
                    freeRerolls = player.freeRerolls,
                    draftPicked = player.draftPicked,
                    draftUnlockRemainingMs = (player.draftUnlockAt - nowMillis).coerceAtLeast(0L),
                    carouselX = player.carouselX,
                    carouselY = player.carouselY,
                    tacticianU = player.tacticianU,
                    tacticianV = player.tacticianV,
                    lastIncome = player.lastIncome,
                    lastInterest = player.lastInterest,
                    lastStreakGold = player.lastStreakGold,
                    lastSettledRound = player.lastSettledRound,
                    lastXpGranted = player.lastXpGranted,
                    lastLevelsGained = player.lastLevelsGained,
                    legacyIncomePending = player.legacyIncomePending,
                    lastItemEvent = player.lastItemEvent,
                    itemEventSerial = player.itemEventSerial,
                    lastPveLoot = player.lastPveLoot.toList(),
                    pveLootSerial = player.pveLootSerial,
                    tactician = player.tactician,
                    tacticianEmoteRemainingMs = (player.tacticianEmoteUntil - nowMillis).coerceAtLeast(0L),
                    arena = player.arena,
                    specialRewards = player.specialRewards.toList()
                )
            },
            draftOffers = draftOffers.map { offer ->
                DraftSnapshot(offer.index, offer.unitId, offer.itemId, offer.takenBy, offer.x, offer.y)
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
        require(saved.schema in 1..4) { "Unsupported TFT session snapshot schema " + saved.schema }
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
            val restoredAugments = p.augments.filter(augmentDefs::containsKey).distinct()
            val restoredChoices = p.augmentChoices.filter(augmentDefs::containsKey).distinct()
                .filterNot(restoredAugments::contains)

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
                itemBench = p.itemBench.take(ITEM_TRAY_CAPACITY).toMutableList(),
                pendingItems = (p.itemBench.drop(ITEM_TRAY_CAPACITY) + p.pendingItems.orEmpty()).toMutableList(),
                shopLocked = p.shopLocked,
                shopGeneration = p.shopGeneration,
                acquisitionSerial = p.acquisitionSerial,
                acquisitionEvent = p.acquisitionEvent.orEmpty(),
                augments = restoredAugments.toMutableList(),
                augmentChoices = restoredChoices.toMutableList(),
                freeRerolls = p.freeRerolls.coerceIn(0, 100),
                draftPicked = p.draftPicked,
                draftUnlockAt = now + p.draftUnlockRemainingMs.coerceIn(0L, DRAFT_TOTAL_MS),
                carouselX = p.carouselX.takeIf(Double::isFinite) ?: 0.0,
                carouselY = p.carouselY.takeIf(Double::isFinite) ?: 0.0,
                tacticianU = if(saved.schema>=3) p.tacticianU.takeIf(Double::isFinite)?.coerceIn(0.0,1.0) ?: .5 else .5,
                tacticianV = if(saved.schema>=3) p.tacticianV.takeIf(Double::isFinite)?.coerceIn(0.0,1.0) ?: .5 else .5,
                lastIncome = p.lastIncome.coerceAtLeast(0),
                lastInterest = p.lastInterest.coerceAtLeast(0),
                lastStreakGold = p.lastStreakGold.coerceAtLeast(0),
                lastSettledRound = if (saved.schema >= 2) p.lastSettledRound else if (phase == Phase.POST_COMBAT || phase == Phase.FINISHED) roundIndex else roundIndex - 1,
                lastXpGranted = if (saved.schema >= 2) p.lastXpGranted else 0,
                lastLevelsGained = if (saved.schema >= 2) p.lastLevelsGained else 0,
                legacyIncomePending = if (saved.schema >= 2) p.legacyIncomePending else phase == Phase.POST_COMBAT,
                lastItemEvent = p.lastItemEvent.orEmpty(),
                itemEventSerial = p.itemEventSerial.coerceAtLeast(0L),
                lastPveLoot = if(saved.schema>=4) p.lastPveLoot.take(12).toMutableList() else mutableListOf(),
                pveLootSerial = if(saved.schema>=4) p.pveLootSerial.coerceAtLeast(0L) else 0L,
                tactician = resolveTactician(p.tactician),
                tacticianEmoteUntil = now + p.tacticianEmoteRemainingMs.coerceIn(0L, 10_000L),
                arena = p.arena.takeIf { it in set.rules.arenas } ?: set.rules.defaultArena,
                specialRewards = p.specialRewards.take(32).toMutableList()
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
                    offer.takenBy?.takeIf(players::containsKey),
                    offer.x.takeIf(Double::isFinite) ?: carouselOfferPosition(offer.index, saved.draftOffers.size).first,
                    offer.y.takeIf(Double::isFinite) ?: carouselOfferPosition(offer.index, saved.draftOffers.size).second
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
        val shopEnabled = allowed(player, TftCapability.CAN_OPEN_SHOP)
        val cards = player.shop.mapIndexedNotNull { index, unitId ->
            val def = unitId?.let(unitDefs::get) ?: return@mapIndexedNotNull null
            NativeCardView(
                id = "shop:$index",
                label = def.id,
                subtitle = "${def.price}g • ${def.traits.take(3).joinToString(" /")}",
                accent = "cost${def.cost}",
                value = def.price,
                meta = mapOf(
                    "offerId" to shopOfferId(player, index, def.id),
                    "star" to def.purchaseStar.toString(),
                    "elite" to (def.elite != null).toString(),
                    "unit" to def.id,
                    "species" to def.presentation.species,
                    "aspects" to def.presentation.resolverAspects().joinToString(","),
                    "scale" to def.presentation.scale.toString(),
                    "traits" to def.traits.joinToString(","),
                    "role" to def.role,
                    "team" to def.team,
                    "tags" to def.tags.joinToString(","),
                    "ownedCopies" to countCopies(player,def.id).toString(),
                    "enabled" to shopEnabled.toString()
                )
            )
        }
        val actions = buildList {
            val refreshGold = refreshCost(player)
            val xpGold = buyXpCost(player)
            add(NativeActionView("refresh", "Refresh", "${refreshGold}g", shopEnabled && player.gold >= refreshGold))
            add(NativeActionView("buy_xp", "Buy XP", "${xpGold}g", shopEnabled && player.gold >= xpGold && player.level < progression.maxLevel))
            add(NativeActionView("resign", "Resign", "", !finished && !player.eliminated))
        }
        val active = combatFor(observed.id)
        val opponent = active?.opponentNameFor(observed.id).orEmpty()
        val round = roundDefinition()
        val activePve = active?.pve
        val observedTactician = set.tacticians.firstOrNull { it.id == observed.tactician }
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
            boardWidth = set.rules.boardColumns,
            boardHeight = set.rules.boardRows * 2,
            board = board,
            cards = cards,
            actions = actions,
            fields = linkedMapOf(
                "set" to set.id,
                "participantId" to player.id,
                "botStrategy" to encodeBotStrategy(player.id),
                "boardColumns" to set.rules.boardColumns.toString(),
                "boardRows" to set.rules.boardRows.toString(),
                "shopSlots" to set.rules.shopSlots.toString(),
                "benchSlots" to set.rules.benchSlots.toString(),
                "arenaId" to observed.arena,
                "tacticianEntity" to observedTactician?.entity.orEmpty(),
                "tacticianSpecies" to observedTactician?.presentation?.species.orEmpty(),
                "tacticianAspects" to observedTactician?.presentation?.resolverAspects()?.joinToString(",").orEmpty(),
                "tacticianId" to observed.tactician,
                "tacticianScale" to (observedTactician?.scale ?: observedTactician?.presentation?.scale ?: 1.0).toString(),
                "tacticianVfx" to observedTactician?.cosmeticVfx.orEmpty(),
                "tacticianState" to when { observed.eliminated -> "defeat"; finished && winner==observed.id -> "victory"; finished -> "defeat"; System.currentTimeMillis()<observed.tacticianEmoteUntil -> "emote"; phase==Phase.DRAFT && observed.draftPicked -> "pickup_reaction"; phase==Phase.DRAFT -> "carousel_movement"; phase==Phase.COMBAT -> "round_start"; else -> "idle" },
                "tacticianTarget" to if(phase==Phase.DRAFT) "${observed.carouselX},${observed.carouselY}" else "",
                "tacticianPosition" to "${observed.tacticianU},${observed.tacticianV}",
                "tacticianCanMove" to (phase!=Phase.DRAFT && !player.eliminated && !finished && !scouting).toString(),
                "tacticianPresentationOnly" to "true",
                "scouting" to scouting.toString(),
                "scoutTarget" to observed.id,
                "scoutName" to observed.name,
                "round" to roundLabel(),
                "roundType" to round.type,
                "pveActive" to (activePve != null).toString(),
                "pveRound" to activePve?.round.orEmpty(),
                "pveComponentDrops" to (activePve?.componentDrops ?: 0).toString(),
                "pveLootTable" to activePve?.lootTable.orEmpty(),
                "bossRound" to (round.type == "boss").toString(),
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
                "buyXpCost" to buyXpCost(player).toString(),
                "buyXpAmount" to buyXpAmount(player).toString(),
                "unitCap" to unitCap(player).toString(),
                "boardCount" to player.board.size.toString(),
                "streak" to player.streak.toString(),
                "placement" to (player.placement ?: 0).toString(),
                "eliminated" to player.eliminated.toString(),
                "bench" to encodeBench(observed),
                "players" to encodePlayers(),
                "contestedUnits" to encodePublicContestedUnits(player.id),
                "traits" to encodeTraits(observed),
                "unitCatalog" to encodedUnitCatalog,
                "traitCatalog" to encodedTraitCatalog,
                "itemBench" to player.itemBench.joinToString(","),
                "pendingItems" to player.pendingItems.joinToString(","),
                "shopLocked" to player.shopLocked.toString(),
                "acquisitionSerial" to player.acquisitionSerial.toString(),
                "acquisitionEvent" to player.acquisitionEvent,
                "itemCatalog" to encodeItemCatalog(),
                "lastItemEvent" to player.lastItemEvent,
                "itemEventSerial" to player.itemEventSerial.toString(),
                "pveLoot" to player.lastPveLoot.joinToString(","),
                "pveLootSerial" to player.pveLootSerial.toString(),
                "augments" to player.augments.joinToString(","),
                "selectedAugments" to encodeSelectedAugments(observed),
                "augmentChoices" to encodeAugmentChoices(player),
                "draft" to encodeDraft(player),
                "carouselPosition" to "${player.carouselX},${player.carouselY}",
                "carouselMaxMove" to set.carousel.maxMovePerIntent.toString(),
                "carouselArenaId" to set.carousel.arenaId,
                "carouselMovementRadius" to set.carousel.movementRadius.toString(),
                "carouselUnlockAt" to player.draftUnlockAt.toString(),
                "carouselPickupRadius" to set.carousel.pickupRadius.toString(),
                "carouselPicked" to player.draftPicked.toString(),
                "carouselRevision" to revision.toString(),
                "carouselCenterDecoration" to set.carousel.centerDecoration,
                "opponent" to opponent,
                "lastIncome" to player.lastIncome.toString(),
                "lastInterest" to player.lastInterest.toString(),
                "lastStreakGold" to player.lastStreakGold.toString(),
                "freeRerolls" to player.freeRerolls.toString(),
                "specialRewards" to player.specialRewards.joinToString(","),
                "result" to when { !finished -> ""; winner==player.id -> "Victory"; else -> "Defeat" },
                "canEditBoard" to (canEditBoard(player) && !scouting).toString(),
                "capabilities" to capabilities(player).joinToString(",", transform = TftCapability::name)
            ),
            log = log.toList().takeLast(12),
            revision = revision,
            finished = finished,
            winner = winner?.let { id -> seats.firstOrNull { it.id == id }?.name },
            resultPresentation = if(!finished && !player.eliminated) null else NativeGameResultPresentation(
                outcome = when(player.placement){
                    1 -> "top_1"
                    2 -> "top_2"
                    3 -> "top_3"
                    else -> if(winner==player.id)"victory" else "defeat"
                },
                reason = when(player.placement){1->"first";2->"second";3->"third";else->"placement"},
                backdrop = "tft",
                stats = listOf(
                    NativeResultLine("placement",(player.placement?:8).toString()),
                    NativeResultLine("level",player.level.toString()),
                    NativeResultLine("health",player.hp.coerceAtLeast(0).toString()),
                    NativeResultLine("gold",player.gold.toString())
                ),
                rewards = listOf(NativeResultLine("match_reward")),
                progression = listOf(NativeResultLine("placement",(player.placement?:8).toString())),
                canRematch = finished
            )
        )
    }

    override fun act(viewerId: String, action: String, args: Map<String, String>): NativeGameResult {
        if (action == "scout") {
            val player = players[viewerId] ?: return reject("Spectator")
            if (!allowed(player, TftCapability.CAN_SCOUT)) return reject("Scouting is unavailable")
            return scout(viewerId, args["target"])
        }
        if (finished) return NativeGameResult(false, message = "Game finished")
        val player = players[viewerId] ?: return NativeGameResult(false, message = "Spectator")
        if (player.eliminated) return NativeGameResult(false, message = "Trainer eliminated")
        return when (action) {
            "buy" -> buy(player, args["index"]?.toIntOrNull(), args["offerId"])
            "shop_lock" -> shopLock(player, args["locked"])
            "refresh" -> refresh(player)
            "buy_xp" -> buyXp(player)
            "deploy" -> deploy(player, args["bench"]?.toIntOrNull(), args["slot"]?.toIntOrNull())
            "move" -> move(player, args["from"]?.toIntOrNull(), args["to"]?.toIntOrNull())
            "bench" -> bench(player, args["slot"]?.toIntOrNull(), args["bench"]?.toIntOrNull())
            "swap_bench" -> swapBench(player, args["from"]?.toIntOrNull(), args["to"]?.toIntOrNull())
            "sell" -> sell(player, args)
            "equip_item" -> equipItem(player, args)
            "choose_augment" -> chooseAugment(player, args["id"])
            "carousel_move" -> carouselMove(player, args)
            "carousel_pick" -> carouselPick(player, args["index"]?.toIntOrNull(), args["revision"]?.toLongOrNull())
            "draft_pick" -> carouselPick(player, args["index"]?.toIntOrNull(), args["revision"]?.toLongOrNull())
            "tactician_move" -> tacticianMove(player,args)
            "tactician_emote" -> if (!allowed(player, TftCapability.CAN_EMOTE)) reject("Emotes are unavailable") else {
                player.tacticianEmoteUntil = System.currentTimeMillis() + 2_000L
                bump("${player.name} emotes")
                accept("Tactician emote")
            }
            "resign" -> resign(player)
            else -> NativeGameResult(false, message = "Unknown TFT action")
        }
    }

    private fun tacticianMove(player:PlayerState,args:Map<String,String>):NativeGameResult {
        if(phase==Phase.DRAFT) return reject("Use carousel movement during draft")
        val u=args["u"]?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return reject("Invalid tactician X")
        val v=args["v"]?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return reject("Invalid tactician Y")
        if(u !in 0.0..1.0 || v !in 0.0..1.0) return reject("Tactician destination is outside staging")
        val distance=kotlin.math.hypot(u-player.tacticianU,v-player.tacticianV)
        if(distance>TACTICIAN_MAX_NORMALIZED_MOVE+1.0e-6) return reject("Tactician movement step is too large")
        player.tacticianU=u
        player.tacticianV=v
        revision++
        return accept("Tactician moved")
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
            Phase.DRAFT -> if (nowMillis >= phaseEndsAt) {
                autoResolveDraft()
                roundIndex++
                if (!finished) startPlanning(nowMillis, false)
                changed = true
            }
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

    private fun buy(player: PlayerState, index: Int?, offerId: String?): NativeGameResult {
        if (!allowed(player,TftCapability.CAN_BUY_UNIT)) return reject("Buying is unavailable")
        val i = index ?: return reject("Missing shop slot")
        val unitId = player.shop.getOrNull(i) ?: return reject("Shop slot empty")
        if (offerId != shopOfferId(player, i, unitId)) return reject("Shop offer changed")
        val def = unitDefs[unitId] ?: return reject("Unit definition missing")
        if (!def.shopEligible) return reject("Unit is not sold in shops")
        if (def.elite != null && ownsElite(player)) return reject("Only one elite may be owned")
        if (player.gold < def.price) return reject("Not enough gold")
        val unit = incomingUnit(unitId)
        val transaction = TftAcquisition.resolve(player.bench, player.board, unit, ::mergeUpgradeItems)
        if (transaction.outcome == TftAcquisition.Outcome.REJECT) return reject("Bench is full")
        // Shop offers already own one pool reservation. Simulation has no side effects.
        player.gold -= def.price
        player.shop[i] = null
        commitAcquisition(player, transaction)
        nextUnitSerial++
        removeEliteOffers(player)
        bump("${player.name} bought ${def.id}")
        return accept("Bought ${def.id}")
    }

    private fun shopOfferId(player: PlayerState, index: Int, unitId: String) = "$sessionId:${player.id}:${player.shopGeneration}:$index:$unitId"
    private fun shopLock(player: PlayerState, locked: String?): NativeGameResult {
        if (!allowed(player, TftCapability.CAN_OPEN_SHOP)) return reject("Shop is unavailable")
        val desired = locked?.toBooleanStrictOrNull() ?: return reject("Missing lock state")
        if (player.shopLocked == desired) return NativeGameResult(true, false, "Shop lock unchanged")
        player.shopLocked = desired
        bump("Shop lock updated")
        return accept("Shop lock updated")
    }

    private fun refresh(player: PlayerState): NativeGameResult {
        if (!allowed(player,TftCapability.CAN_REROLL)) return reject("Rerolling is unavailable")
        val cost = refreshCost(player)
        if (player.gold < cost) return reject("Not enough gold")
        player.gold -= cost
        if (player.freeRerolls > 0) player.freeRerolls--
        rerollShop(player)
        bump("${player.name} refreshed shop")
        return accept("Shop refreshed")
    }

    private fun buyXp(player: PlayerState): NativeGameResult {
        if (!allowed(player,TftCapability.CAN_BUY_XP) || player.level >= progression.maxLevel) return reject("Cannot buy XP")
        val goldCost = buyXpCost(player)
        if (player.gold < goldCost) return reject("Not enough gold")
        grantXp(player, buyXpAmount(player))
        player.gold -= goldCost
        bump("${player.name} bought XP")
        return accept("XP purchased")
    }

    private fun deploy(player: PlayerState, benchIndex: Int?, slot: Int?): NativeGameResult {
        if (!allowed(player,TftCapability.CAN_MOVE_BOARD_UNIT)) return reject("Board is locked")
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
        if (!allowed(player,TftCapability.CAN_MOVE_BOARD_UNIT)) return reject("Board is locked")
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
        if (!allowed(player,TftCapability.CAN_INTERACT_BENCH)) return reject("Board is locked")
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
        if (!allowed(player,TftCapability.CAN_MOVE_BENCH_UNIT)) return reject("Board is locked")
        val a = from ?: return reject("Missing source")
        val b = to ?: return reject("Missing destination")
        if (a !in player.bench.indices || b !in player.bench.indices) return reject("Invalid bench slot")
        val unit = player.bench[a] ?: return reject("Bench slot empty")
        player.bench[a] = player.bench[b]; player.bench[b] = unit
        bump("${player.name} repositioned bench")
        return accept("Bench unit moved")
    }

    private fun sell(player: PlayerState, args: Map<String, String>): NativeGameResult {
        if (!allowed(player,TftCapability.CAN_SELL)) return reject("Selling is unavailable")
        val origin = args["origin"] ?: return reject("Missing origin")
        val index = args["index"]?.toIntOrNull() ?: return reject("Missing unit slot")
        val unit = when (origin) {
            "bench" -> player.bench.getOrNull(index)
            "board" -> player.board[index]
            else -> null
        } ?: return reject("Unit not found")
        if (args["instanceId"]?.let { it != unit.instanceId } == true) return reject("Unit target changed")
        val def = unitDefs[unit.unitId] ?: return reject("Unit definition missing")
        when (origin) {
            "bench" -> player.bench[index] = null
            "board" -> player.board.remove(index)
        }
        player.gold = (player.gold + sellValue(unit)).coerceAtMost(MAX_GOLD)
        pool.returnCopies(unit.poolSourceUnitId(), unit.reservedCopies())
        grantItems(player, unit.items.flatMap(::unpackItem))
        bump("${player.name} sold ${unit.unitId}")
        return accept("Unit sold")
    }

    private fun equipItem(player: PlayerState, args: Map<String, String>): NativeGameResult {
        if (!allowed(player,TftCapability.CAN_EQUIP_ITEM)) return reject("Equipping is unavailable")
        val itemIndex = args["item"]?.toIntOrNull() ?: return reject("Missing item")
        val item = player.itemBench.getOrNull(itemIndex) ?: return reject("Item not found")
        val origin = args["origin"] ?: return reject("Missing target origin")
        val unitIndex = args["index"]?.toIntOrNull() ?: return reject("Missing target")
        val unit = when (origin) { "bench" -> player.bench.getOrNull(unitIndex); "board" -> player.board[unitIndex]; else -> null } ?: return reject("Target unit not found")
        if (args["instanceId"]?.let { it != unit.instanceId } == true || args["itemId"]?.let { it != item } == true) return reject("Item target changed")
        val requestedSlot=args["itemSlot"]?.toIntOrNull()
        if (args.containsKey("itemSlot") && (requestedSlot == null || requestedSlot !in 0..2)) return reject("Invalid item slot")
        if (item.startsWith("full:") && requestedSlot != null && requestedSlot != unit.items.size) return reject("Selected item slot is occupied")
        val componentSlots=unit.items.indices.filter{!unit.items[it].startsWith("full:")}
        val loose=if(item.startsWith("full:"))-1 else if(requestedSlot!=null){
            requestedSlot.takeIf{it in componentSlots&&itemRecipes.containsKey(listOf(unit.items[it],item).sorted().joinToString("+"))}?:return reject("Selected component has no valid recipe")
        }else componentSlots.firstOrNull{itemRecipes.containsKey(listOf(unit.items[it],item).sorted().joinToString("+"))}?:-1
        if (loose >= 0) {
            if (!allowed(player,TftCapability.CAN_COMBINE_ITEM)) return reject("Combining is unavailable")
            val previous = unit.items[loose]
            val key = listOf(previous, item).sorted().joinToString("+")
            val recipe = itemRecipes[key] ?: return reject("No recipe for $previous + $item")
            player.itemBench.removeAt(itemIndex)
            drainPendingItems(player)
            unit.items[loose] = "full:${recipe.id}"
            player.itemEventSerial++
            player.lastItemEvent = "combine:$previous+$item->${recipe.id}:${unit.instanceId}"
            bump("${player.name} combined ${recipe.id} on ${unit.unitId}")
            return accept("Combined ${recipe.name}")
        }
        if(componentSlots.isNotEmpty()&&!item.startsWith("full:"))return reject("No compatible component recipe")
        if (unit.items.size >= 3) return reject("Unit item slots are full")
        player.itemBench.removeAt(itemIndex)
            drainPendingItems(player)
        unit.items += item
        player.itemEventSerial++
        player.lastItemEvent = "equip:$item:${unit.instanceId}"
        bump("${player.name} equipped $item on ${unit.unitId}")
        return accept("Item equipped")
    }

    private fun chooseAugment(player: PlayerState, id: String?): NativeGameResult {
        if (phase != Phase.PLANNING || player.augmentChoices.isEmpty()) return reject("No augment choice")
        val chosen = id?.takeIf(player.augmentChoices::contains) ?: return reject("Invalid augment")
        if (chosen in player.augments) return reject("Augment already owned")
        player.augments += chosen
        player.augmentChoices.clear()
        bump("${player.name} chose $chosen")
        return accept("Augment selected")
    }

    private fun carouselMove(player: PlayerState, args: Map<String, String>): NativeGameResult {
        if (phase != Phase.DRAFT || player.draftPicked) return reject("Carousel movement is not active")
        if (System.currentTimeMillis() < player.draftUnlockAt) return reject("Carousel release is locked")
        val x = args["x"]?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return reject("Invalid carousel X")
        val y = args["y"]?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return reject("Invalid carousel Y")
        val dx = x - player.carouselX; val dy = y - player.carouselY
        val step = sqrt(dx * dx + dy * dy)
        if (step > set.carousel.maxMovePerIntent + 1.0e-6) return reject("Impossible carousel movement")
        if (sqrt(x * x + y * y) > set.carousel.movementRadius) return reject("Carousel boundary exceeded")
        player.carouselX = x; player.carouselY = y; revision++
        return accept("Carousel movement accepted")
    }

    private fun carouselPick(player: PlayerState, index: Int?, expectedRevision: Long?): NativeGameResult {
        if (phase != Phase.DRAFT) return reject("Draft is not active")
        if (expectedRevision == null || expectedRevision != revision) return reject("Stale carousel revision")
        if (player.draftPicked) return reject("Already drafted")
        if (System.currentTimeMillis() < player.draftUnlockAt) return reject("Draft pick is not unlocked yet")
        val offer = index?.let { draftOffers.getOrNull(it) } ?: return reject("Invalid draft offer")
        if (offer.takenBy != null) return reject("Offer already taken")
        val dx = player.carouselX - offer.x; val dy = player.carouselY - offer.y
        if (sqrt(dx * dx + dy * dy) > set.carousel.pickupRadius) return reject("Tactician is out of pickup range")
        if (!resolveCarousel(player, offer)) return reject("Invalid draft reward")
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
        val alive = when (set.carousel.releaseOrder) {
            "highest_health_first" -> alivePlayers().sortedByDescending { it.hp }
            "seat_order" -> alivePlayers()
            "random_seeded" -> alivePlayers().shuffled(rng)
            else -> alivePlayers().sortedBy { it.hp }
        }
        alive.forEachIndexed { index, p ->
            p.draftPicked = false
            p.draftUnlockAt = now + (index / set.carousel.releaseWaveSize) * set.carousel.releaseDelayMs
            val angle = Math.PI * 2.0 * index / alive.size.coerceAtLeast(1)
            p.carouselX = cos(angle) * set.carousel.spawnRadius
            p.carouselY = sin(angle) * set.carousel.spawnRadius
        }
        val offerCount = max(set.carousel.offerCount, alive.size + 1)
        repeat(offerCount) { idx ->
            val unitId = pool.reserveForLevel(7) { it.elite == null } ?: return@repeat
            val item = set.components.randomOrNull(rng)?.id ?: ""
            val (x, y) = carouselOfferPosition(idx, offerCount)
            draftOffers += DraftOffer(idx, unitId, item, x = x, y = y)
        }
        phaseEndsAt = now + set.carousel.durationMs
        bump("${roundLabel()} shared draft")
    }

    private fun autoResolveDraft() {
        alivePlayers().filterNot { it.draftPicked }.sortedBy { it.hp }.forEach { player ->
            val offer = draftOffers.firstOrNull { it.takenBy == null } ?: return@forEach
            check(resolveCarousel(player, offer)) { "Invalid reserved carousel reward" }
        }
        draftOffers.filter { it.takenBy == null }.forEach { pool.returnCopies(it.unitId, 1) }
        draftOffers.clear()
    }

    private fun startPlanning(now: Long, firstRound: Boolean) {
        phase = Phase.PLANNING; combats.clear()
        alivePlayers().forEach { player ->
            player.lastPveLoot.clear()
            if (player.legacyIncomePending) { grantIncome(player); player.legacyIncomePending = false }
            if (isAugmentRound(roundLabel()) && player.augmentChoices.isEmpty()) {
                val owned = player.augments.toSet()
                val tier = roundDefinition().augmentTier
                val eligible = set.augments.filter { it.id !in owned && (tier == null || it.tier == tier) }
                player.augmentChoices += eligible.shuffled(rng).take(3).map { it.id }
            }
            val authoredFreeRerolls = playerModifiers(player).value(TftPlayerModifier.FREE_REFRESH_COUNT).toInt().coerceAtLeast(0)
            player.freeRerolls = maxOf(player.freeRerolls, authoredFreeRerolls)
            if (!player.shopLocked || firstRound) rerollShop(player)
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
                    val baseDrops = match.pve.componentDrops.coerceAtLeast(0)
                    val drops = playerModifiers(a).apply(TftPlayerModifier.PVE_DROP_COUNT, baseDrops.toDouble()).toInt().coerceAtLeast(0)
                    val presentationLoot = if (match.pve.lootTable != null) {
                        settleLoot(a, match.pve.lootTable, match.pve.lootRolls, baseDrops) +
                            grantRandomComponents(a, (drops - baseDrops).coerceAtLeast(0))
                    } else {
                        grantRandomComponents(a, drops)
                    }
                    a.lastPveLoot.clear()
                    a.lastPveLoot.addAll(presentationLoot.take(12))
                    a.pveLootSerial++
                } else {
                    onLoss(a)
                    a.hp -= PVE_LOSS_DAMAGE
                }
                if (a.hp <= 0) eliminated += a else healFromModifiers(a)
                return@forEach
            }
            val b = match.bId?.let(players::get)
            if (b == null) {
                when (outcome.winnerTeam) {
                    0 -> onWin(a)
                    1 -> { onLoss(a); a.hp -= playerDamage(stageNumber(), outcome.survivingTeam1) }
                    else -> { a.streak = 0; a.hp -= DRAW_DAMAGE }
                }
                if (a.hp <= 0) eliminated += a else healFromModifiers(a)
                return@forEach
            }
            when (outcome.winnerTeam) {
                0 -> { onWin(a); onLoss(b); b.hp -= playerDamage(stageNumber(), outcome.survivingTeam0) }
                1 -> { onWin(b); onLoss(a); a.hp -= playerDamage(stageNumber(), outcome.survivingTeam1) }
                else -> { a.streak = 0; b.streak = 0; a.hp -= DRAW_DAMAGE; b.hp -= DRAW_DAMAGE }
            }
            if (a.hp <= 0) eliminated += a else healFromModifiers(a)
            if (b.hp <= 0) eliminated += b else healFromModifiers(b)
        }
        val participants = unique.flatMap { listOfNotNull(it.aId, it.bId) }.toSet()
        players.values.filter { it.id in participants && !it.eliminated }.forEach(::advancePermanentEvolutions)
        eliminatePlayers(eliminated.distinctBy { it.id })
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
    private fun healFromModifiers(player: PlayerState) {
        val heal = playerModifiers(player).value(TftPlayerModifier.POST_ROUND_HEAL).toInt()
        if (heal > 0 && player.hp > 0) player.hp = (player.hp + heal).coerceAtMost(100)
    }

    private fun rerollShop(player: PlayerState) {
        player.shop.forEach { unit -> if (unit != null) pool.returnCopies(unit, 1) }
        player.shopGeneration++
        val eliteOwned = ownsElite(player)
        for (i in player.shop.indices) player.shop[i] = pool.reserveForLevel(player.level) { !eliteOwned || it.elite == null }
    }

    private fun ensureFormation(player: PlayerState) {
        val cap = unitCap(player)
        while (player.board.size < cap) {
            val benchIndex = player.bench.indexOfFirst { it != null }; if (benchIndex < 0) break
            val slot = (0 until formationCells).firstOrNull { it !in player.board } ?: break
            player.board[slot] = player.bench[benchIndex]!!; player.bench[benchIndex] = null
        }
    }

    private fun incomingUnit(unitId: String, poolCopies: Int = 1): TftOwnedUnit {
        val def = unitDefs.getValue(unitId)
        return TftOwnedUnit("u" + nextUnitSerial, unitId, def.purchaseStar, poolCopies = poolCopies, poolUnitId = unitId)
    }

    private fun advancePermanentEvolutions(player: PlayerState) {
        player.board.toMap().forEach { (slot, owned) ->
            val result = TftPermanentEvolution.advance(owned, unitDefs)
            if (result.unit == owned) return@forEach
            player.board[slot] = result.unit
            result.evolvedFrom?.let { source ->
                player.acquisitionSerial++
                player.acquisitionEvent = "EVOLVE~" + source + "~" + result.unit.unitId + "~" + owned.instanceId
                bump(player.name + ": " + source + " permanently evolved into " + result.unit.unitId)
            }
        }
    }

    private fun ownsElite(player: PlayerState) = unitLocations(player).any { unitDefs[it.unit.unitId]?.elite != null }

    private fun removeEliteOffers(player: PlayerState) {
        if (!ownsElite(player)) return
        player.shop.indices.forEach { index ->
            val id = player.shop[index]
            if (id != null && unitDefs[id]?.elite != null) {
                pool.returnCopies(id, 1)
                player.shop[index] = null
            }
        }
    }

    private fun commitAcquisition(player: PlayerState, transaction: TftAcquisition.Resolution) {
        check(transaction.outcome != TftAcquisition.Outcome.REJECT)
        player.bench.clear(); player.bench.addAll(transaction.bench)
        player.board.clear(); player.board.putAll(transaction.board)
        grantItems(player, transaction.returnedItems)
        transaction.upgrades.forEach { upgrade ->
            player.acquisitionSerial++
            player.acquisitionEvent = "STAR_UP~${upgrade.unitId}~${upgrade.star}~${upgrade.instanceId}"
            bump("${player.name}: ${upgrade.unitId} -> ${upgrade.star}★")
        }
    }

    /** Guaranteed rewards share the shop's simulation; full capacity converts only the incoming unit. */
    private fun acquireReward(player: PlayerState, unit: TftOwnedUnit, source: String) {
        val def = unitDefs.getValue(unit.unitId)
        val transaction = if (def.elite != null && ownsElite(player)) TftAcquisition.Resolution(TftAcquisition.Outcome.REJECT)
            else TftAcquisition.resolve(player.bench, player.board, unit, ::mergeUpgradeItems)
        if (transaction.outcome != TftAcquisition.Outcome.REJECT) commitAcquisition(player, transaction)
        else {
            val before = player.gold
            player.gold = (player.gold + sellValue(unit)).coerceAtMost(MAX_GOLD)
            grantItems(player, unit.items.flatMap(::unpackItem))
            pool.returnCopies(unit.poolSourceUnitId(), unit.reservedCopies())
            player.acquisitionSerial++
            player.acquisitionEvent = "${source}_AUTO_SELL~${unit.unitId}~${player.gold - before}~${unit.items.joinToString(",")}"
            bump(player.acquisitionEvent)
        }
        nextUnitSerial++
        removeEliteOffers(player)
    }

    private fun resolveCarousel(player: PlayerState, offer: DraftOffer): Boolean {
        if (player.draftPicked || offer.takenBy != null || offer.unitId !in unitDefs) return false
        if (offer.itemId.isNotBlank() && set.components.none { it.id == offer.itemId }) return false
        val unit = incomingUnit(offer.unitId).also { if (offer.itemId.isNotBlank()) it.items += offer.itemId }
        acquireReward(player, unit, "CAROUSEL")
        offer.takenBy = player.id
        player.draftPicked = true
        return true
    }

    private fun grantItems(player: PlayerState, items: List<String>) {
        player.pendingItems.addAll(items)
        drainPendingItems(player)
    }

    private fun drainPendingItems(player: PlayerState) {
        val amount = minOf(ITEM_TRAY_CAPACITY - player.itemBench.size, player.pendingItems.size).coerceAtLeast(0)
        if (amount > 0) {
            player.itemBench.addAll(player.pendingItems.take(amount))
            player.pendingItems.subList(0, amount).clear()
        }
    }

    private fun sellValue(unit: TftOwnedUnit): Int {
        val current = unitDefs.getValue(unit.unitId)
        val source = unitDefs[unit.poolSourceUnitId()] ?: current
        return if (current.elite != null) current.price else source.cost * copiesForStar(unit.star)
    }

    private fun mergeUpgradeItems(raw: List<String>): Pair<List<String>, List<String>> {
        val kept = mutableListOf<String>()
        val overflow = mutableListOf<String>()
        raw.flatMap(::unpackItem).forEach { item ->
            if (!item.startsWith("full:")) {
                val combineAt = kept.indices.firstOrNull { index ->
                    val existing = kept[index]
                    !existing.startsWith("full:") && itemRecipes.containsKey(listOf(existing, item).sorted().joinToString("+"))
                }
                if (combineAt != null) {
                    val existing = kept[combineAt]
                    val recipe = itemRecipes.getValue(listOf(existing, item).sorted().joinToString("+"))
                    kept[combineAt] = "full:${recipe.id}"
                    return@forEach
                }
            }
            if (kept.size < 3) kept += item else overflow += item
        }
        return kept to overflow
    }

    private fun releasePlayerPool(player: PlayerState) {
        player.shop.forEach { it?.let { id -> pool.returnCopies(id, 1) } }; player.shop.indices.forEach { player.shop[it] = null }
        unitLocations(player).forEach { ref -> pool.returnCopies(ref.unit.unitId, ref.unit.reservedCopies()); grantItems(player, ref.unit.items.flatMap(::unpackItem)) }
        player.board.clear(); player.bench.indices.forEach { player.bench[it] = null }
    }

    private fun buildBoardView(player: PlayerState): List<String> {
        val cells = MutableList(formationCells * 2) { "" }
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
            val cell = formationCells + slot
            if (cell in cells.indices) cells[cell] = listOf(unit.instanceId, unit.unitId, def.presentation.species, unit.star, "-1", "-1", "0", "0", "0", def.presentation.resolverAspects().joinToString(","), unit.items.joinToString(","), def.cost, def.role, "", 0, 0L, 0L, 1, def.presentation.scale).joinToString("~")
        }
        return cells
    }

    private fun encodeCombatUnit(unit: TftCombatUnit, relativeTeam: Int): String = listOf(
        unit.instanceId, unit.definition.id, unit.definition.presentation.species, unit.star, unit.hp, unit.maxHp,
        unit.mana, unit.maxMana, relativeTeam, unit.definition.presentation.resolverAspects().joinToString(","), unit.items.joinToString(","),
        unit.definition.cost, unit.definition.role, unit.targetId.orEmpty(), unit.casts, unit.damageDone, unit.healingDone,
        if (unit.alive) 1 else 0, unit.definition.presentation.scale
    ).joinToString("~")

    private fun encodeBench(player: PlayerState): String = player.bench.mapIndexedNotNull { index, unit ->
        unit ?: return@mapIndexedNotNull null
        val def = unitDefs[unit.unitId] ?: return@mapIndexedNotNull null
        listOf(index, unit.instanceId, unit.unitId, def.presentation.species, unit.star, def.presentation.resolverAspects().joinToString(","), unit.items.joinToString(","), def.cost, def.role, def.presentation.scale).joinToString("~")
    }.joinToString(";")

    private fun encodePublicContestedUnits(viewerId:String):String = players.values.asSequence()
        .filter{it.id!=viewerId}
        .flatMap{it.board.values.asSequence()}
        .map{it.unitId}
        .distinct()
        .sorted()
        .joinToString(",")

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

    // Public immutable metadata has stable ordering and never encodes a player's private shop/bench.
    private val encodedUnitCatalog: String by lazy {
        val ids = set.units.sortedWith(compareBy<TftUnitDefinition> { it.cost }.thenBy { it.id }).map { it.id }
        JsonObject().apply {
            ids.forEach { id ->
                val def = unitDefs[id] ?: return@forEach
                add(def.id, JsonObject().apply {
                    addProperty("name", def.id.replace('_', ' ').replaceFirstChar { it.uppercase() }.take(96))
                    addProperty("species", def.presentation.species.take(160))
                    addProperty("scale", def.presentation.scale)
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

    private val encodedTraitCatalog: String by lazy {
        val ids = set.traits.sortedBy { it.name }.map { it.id }
        JsonObject().apply {
            ids.forEach { id ->
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
                val mechanic = buildList {
                    def.effects.forEach { (key, value) -> add(key + ": " + value) }
                    def.traitEffects.orEmpty().forEach { (trait, effects) ->
                        effects.forEach { (key, value) -> add(trait + "." + key + ": " + value) }
                    }
                    def.playerModifiers.forEach { (key, value) -> add(key.name.lowercase() + ": " + value) }
                }
                addProperty("mechanic", mechanic.joinToString(" • "))
            })
        }
    }.toString()

    private fun encodeAugmentChoices(player: PlayerState): String = player.augmentChoices.joinToString(";") { id ->
        val def = augmentDefs[id]
        listOf(
            id,
            def?.name ?: id,
            def?.description ?: "",
            def?.aiWeight ?: 50,
            def?.tags.orEmpty().joinToString(","),
            def?.tier ?: "Gold"
        ).joinToString("~")
    }

    private fun encodeDraft(player: PlayerState): String {
        if (phase != Phase.DRAFT) return ""
        val unlocked = System.currentTimeMillis() >= player.draftUnlockAt
        return draftOffers.joinToString(";") { offer ->
            val def = unitDefs[offer.unitId]
            listOf(offer.index, offer.unitId, def?.presentation?.species ?: "", offer.itemId, offer.takenBy ?: "", if (unlocked) 1 else 0, def?.cost ?: 1, def?.traits?.joinToString(",") ?: "", offer.x, offer.y, def?.presentation?.resolverAspects()?.joinToString(",") ?: "", def?.presentation?.scale ?: 1.0).joinToString("~")
        }
    }

    private fun carouselOfferPosition(index: Int, count: Int): Pair<Double, Double> {
        val angle = Math.PI * 2.0 * index / count.coerceAtLeast(1) - Math.PI / 2.0
        return cos(angle) * set.carousel.ringRadius to sin(angle) * set.carousel.ringRadius
    }

    private fun traitCounts(player: PlayerState): Map<String, Int> = player.board.values.distinctBy { it.unitId }.mapNotNull { unitDefs[it.unitId] }.flatMap { it.traits }.groupingBy { it }.eachCount()
    private fun playerModifiers(player: PlayerState): TftPlayerModifierSet = TftPlayerModifierSet.compile(
        player.augments.mapNotNull(augmentDefs::get).map { it.playerModifiers }
    )
    private fun refreshCost(player: PlayerState) = if (player.freeRerolls > 0) 0 else
        playerModifiers(player).apply(TftPlayerModifier.SHOP_REFRESH_COST, 2.0).toInt().coerceAtLeast(0)
    private fun buyXpCost(player: PlayerState) = playerModifiers(player)
        .apply(TftPlayerModifier.XP_PURCHASE_COST, progression.buyXp.goldCost.toDouble()).toInt().coerceAtLeast(0)
    private fun buyXpAmount(player: PlayerState) = playerModifiers(player)
        .apply(TftPlayerModifier.XP_PURCHASE_AMOUNT, progression.buyXp.xpGranted.toDouble()).toInt().coerceAtLeast(1)
    private fun capabilities(player: PlayerState): Set<TftCapability> =
        if (finished || player.eliminated) emptySet() else set.rules.phaseCapabilities[phase.id].orEmpty()
    private fun allowed(player: PlayerState, capability: TftCapability) = capability in capabilities(player)
    private fun canEditBoard(player: PlayerState) = allowed(player, TftCapability.CAN_MOVE_BOARD_UNIT)
    private fun unitCap(player: PlayerState) = playerModifiers(player)
        .apply(TftPlayerModifier.BOARD_CAPACITY, player.level.toDouble()).toInt().coerceIn(1, set.rules.maxBoardCapacity)

    private fun settleRound(player: PlayerState, roundType: String) {
        if (player.lastSettledRound >= roundIndex) return
        val round = roundDefinition()
        val modifiers = playerModifiers(player)
        val amount = if (round.passiveXp) TftProgression.passiveAmount(
            progression,
            roundType,
            modifiers.value(TftPlayerModifier.XP_GAIN_FLAT),
            modifiers.value(TftPlayerModifier.XP_GAIN_MULTIPLIER)
        ) else 0
        val xp = TftProgression.grant(progression, player.level, player.xp, amount)
        if (round.income) {
            grantIncome(player)
        } else {
            player.lastIncome = 0
            player.lastInterest = 0
            player.lastStreakGold = 0
        }
        player.level = xp.level; player.xp = xp.xp
        player.lastXpGranted = xp.granted; player.lastLevelsGained = xp.levelsGained
        player.lastSettledRound = roundIndex
    }

    private fun grantXp(player: PlayerState, amount: Int) {
        val xp = TftProgression.grant(progression, player.level, player.xp, amount.coerceAtLeast(0))
        player.level = xp.level; player.xp = xp.xp
        player.lastXpGranted = xp.granted; player.lastLevelsGained = xp.levelsGained
    }

    private fun grantRandomComponents(player: PlayerState, count: Int): List<String> = buildList {
        repeat(count.coerceIn(0, 6)) {
            set.components.randomOrNull(rng)?.id?.let { id ->
                grantItems(player, listOf(id))
                add(id)
            }
        }
    }

    private fun settleLoot(player: PlayerState, tableId: String, configuredRolls: Int, fallbackRolls: Int):List<String> {
        val table = set.lootTables.firstOrNull { it.id == tableId } ?: return emptyList()
        val presented=mutableListOf<String>()
        val rolls = if (configuredRolls > 0) configuredRolls else fallbackRolls
        repeat(rolls.coerceIn(0, 20)) {
            val total = table.entries.sumOf { entry -> entry.weight }
            var roll = rng.nextInt(total)
            val entry = table.entries.first { candidate -> roll -= candidate.weight; roll < 0 }
            when (entry.type) {
                "gold" -> {
                    player.gold = (player.gold + entry.amount).coerceAtMost(MAX_GOLD)
                    repeat(entry.amount.coerceAtMost(3)){presented+="loot:gold"}
                }
                "component" -> repeat(entry.amount) {
                    entry.value?.takeIf { id -> set.components.any { c -> c.id == id } }?.let { id ->
                        grantItems(player, listOf(id));presented+=id
                    }
                }
                "full_item" -> repeat(entry.amount) {
                    entry.value?.takeIf { id -> set.fullItems.any { f -> f.id == id } }?.let { id ->
                        val item="full:$id";grantItems(player, listOf(item));presented+=item
                    }
                }
                "unit" -> repeat(entry.amount) {
                    entry.value?.takeIf(unitDefs::containsKey)?.let { id ->
                        acquireReward(player, incomingUnit(id, poolCopies = 0), "PVE")
                        presented+="loot:unit"
                    }
                }
                "xp" -> { grantXp(player, entry.amount);presented+="loot:xp" }
                "free_reroll" -> { player.freeRerolls = (player.freeRerolls + entry.amount).coerceAtMost(100);presented+="loot:reroll" }
                "choice" -> { player.specialRewards += "choice:${entry.choices.joinToString("|")}";presented+="loot:special" }
                "special" -> { player.specialRewards += (entry.value ?: "reward");presented+="loot:special" }
            }
        }
        return presented
    }

    private fun xpToNext(level: Int): Int = if (level >= progression.maxLevel) 0
        else progression.xpToNextByLevel.getValue(level.toString())
    private fun playerDamage(stage: Int, survivors: List<TftCombatUnit>): Int {
        val base = when (stage) { 1 -> 0; 2 -> 2; 3 -> 5; 4 -> 8; 5 -> 10; 6 -> 13; else -> 15 + (stage - 7) * 2 }
        val units = survivors.fold(0) { acc, unit -> acc + when (unit.star) { 3 -> 3; 2 -> 2; else -> 1 } }
        return (base + units).coerceAtLeast(1)
    }
    private fun streakGold(streak: Int): Int = when (abs(streak)) { in 0..1 -> 0; 2, 3 -> 1; 4 -> 2; else -> 3 }
    private fun autoChooseAugments() {
        alivePlayers().forEach { player ->
            val available = player.augmentChoices.filterNot(player.augments::contains)
            if (available.isNotEmpty()) player.augments += available.random(rng)
            player.augmentChoices.clear()
        }
    }
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
        val requested = selection?.takeIf(String::isNotBlank) ?: return set.defaultTactician
        val requestedEntity = if (':' in requested) requested else "minecraft:$requested"
        return set.tacticians.firstOrNull { tactician ->
            tactician.id == requested ||
                tactician.entity == requestedEntity ||
                tactician.presentation?.species == requested
        }?.id ?: set.defaultTactician
    }

    private fun encodeItemCatalog():String = JsonObject().apply {
        set.components.forEach { component ->
            add(component.id,JsonObject().apply {
                addProperty("kind","component")
                addProperty("name",component.name)
                addProperty("stack",component.stack)
                addProperty("effects",component.effects.entries.joinToString(","){"${it.key}=${it.value}"})
            })
        }
        set.fullItems.forEach { item ->
            add(item.id,JsonObject().apply {
                addProperty("kind","full")
                addProperty("name",item.name)
                addProperty("stack",item.stack)
                addProperty("components",item.components.joinToString(","))
                addProperty("effects",item.effects.entries.joinToString(","){"${it.key}=${it.value}"})
            })
        }
    }.toString()

    private fun encodeBotStrategy(participantId:String):String {
        if(set.botStrategies.isEmpty())return ""
        val strategy=set.botStrategies[Math.floorMod(participantId.hashCode(),set.botStrategies.size)]
        fun list(values:List<String>)=values.joinToString(",")
        fun map(values:Map<String,Double>)=values.entries.sortedBy{it.key}.joinToString(","){"${it.key}=${it.value}"}
        val transitions=strategy.transitionRules.joinToString(",") { rule -> listOf(rule.phase,rule.minimumLevel,rule.maximumLevel,rule.team,rule.minimumCopies,rule.maximumContested).joinToString(":") }
        return listOf(strategy.id,list(strategy.preferredTeams),list(strategy.fallbackTeams),list(strategy.preferredTraits),list(strategy.preferredCarryRoles),list(strategy.preferredItemTags),list(strategy.preferredAugmentTags),map(strategy.economyProfile),map(strategy.rollProfile),map(strategy.levelProfile),map(strategy.positioningProfile),transitions).joinToString("~")
    }
    private fun countCopies(player:PlayerState,unitId:String)=unitLocations(player).filter{it.unit.unitId==unitId}.sumOf{copiesForStar(it.unit.star)}
    private fun copiesForStar(star: Int) = when (star) { 2 -> 3; 3 -> 9; else -> 1 }
    /** Legacy combo identities are unpacked; authored completed items stay completed. */
    private fun unpackItem(item: String): List<String> = when {
        item.startsWith("combo:") -> item.removePrefix("combo:").split('+').filter(String::isNotBlank)
        else -> listOf(item)
    }
    private fun rotateCell(cell: Int): Int = formationCells * 2 - 1 - cell
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
        val pendingItems: List<String>? = null,
        val shopLocked: Boolean = false,
        val shopGeneration: Long = 0L,
        val acquisitionSerial: Long = 0L,
        val acquisitionEvent: String? = null,
        val augments: List<String>,
        val augmentChoices: List<String>,
        val freeRerolls: Int,
        val draftPicked: Boolean,
        val draftUnlockRemainingMs: Long,
        val carouselX: Double = 0.0,
        val carouselY: Double = 0.0,
        val tacticianU: Double = .5,
        val tacticianV: Double = .5,
        val lastIncome: Int,
        val lastInterest: Int,
        val lastStreakGold: Int,
        val lastSettledRound: Int,
        val lastXpGranted: Int,
        val lastLevelsGained: Int,
        val legacyIncomePending: Boolean,
        val lastItemEvent: String? = null,
        val itemEventSerial: Long = 0L,
        val lastPveLoot: List<String> = emptyList(),
        val pveLootSerial: Long = 0L,
        val tactician: String?,
        val tacticianEmoteRemainingMs: Long = 0L,
        val arena: String = "",
        val specialRewards: List<String> = emptyList()
    )
    private data class BoardSnapshot(val slot: Int, val unit: TftOwnedUnit)
    private data class DraftSnapshot(
        val index: Int,
        val unitId: String,
        val itemId: String,
        val takenBy: String?,
        val x: Double = Double.NaN,
        val y: Double = Double.NaN
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
        val pendingItems: MutableList<String> = mutableListOf(),
        var shopLocked: Boolean = false, var shopGeneration: Long = 0L,
        var acquisitionSerial: Long = 0L, var acquisitionEvent: String = "",
        val itemBench: MutableList<String> = mutableListOf(), val augments: MutableList<String> = mutableListOf(),
        val augmentChoices: MutableList<String> = mutableListOf(), var freeRerolls: Int = 0, var draftPicked: Boolean = false,
        var draftUnlockAt: Long = 0L, var carouselX: Double = 0.0, var carouselY: Double = 0.0,
        var tacticianU:Double=.5,var tacticianV:Double=.5,
        var lastIncome: Int = 0, var lastInterest: Int = 0, var lastStreakGold: Int = 0,
        var lastSettledRound: Int = -1, var lastXpGranted: Int = 0, var lastLevelsGained: Int = 0, var legacyIncomePending: Boolean = false,
        var tactician: String = "", var arena: String = "kanto_stadium", var tacticianEmoteUntil:Long=0L,var lastItemEvent:String="",var itemEventSerial:Long=0L,
        val lastPveLoot:MutableList<String> = mutableListOf(),var pveLootSerial:Long=0L,
        val specialRewards: MutableList<String> = mutableListOf()
    )
    private data class DraftOffer(val index: Int, val unitId: String, val itemId: String, var takenBy: String? = null, val x: Double = 0.0, val y: Double = 0.0)
    private data class MatchCombat(val aId:String,val bId:String?,val opponentLabel:String,val engine:TftCombatEngine,val pve:TftPveRoundDefinition?=null,val ghostOwnerId:String?=null,var resolved:Boolean=false){fun opponentNameFor(viewer:String):String=when{pve!=null->"PvE";viewer==aId->opponentLabel;bId!=null&&viewer==bId->"Opponent";else->opponentLabel}}
    private enum class Phase(val id:String){DRAFT("draft"),PLANNING("planning"),COMBAT("combat"),POST_COMBAT("post"),FINISHED("finished")}

    private class SharedPool(private val set:TftSetDefinition,private val rng:Random){
        private val defs=set.units.associateBy{it.id};private val counts=linkedMapOf<String,Int>();private val initial=linkedMapOf<String,Int>();private val odds=set.shopOdds.associateBy{it.level}
        init{set.units.forEach{unit->val amount=set.poolSizeByCost[unit.cost.toString()]?:error("Missing TFT pool size for cost ${unit.cost}");counts[unit.id]=if(unit.shopEligible)amount else 0;initial[unit.id]=if(unit.shopEligible)amount else 0}}
        fun reserveForLevel(level:Int, eligible:(io.github.aristheg201.svhub.native.game.tft.TftUnitDefinition)->Boolean = {true}):String?{val row=odds[level]?:odds.values.minByOrNull{abs(it.level-level)}?:return null;repeat(7){val cost=rollCost(row.odds);reserveCost(cost,eligible)?.let{return it}};return counts.entries.filter{it.value>0&&eligible(defs.getValue(it.key))}.weightedByCount()?.also{counts[it]=counts.getValue(it)-1}}
        fun returnCopies(unitId:String,amount:Int){if(amount<=0||unitId !in counts)return;counts[unitId]=(counts.getValue(unitId)+amount).coerceAtMost(initial.getValue(unitId))}
        fun snapshotCounts(): Map<String, Int> = counts.toMap()
        fun restoreCounts(saved: Map<String, Int>) {
            require(saved.keys == counts.keys) { "TFT recovery pool keys do not match set units" }
            counts.keys.forEach { id ->
                counts[id] = (saved[id] ?: 0).coerceIn(0, initial.getValue(id))
            }
        }
        private fun reserveCost(cost:Int,eligible:(io.github.aristheg201.svhub.native.game.tft.TftUnitDefinition)->Boolean):String?{val candidates=counts.entries.filter{it.value>0&&defs[it.key]?.cost==cost&&eligible(defs.getValue(it.key))};val chosen=candidates.weightedByCount()?:return null;counts[chosen]=counts.getValue(chosen)-1;return chosen}
        private fun rollCost(values:List<Int>):Int{val roll=rng.nextInt(100);var acc=0;values.forEachIndexed{index,chance->acc+=chance;if(roll<acc)return index+1};return 1}
        private fun List<Map.Entry<String,Int>>.weightedByCount():String?{val total=sumOf{it.value};if(total<=0)return null;var roll=rng.nextInt(total);for(entry in this){roll-=entry.value;if(roll<0)return entry.key};return lastOrNull()?.key}
    }

    companion object {
        private const val ITEM_TRAY_CAPACITY=128
        private const val MAX_GOLD=999
        private const val PVE_LOSS_DAMAGE=5;private const val DRAW_DAMAGE=2;private const val DRAFT_TOTAL_MS=120_000L
        private const val TACTICIAN_MAX_NORMALIZED_MOVE=.22
    }
}
