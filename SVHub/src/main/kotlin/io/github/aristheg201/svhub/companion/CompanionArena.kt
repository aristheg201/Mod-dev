package io.github.aristheg201.svhub.companion

import com.google.gson.JsonObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

object CompanionArena {
    data class Fighter(val id: String, val name: String, val maxHp: Int, val power: Int, val guard: Int, val speed: Int)
    data class Match(
        val player: Fighter,
        val enemy: Fighter,
        var playerHp: Int = player.maxHp,
        var enemyHp: Int = enemy.maxHp,
        var playerEnergy: Int = 0,
        var enemyEnergy: Int = 0,
        var playerGuarding: Boolean = false,
        var turn: Int = 1,
        var finished: Boolean = false,
        var result: String = "",
        var log: String = "Trận đấu bắt đầu."
    )

    val roster = linkedMapOf(
        "allay" to Fighter("allay", "Allay", 72, 14, 8, 18),
        "axolotl" to Fighter("axolotl", "Axolotl", 86, 12, 12, 12),
        "bee" to Fighter("bee", "Bee", 68, 16, 7, 20),
        "cat" to Fighter("cat", "Cat", 74, 14, 8, 19),
        "fox" to Fighter("fox", "Fox", 78, 15, 8, 18),
        "frog" to Fighter("frog", "Frog", 82, 13, 11, 14),
        "parrot" to Fighter("parrot", "Parrot", 70, 14, 7, 21),
        "rabbit" to Fighter("rabbit", "Rabbit", 72, 13, 7, 22),
        "wolf" to Fighter("wolf", "Wolf", 92, 17, 11, 15),
        "armadillo" to Fighter("armadillo", "Armadillo", 104, 11, 18, 8),
        "sniffer" to Fighter("sniffer", "Sniffer", 112, 15, 15, 7)
    )

    private val matches = ConcurrentHashMap<UUID, Match>()

    fun start(playerId: UUID, fighterId: String): String {
        val fighter = roster[fighterId] ?: return "Hãy chọn một Linh Thú trước."
        val candidates = roster.values.filter { it.id != fighterId }
        val enemy = candidates[Math.floorMod(playerId.hashCode() + matches.size, candidates.size)]
        matches[playerId] = Match(fighter, enemy)
        return "Đối thủ ${enemy.name} đã vào sân."
    }

    @Synchronized
    fun act(playerId: UUID, action: String): String {
        val match = matches[playerId] ?: return "Chưa có trận đấu."
        if (match.finished) return "Trận đã kết thúc."
        match.playerGuarding = false
        val playerMessage = when (action) {
            "attack" -> {
                val damage = damage(match.player.power, match.enemy.guard, match.turn)
                match.enemyHp = max(0, match.enemyHp - damage)
                match.playerEnergy = (match.playerEnergy + 1).coerceAtMost(3)
                "${match.player.name} đánh trúng $damage sát thương."
            }
            "skill" -> {
                if (match.playerEnergy < 2) return "Cần 2 năng lượng để dùng kỹ năng."
                val damage = damage(match.player.power + 10, match.enemy.guard / 2, match.turn + 7)
                match.enemyHp = max(0, match.enemyHp - damage)
                match.playerEnergy -= 2
                "${match.player.name} tung kỹ năng: $damage sát thương."
            }
            "guard" -> {
                match.playerGuarding = true
                match.playerEnergy = (match.playerEnergy + 1).coerceAtMost(3)
                "${match.player.name} thủ thế và tích năng lượng."
            }
            else -> return "Thao tác không hợp lệ."
        }
        if (match.enemyHp <= 0) return finish(match, true, playerMessage)

        val enemyUsesSkill = match.enemyEnergy >= 2 && (match.turn + match.enemy.speed) % 3 == 0
        val raw = if (enemyUsesSkill) {
            match.enemyEnergy -= 2
            damage(match.enemy.power + 9, match.player.guard / 2, match.turn + 11)
        } else {
            match.enemyEnergy = (match.enemyEnergy + 1).coerceAtMost(3)
            damage(match.enemy.power, match.player.guard, match.turn + 3)
        }
        val received = if (match.playerGuarding) (raw + 1) / 2 else raw
        match.playerHp = max(0, match.playerHp - received)
        val enemyMessage = if (enemyUsesSkill) "${match.enemy.name} phản công bằng kỹ năng: $received." else "${match.enemy.name} phản công: $received."
        match.log = "$playerMessage $enemyMessage"
        match.turn++
        if (match.playerHp <= 0) return finish(match, false, match.log)
        return match.log
    }

    fun clear(playerId: UUID) = matches.remove(playerId)

    fun appendState(playerId: UUID, target: JsonObject) {
        val match = matches[playerId] ?: return
        val arena = JsonObject()
        arena.addProperty("playerId", match.player.id)
        arena.addProperty("playerName", match.player.name)
        arena.addProperty("playerHp", match.playerHp)
        arena.addProperty("playerMaxHp", match.player.maxHp)
        arena.addProperty("playerEnergy", match.playerEnergy)
        arena.addProperty("enemyId", match.enemy.id)
        arena.addProperty("enemyName", match.enemy.name)
        arena.addProperty("enemyHp", match.enemyHp)
        arena.addProperty("enemyMaxHp", match.enemy.maxHp)
        arena.addProperty("turn", match.turn)
        arena.addProperty("finished", match.finished)
        arena.addProperty("result", match.result)
        arena.addProperty("log", match.log)
        target.add("arena", arena)
    }

    private fun finish(match: Match, playerWon: Boolean, prefix: String): String {
        match.finished = true
        match.result = if (playerWon) "Victory" else "Defeat"
        match.log = "$prefix ${if (playerWon) "Bạn thắng trận." else "Bạn thua trận."}"
        return match.log
    }

    private fun damage(power: Int, guard: Int, salt: Int): Int = (power - guard / 3 + (salt % 5) - 2).coerceAtLeast(3)
}
