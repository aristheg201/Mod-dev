package io.github.aristheg201.svhub.native

import com.cobblemon.mod.common.Cobblemon
import io.github.aristheg201.svhub.SVHub
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference

object SkiesSkinsBridge {
    data class ApplyResult(val ok: Boolean, val message: String)
    private data class Api(val instance: Any, val getSkin: Method, val getUserData: Method, val saveUserData: Method,
                           val giveUserSkin: Method, val openInventory: Method, val applySkin: Method, val removeSkin: Method,
                           val getPokemonSkin: Method, val configManager: Any, val skinsGetter: Method)
    private val cached = AtomicReference<List<NativeSkin>?>(null)
    private val api: Api? by lazy { resolve() }

    fun available(): Boolean = FabricLoader.getInstance().isModLoaded("skiesskins") && api != null

    fun skins(): List<NativeSkin> {
        cached.get()?.let { return it }
        val a = api ?: return emptyList()
        val map = runCatching { a.skinsGetter.invoke(a.configManager) as? Map<*, *> }.getOrNull().orEmpty()
        val out = map.entries.asSequence().mapNotNull { (rawId, cfg) ->
            val id = rawId?.toString() ?: return@mapNotNull null
            if (!id.startsWith("svhub_")) return@mapNotNull null
            cfg ?: return@mapNotNull null
            skinView(id, cfg)
        }.sortedBy { it.id }.toList()
        cached.compareAndSet(null, out)
        return cached.get() ?: out
    }
    fun invalidate() { cached.set(null) }
    fun skin(id: String): NativeSkin? = skins().firstOrNull { it.id == id }

    fun ownedIds(player: ServerPlayer): Set<String> = inventoryObjects(player).mapNotNullTo(linkedSetOf()) { field(it, "id")?.toString() }
    fun ownedCount(player: ServerPlayer): Int = inventoryObjects(player).size

    fun grant(player: ServerPlayer, skinId: String, amount: Int = 1): Boolean {
        if (amount !in 1..100) return false
        val a = api ?: return false
        val cfg = runCatching { a.getSkin.invoke(a.instance, skinId) }.getOrNull() ?: return false
        return runCatching { a.giveUserSkin.invoke(a.instance, player, cfg, amount) as? Boolean ?: false }.getOrDefault(false)
    }

    fun openInventory(player: ServerPlayer): Boolean {
        val a = api ?: return false
        return runCatching { a.openInventory.invoke(a.instance, player); true }.getOrDefault(false)
    }

    fun openShop(player: ServerPlayer, source: String): Boolean {
        val shop = if (source == "dbz") "svhub-dbz-01" else "svhub-hunter-01"
        return runCatching {
            player.server.commands.performPrefixedCommand(player.server.createCommandSourceStack(), "skins shop $shop ${player.gameProfile.name}")
            true
        }.getOrDefault(false)
    }

    fun apply(player: ServerPlayer, skinId: String, slot: Int): ApplyResult {
        if (slot !in 1..6) return ApplyResult(false, "Party slot phải từ 1 đến 6.")
        val a = api ?: return ApplyResult(false, "SkiesSkins backend chưa sẵn sàng.")
        val cfg = runCatching { a.getSkin.invoke(a.instance, skinId) }.getOrNull() ?: return ApplyResult(false, "Skin không tồn tại trong SkiesSkins.")
        val inventory = inventoryObjects(player)
        val owned = inventory.firstOrNull { field(it, "id")?.toString() == skinId } ?: return ApplyResult(false, "Bạn chưa sở hữu skin này.")
        val pokemon = runCatching { Cobblemon.storage.getParty(player).get(slot - 1) }.getOrNull() ?: return ApplyResult(false, "Slot $slot không có Pokémon.")
        val result = runCatching { a.applySkin.invoke(a.instance, pokemon, cfg)?.toString().orEmpty() }.getOrElse { return ApplyResult(false, "Không thể áp skin: ${it.message}") }
        if (result != "SUCCESS") return ApplyResult(false, when (result) {
            "INVALID_SPECIES" -> "Skin không đúng species của Pokémon ở slot $slot."
            "ALREADY_HAS_SKIN" -> "Pokémon này đã có skin."
            "MISSING_ASPECTS" -> "Pokémon thiếu aspect bắt buộc."
            "BLACKLISTED_ASPECTS" -> "Pokémon có aspect bị blacklist."
            else -> "SkiesSkins từ chối: $result"
        })
        if (!bool(cfg, "infinite")) {
            val user = userData(player) ?: run { runCatching { a.removeSkin.invoke(a.instance, pokemon) }; return ApplyResult(false, "Không đọc được inventory SkiesSkins.") }
            val list = listField(user, "inventory") ?: run { runCatching { a.removeSkin.invoke(a.instance, pokemon) }; return ApplyResult(false, "Inventory SkiesSkins không hợp lệ.") }
            if (!list.remove(owned) || !saveUser(player, user)) {
                runCatching { a.removeSkin.invoke(a.instance, pokemon) }
                return ApplyResult(false, "Lưu SkiesSkins thất bại; đã rollback skin.")
            }
        }
        return ApplyResult(true, "Đã áp ${string(cfg, "name", skinId)} cho slot $slot.")
    }

