package io.github.aristheg201.svhub.native.game

import io.github.aristheg201.svhub.engine.DamagePipeline
import io.github.aristheg201.svhub.engine.EffectDefinition

/** Shared route-mode selectors. Content supplies selector IDs; sessions supply immutable observations. */
object RouteTargetSelectors {
    data class Candidate<T>(val value:T,val progress:Double,val hp:Double,val maxHp:Double,val distance:Double,val tags:Set<String>,val stableId:String)
    private val comparators=mapOf<String,Comparator<Candidate<*>>>(
        "first" to compareByDescending<Candidate<*>>{it.progress}.thenBy{it.stableId},
        "last" to compareBy<Candidate<*>>{it.progress}.thenBy{it.stableId},
        "strongest" to compareByDescending<Candidate<*>>{it.maxHp}.thenBy{it.stableId},
        "weakest" to compareBy<Candidate<*>>{it.maxHp}.thenBy{it.stableId},
        "highest_hp" to compareByDescending<Candidate<*>>{it.hp}.thenBy{it.stableId},
        "lowest_hp" to compareBy<Candidate<*>>{it.hp}.thenBy{it.stableId},
        "nearest" to compareBy<Candidate<*>>{it.distance}.thenBy{it.stableId}
    )
    fun <T> select(id:String,candidates:Collection<Candidate<T>>,requiredTags:Set<String>):T? {
        val order=comparators[id]?:return null
        return candidates.asSequence().filter{requiredTags.isEmpty()||it.tags.any(requiredTags::contains)}.sortedWith(order as Comparator<Candidate<T>>).firstOrNull()?.value
    }
    fun supported()=comparators.keys
}

/** EffectDefinition interpreter shared by route-defense towers, bosses and hazards. */
object RouteEffectRuntime {
    data class Status(val id:String,val remainingTicks:Int,val intensity:Double,val stacks:Int=1)
    interface Target { var hp:Int;var shield:Int;val statuses:MutableMap<String,Status>;val resistances:List<TdResistance> }
    fun apply(nodes:List<EffectDefinition>,target:Target,element:String,baseDamage:Double):Int {var dealt=0;for(node in nodes){when(node.op()){
        "damage","true_damage"->{val authored=node.value("amount",baseDamage);val resistance=if(node.op()=="true_damage")1.0 else target.resistances.firstOrNull{it.type.equals(element,true)}?.multiplier?:1.0;var amount=DamagePipeline.scalar(authored,resistance).toInt();val absorbed=minOf(target.shield,amount);target.shield-=absorbed;amount-=absorbed;val actual=minOf(target.hp,amount);target.hp-=actual;dealt+=actual}
        "add_status"->{val id=node.text("id","");if(id.isNotBlank()){val next=Status(id,node.value("duration_ticks",5.0).toInt().coerceAtLeast(1),node.value("intensity",1.0),node.value("stacks",1.0).toInt().coerceAtLeast(1));target.statuses[id]=next}}
        "shield"->target.shield=(target.shield+node.value("amount",0.0).toInt()).coerceAtLeast(0)
        "sequence","parallel","area_effect","targeted_effect"->dealt+=apply(node.children(),target,element,baseDamage)
    }};return dealt}
}
