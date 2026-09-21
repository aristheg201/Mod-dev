package io.github.aristheg201.svhub.native

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.nio.charset.StandardCharsets
import java.util.UUID

object NativeSkinService {
    data class Result(val ok:Boolean,val message:String)
    data class BonusPokemonSpec(val uuid:String,val species:String,val perfectIvs:Int)
    enum class BonusDeliveryResult { ALREADY_PRESENT, DELIVERED, RETRY }

    fun purchase(player:ServerPlayer,skinId:String):Result{val skin=NativeSkinCatalog.get(skinId)?:return Result(false,"gui.svhub.skin.not_found");val ok=SkiesSkinsBridge.openShop(player,skin.source);return Result(ok,if(ok)"gui.svhub.skin.shop_opened" else "gui.svhub.skin.backend_unavailable")}
    fun equip(player:ServerPlayer,skinId:String,slot:Int)=SkiesSkinsBridge.apply(player,skinId,slot).let{Result(it.ok,if(it.ok)"gui.svhub.skin.equipped" else "gui.svhub.skin.operation_failed")}
    fun unequip(player:ServerPlayer,slot:Int)=SkiesSkinsBridge.remove(player,slot).let{Result(it.ok,if(it.ok)"gui.svhub.skin.removed" else "gui.svhub.skin.operation_failed")}
    fun openInventory(player:ServerPlayer):Result{val ok=SkiesSkinsBridge.openInventory(player);return Result(ok,if(ok)"gui.svhub.skin.inventory_opened" else "gui.svhub.skin.backend_unavailable")}

    fun state(player:ServerPlayer,page:Int=0,source:String="all"):JsonObject{
        val owned=SkiesSkinsBridge.ownedIds(player);val (skins,pages)=NativeSkinCatalog.page(page,24,source)
        return JsonObject().apply{addProperty("module","skins");addProperty("backend","SkiesSkins");addProperty("backendReady",SkiesSkinsBridge.available());addProperty("page",page.coerceIn(0,pages-1));addProperty("pages",pages);addProperty("source",source);addProperty("ownedCount",SkiesSkinsBridge.ownedCount(player));add("wallet",walletJson(NativeProfileStore.get(player.uuid)?:NativeProfile()));add("skins",JsonArray().also{a->skins.forEach{s->a.add(skinJson(s,owned))}})}
    }

    internal fun skinJson(skin:NativeSkin,owned:Set<String>)=JsonObject().apply{addProperty("id",skin.id);addProperty("name",skin.name);addProperty("species",skin.species);addProperty("aspect",skin.aspect);addProperty("source",skin.source);addProperty("rarity",skin.rarity);addProperty("perfectIvs",skin.perfectIvs);addProperty("owned",skin.id in owned)}
    internal fun walletJson(p:NativeProfile)=JsonObject().apply{addProperty("ticket",p.gachaTickets)}

    internal fun bonusPokemonUuidForRequest(requestId:String):UUID =
        UUID.nameUUIDFromBytes(("svhub:gacha-bonus:"+requestId).toByteArray(StandardCharsets.UTF_8))

    internal fun prepareBonusPokemon(requestId:String,seed:Long,perfectIvs:Int):BonusPokemonSpec? {
        if(perfectIvs<=0)return null
        val pool=PokemonSpecies.implemented.asSequence()
            .filter{it.nationalPokedexNumber>0&&it.resourceIdentifier.namespace=="cobblemon"}
            .sortedBy{it.resourceIdentifier.toString()}
            .toList()
        if(pool.isEmpty())return null
        val mixed=seed xor (requestId.hashCode().toLong() shl 32) xor requestId.length.toLong()
        val index=Math.floorMod(mixed,pool.size.toLong()).toInt()
        return BonusPokemonSpec(
            bonusPokemonUuidForRequest(requestId).toString(),
            pool[index].resourceIdentifier.toString(),
            perfectIvs.coerceIn(1,6)
        )
    }

    internal fun ensureBonusPokemon(player:ServerPlayer,spec:BonusPokemonSpec):BonusDeliveryResult {
        val bonusUuid=runCatching{UUID.fromString(spec.uuid)}.getOrNull()?:return BonusDeliveryResult.RETRY
        val party=runCatching{Cobblemon.storage.getParty(player)}.getOrNull()?:return BonusDeliveryResult.RETRY
        val pc=runCatching{Cobblemon.storage.getPC(player)}.getOrNull()?:return BonusDeliveryResult.RETRY
        if(party.any{it.uuid==bonusUuid}||pc.any{it.uuid==bonusUuid})return BonusDeliveryResult.ALREADY_PRESENT

        val parts=spec.species.split(':',limit=2)
        val identifier=if(parts.size==2)ResourceLocation.fromNamespaceAndPath(parts[0],parts[1])
        else ResourceLocation.fromNamespaceAndPath("cobblemon",spec.species)
        val species=PokemonSpecies.getByIdentifier(identifier)?:return BonusDeliveryResult.RETRY

        val pokemon=Pokemon()
        pokemon.uuid=bonusUuid
        pokemon.species=species
        Stats.PERMANENT.toList().sortedBy{it.toString()}.take(spec.perfectIvs.coerceIn(1,6)).forEach{pokemon.setIV(it,31)}
        return if(party.add(pokemon))BonusDeliveryResult.DELIVERED else BonusDeliveryResult.RETRY
    }
}