    fun remove(player: ServerPlayer, slot: Int): ApplyResult {
        if (slot !in 1..6) return ApplyResult(false, "Party slot phải từ 1 đến 6.")
        val a = api ?: return ApplyResult(false, "SkiesSkins backend chưa sẵn sàng.")
        val pokemon = runCatching { Cobblemon.storage.getParty(player).get(slot - 1) }.getOrNull() ?: return ApplyResult(false, "Slot $slot không có Pokémon.")
        val cfg = runCatching { a.getPokemonSkin.invoke(a.instance, pokemon) }.getOrNull() ?: return ApplyResult(false, "Pokémon ở slot này chưa có skin SkiesSkins.")
        val pair = runCatching { a.removeSkin.invoke(a.instance, pokemon) }.getOrElse { return ApplyResult(false, "Không thể gỡ skin: ${it.message}") }
        val first = runCatching { pair.javaClass.getMethod("getFirst").invoke(pair)?.toString() }.getOrNull()
        if (first != "SUCCESS") return ApplyResult(false, "SkiesSkins từ chối gỡ skin: $first")
        if (!bool(cfg, "infinite")) {
            val user = userData(player) ?: return ApplyResult(false, "Đã gỡ skin nhưng không đọc được inventory để hoàn token.")
            val list = listField(user, "inventory") ?: return ApplyResult(false, "Đã gỡ skin nhưng inventory backend không hợp lệ.")
            val id = string(cfg, "id", "")
            val token = runCatching {
                val c = Class.forName("com.pokeskies.skiesskins.data.UserSkinData")
                c.getConstructor(String::class.java).newInstance(id)
            }.getOrNull() ?: return ApplyResult(false, "Đã gỡ skin nhưng không tạo được token hoàn trả.")
            list.add(token)
            if (!saveUser(player, user)) return ApplyResult(false, "Đã gỡ skin nhưng SkiesSkins không lưu được token hoàn trả; báo admin.")
        }
        return ApplyResult(true, "Đã gỡ skin khỏi slot $slot.")
    }

    private fun userData(player: ServerPlayer): Any? { val a = api ?: return null; return runCatching { a.getUserData.invoke(a.instance, player) }.getOrNull() }
    private fun saveUser(player: ServerPlayer, user: Any): Boolean { val a = api ?: return false; return runCatching { a.saveUserData.invoke(a.instance, player, user) as? Boolean ?: false }.getOrDefault(false) }
    private fun inventoryObjects(player: ServerPlayer): MutableList<Any> { val user = userData(player) ?: return mutableListOf(); return listField(user, "inventory") ?: mutableListOf() }
    @Suppress("UNCHECKED_CAST") private fun listField(obj: Any, name: String): MutableList<Any>? = runCatching { obj.javaClass.getField(name).get(obj) as? MutableList<Any> }.getOrNull()
    private fun field(obj: Any, name: String): Any? = runCatching { obj.javaClass.getField(name).get(obj) }.getOrNull()

