package io.github.aristheg201.svhub.native

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object NativeSkinService {
    data class Result(val ok:Boolean,val message:String)
    fun purchase(player:ServerPlayer,skinId:String):Result{val skin=NativeSkinCatalog.get(skinId)?:return Result(false,"Skin không tồn tại trong SkiesSkins.");val ok=SkiesSkinsBridge.openShop(player,skin.source);return Result(ok,if(ok)"Đã mở shop SkiesSkins tương ứng." else "Không thể mở SkiesSkins shop.")}
    fun equip(player:ServerPlayer,skinId:String,slot:Int)=SkiesSkinsBridge.apply(player,skinId,slot).let{Result(it.ok,it.message)}
    fun unequip(player:ServerPlayer,slot:Int)=SkiesSkinsBridge.remove(player,slot).let{Result(it.ok,it.message)}
    fun openInventory(player:ServerPlayer):Result{val ok=SkiesSkinsBridge.openInventory(player);return Result(ok,if(ok)"Đã mở tủ SkiesSkins." else "Không thể mở tủ SkiesSkins.")}
    fun state(player:ServerPlayer,page:Int=0,source:String="all"):JsonObject{
        val owned=SkiesSkinsBridge.ownedIds(player);val (skins,pages)=NativeSkinCatalog.page(page,24,source)
        return JsonObject().apply{addProperty("module","skins");addProperty("backend","SkiesSkins");addProperty("backendReady",SkiesSkinsBridge.available());addProperty("page",page.coerceIn(0,pages-1));addProperty("pages",pages);addProperty("source",source);addProperty("ownedCount",SkiesSkinsBridge.ownedCount(player));add("wallet",walletJson(NativeProfileStore.get(player.uuid)?:NativeProfile()));add("skins",JsonArray().also{a->skins.forEach{s->a.add(skinJson(s,owned))}})}
    }
    internal fun skinJson(skin:NativeSkin,owned:Set<String>)=JsonObject().apply{addProperty("id",skin.id);addProperty("name",skin.name);addProperty("species",skin.species);addProperty("aspect",skin.aspect);addProperty("source",skin.source);addProperty("rarity",skin.rarity);addProperty("perfectIvs",skin.perfectIvs);addProperty("owned",skin.id in owned)}
    internal fun walletJson(p:NativeProfile)=JsonObject().apply{addProperty("arcade",p.arcadeTokens);addProperty("ticket",p.gachaTickets);addProperty("skinEconomy","SkiesSkins/BECONOMY")}
    internal fun grantBonusPokemon(player:ServerPlayer,perfectIvs:Int){if(perfectIvs<=0)return;val pool=PokemonSpecies.implemented.asSequence().filter{it.nationalPokedexNumber>0&&it.resourceIdentifier.namespace=="cobblemon"}.toList();val species=pool.randomOrNull()?:return;val cmd="givepokemonother ${player.gameProfile.name} ${species.resourceIdentifier} min_perfect_ivs=$perfectIvs";val ok=runCatching{player.server.commands.performPrefixedCommand(player.server.createCommandSourceStack(),cmd);true}.getOrDefault(false);if(!ok)player.sendSystemMessage(Component.literal("Skin đã grant qua SkiesSkins, nhưng Pokémon thưởng ${perfectIvs}IV chưa phát được; báo admin."))}
}
