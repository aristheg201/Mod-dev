package io.github.aristheg201.svhub.native

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.ThreadLocalRandom

data class NativeBanner(val id:String,val name:String,val source:String,val costTickets:Int,val pity:Int)

object NativeGachaService {
    val banners=listOf(NativeBanner("hunter","Hunter Capsule","hunter",1,30),NativeBanner("beast","Dragon Capsule","dbz",1,25))
    data class RollResult(val ok:Boolean,val message:String,val state:JsonObject?=null)

    fun roll(player:ServerPlayer,bannerId:String):RollResult{
        if(!SkiesSkinsBridge.available())return RollResult(false,"SkiesSkins backend chưa sẵn sàng.")
        val banner=banners.firstOrNull{it.id==bannerId}?:return RollResult(false,"Banner không tồn tại.")
        val profile=NativeProfileStore.get(player.uuid)?:return RollResult(false,"Profile chưa sẵn sàng.")
        if(profile.gachaTickets<banner.costTickets)return RollResult(false,"Không đủ Gacha Ticket.")
        val pool=NativeSkinCatalog.all.filter{matchesBanner(it,banner)}
        if(pool.isEmpty())return RollResult(false,"SkiesSkins chưa load pool cho banner này.")
        val oldPity=profile.pity[banner.id]?:0;val forcePremium=oldPity+1>=banner.pity;val winner=pick(pool,forcePremium)
        if(!NativeProfileStore.mutate(player.uuid){it.debit("ticket",banner.costTickets.toLong())})return RollResult(false,"Không thể khóa ticket.")
        if(!SkiesSkinsBridge.grant(player,winner.id,1)){
            NativeProfileStore.mutate(player.uuid){it.credit("ticket",banner.costTickets.toLong())}
            return RollResult(false,"SkiesSkins từ chối grant; ticket đã hoàn lại.")
        }
        NativeProfileStore.mutate(player.uuid){it.pity[banner.id]=if(winner.rarity in PREMIUM)0 else oldPity+1}
        if(winner.perfectIvs>0)NativeSkinService.grantBonusPokemon(player,winner.perfectIvs)
        val refreshed=NativeProfileStore.get(player.uuid)?:profile
        val resultState=state(player,banner.id).apply{
            add("lastRoll",JsonObject().apply{addProperty("nonce",System.nanoTime());addProperty("banner",banner.id);addProperty("winnerId",winner.id);addProperty("winnerName",winner.name);addProperty("species",winner.species);addProperty("aspect",winner.aspect);addProperty("source",winner.source);addProperty("rarity",winner.rarity);addProperty("pity",refreshed.pity[banner.id]?:0);addProperty("seed",ThreadLocalRandom.current().nextLong())})
            add("strip",buildStrip(pool,winner))
        }
        return RollResult(true,"Nhận ${winner.name}; ownership đã lưu bởi SkiesSkins.",resultState)
    }

    fun state(player: ServerPlayer, selectedBanner: String = "hunter"): JsonObject {
        val profile = NativeProfileStore.get(player.uuid) ?: NativeProfile()
        val selected = banners.firstOrNull { it.id == selectedBanner } ?: banners.first()
        val pool = NativeSkinCatalog.all.filter { matchesBanner(it, selected) }
        val owned = SkiesSkinsBridge.ownedIds(player)
        return JsonObject().apply {
            addProperty("module", "gacha");addProperty("backend", "SkiesSkins");addProperty("backendReady", SkiesSkinsBridge.available());addProperty("selected", selected.id);add("wallet", NativeSkinService.walletJson(profile))
            add("banners", JsonArray().also { array -> banners.forEach { banner -> array.add(JsonObject().apply { addProperty("id", banner.id);addProperty("name", banner.name);addProperty("costTickets", banner.costTickets);addProperty("pity", banner.pity);addProperty("currentPity", profile.pity[banner.id] ?: 0);addProperty("poolSize", NativeSkinCatalog.all.count { matchesBanner(it, banner) }) }) } })
            add("preview", JsonArray().also { array -> pool.sortedByDescending(::rarityRank).take(18).forEach { array.add(NativeSkinService.skinJson(it, owned)) } })
        }
    }
    private fun matchesBanner(s:NativeSkin,b:NativeBanner)=when(b.source){"hunter"->s.source!="dbz";else->s.source==b.source}
    private fun pick(pool:List<NativeSkin>,forcePremium:Boolean):NativeSkin{val by=pool.groupBy{normalizeRarity(it.rarity)};if(forcePremium){val p=PREMIUM.flatMap{by[it].orEmpty()};if(p.isNotEmpty())return randomOne(p)};val available=RARITY_WEIGHTS.filterKeys{!by[it].isNullOrEmpty()};val total=available.values.sum().coerceAtLeast(1);var roll=ThreadLocalRandom.current().nextInt(total);var r=available.keys.firstOrNull()?:return randomOne(pool);for((candidate,w)in available){roll-=w;if(roll<0){r=candidate;break}};return randomOne(by.getValue(r))}
    private fun <T> randomOne(list:List<T>):T=list[ThreadLocalRandom.current().nextInt(list.size)]
    private fun buildStrip(pool:List<NativeSkin>,winner:NativeSkin)=JsonArray().also{a->repeat(39){i->val s=if(i==32)winner else pick(pool,false);a.add(JsonObject().apply{addProperty("id",s.id);addProperty("name",s.name);addProperty("rarity",s.rarity)})}}
    private fun normalizeRarity(r:String)=r.lowercase().let{if(it in RARITY_WEIGHTS)it else "common"};private fun rarityRank(s:NativeSkin)=when(s.rarity){"legendary"->5;"mythic"->4;"epic"->3;"rare"->2;else->1}
    private val PREMIUM=setOf("legendary","mythic");private val RARITY_WEIGHTS=linkedMapOf("legendary" to 100,"mythic" to 400,"epic" to 1200,"rare" to 2800,"uncommon" to 3500,"common" to 2000)
}