    private fun skinView(id: String, cfg: Any): NativeSkin {
        val source = when { id.startsWith("svhub_dbz_") -> "dbz"; id.startsWith("svhub_naruto_") -> "naruto"; else -> "pokelegends" }
        val species = value(cfg, "species")?.toString().orEmpty()
        val aspects = value(cfg, "aspects")
        val apply = value(aspects, "apply") as? List<*>
        val aspect = apply?.firstOrNull()?.toString().orEmpty()
        val perfect = if (source == "dbz") 0 else 3
        return NativeSkin(id, string(cfg, "name", id), species, aspect, source, if (source == "dbz") "beastcoin" else "huntercoin", 0L, perfect, rarity(id, species), bool(cfg, "untradable", true))
    }
    private fun rarity(id: String, species: String): String {
        val s = species.substringAfter(':').lowercase()
        return when {
            s in LEGENDARY -> "legendary"
            id.contains("god") || id.contains("celestial") || id.contains("primal") -> "mythic"
            id.contains("mega") || id.contains("shadow") || id.contains("royal") -> "epic"
            id.startsWith("svhub_dbz_") || id.startsWith("svhub_naruto_") -> "epic"
            else -> "rare"
        }
    }
    private fun value(obj: Any?, name: String): Any? {
        if (obj == null) return null
        val suffix = name.replaceFirstChar { it.uppercase() }
        return runCatching { obj.javaClass.getMethod("get$suffix").invoke(obj) }.getOrElse { field(obj, name) }
    }
    private fun string(obj: Any?, name: String, fallback: String) = value(obj, name)?.toString()?.takeIf { it.isNotBlank() } ?: fallback
    private fun bool(obj: Any?, name: String, fallback: Boolean = false): Boolean = (value(obj, name) as? Boolean) ?: fallback

    private fun resolve(): Api? {
        if (!FabricLoader.getInstance().isModLoaded("skiesskins")) return null
        return runCatching {
            val c = Class.forName("com.pokeskies.skiesskins.api.SkiesSkinsAPI")
            val instance = c.getField("INSTANCE").get(null)
            val cm = Class.forName("com.pokeskies.skiesskins.config.ConfigManager")
            val cmi = cm.getField("INSTANCE").get(null)
            Api(instance,
                c.getMethod("getSkin", String::class.java), c.methods.first { it.name == "getUserData" && it.parameterCount == 1 },
                c.methods.first { it.name == "saveUserData" && it.parameterCount == 2 }, c.methods.first { it.name == "giveUserSkin" && it.parameterCount == 3 },
                c.methods.first { it.name == "openSkinInventory" && it.parameterCount == 1 }, c.methods.first { it.name == "applySkin" && it.parameterCount == 2 },
                c.methods.first { it.name == "removeSkin" && it.parameterCount == 1 }, c.methods.first { it.name == "getPokemonSkin" && it.parameterCount == 1 },
                cmi, cm.getMethod("getSKINS"))
        }.onFailure { SVHub.LOGGER.error("SkiesSkins backend bridge unavailable", it) }.getOrNull()
    }

    private val LEGENDARY = setOf("articuno","zapdos","moltres","mewtwo","mew","raikou","entei","suicune","lugia","ho_oh","celebi","regirock","regice","registeel","latias","latios","kyogre","groudon","rayquaza","jirachi","deoxys","uxie","mesprit","azelf","dialga","palkia","heatran","regigigas","giratina","cresselia","phione","manaphy","darkrai","shaymin","arceus","victini","cobalion","terrakion","virizion","tornadus","thundurus","reshiram","zekrom","landorus","kyurem","keldeo","meloetta","genesect","xerneas","yveltal","zygarde","diancie","hoopa","volcanion","type_null","silvally","tapu_koko","tapu_lele","tapu_bulu","tapu_fini","cosmog","cosmoem","solgaleo","lunala","nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana","guzzlord","necrozma","magearna","marshadow","zeraora","meltan","melmetal","zacian","zamazenta","eternatus","kubfu","urshifu","zarude","regieleki","regidrago","glastrier","spectrier","calyrex","enamorus","wo_chien","chien_pao","ting_lu","chi_yu","koraidon","miraidon","okidogi","munkidori","fezandipiti","ogerpon","terapagos","pecharunt")
}
