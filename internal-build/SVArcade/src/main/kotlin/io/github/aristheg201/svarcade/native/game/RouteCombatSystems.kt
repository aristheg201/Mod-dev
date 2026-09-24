package io.github.aristheg201.svarcade.native.game

import io.github.aristheg201.svarcade.engine.DamagePipeline
import io.github.aristheg201.svarcade.engine.EffectDefinition
import io.github.aristheg201.svarcade.engine.BattleEvent
import io.github.aristheg201.svarcade.engine.TriggerDefinition

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
        "damage","true_damage"->{val authored=node.value("amount",baseDamage);val vulnerability=1.0+(target.statuses["vulnerability"]?.intensity?:0.0);val resistanceModifier=1.0+(target.statuses["resistance_modification"]?.intensity?:0.0);val resistance=if(node.op()=="true_damage")1.0 else (target.resistances.firstOrNull{it.type.equals(element,true)}?.multiplier?:1.0)*resistanceModifier;var amount=DamagePipeline.scalar(authored*vulnerability,resistance).toInt();val absorbed=minOf(target.shield,amount);target.shield-=absorbed;amount-=absorbed;val actual=minOf(target.hp,amount);target.hp-=actual;dealt+=actual}
        "add_status"->{val id=node.text("id","");if(id.isNotBlank()){val next=Status(id,node.value("duration_ticks",5.0).toInt().coerceAtLeast(1),node.value("intensity",1.0),node.value("stacks",1.0).toInt().coerceAtLeast(1));target.statuses[id]=next}}
        "shield"->target.shield=(target.shield+node.value("amount",0.0).toInt()).coerceAtLeast(0)
        "sequence","parallel","area_effect","targeted_effect"->dealt+=apply(node.children(),target,element,baseDamage)
    }};return dealt}
}


object RouteStatusRuntime {
    data class TickResult(val damage:Int,val stunned:Boolean,val speedMultiplier:Double)
    fun tick(target:RouteEffectRuntime.Target):TickResult {
        val burn=target.statuses["burn"]?.intensity?:0.0;val poison=target.statuses["poison"]?.intensity?:0.0
        val damage=(burn+poison).toInt().coerceAtLeast(0);val absorbed=minOf(target.shield,damage);target.shield-=absorbed;target.hp=(target.hp-(damage-absorbed)).coerceAtLeast(0)
        val stunned="stun" in target.statuses;val speed=(1.0-(target.statuses["slow"]?.intensity?:0.0)).coerceIn(0.0,1.0)
        target.statuses.replaceAll{_,status->status.copy(remainingTicks=status.remainingTicks-1)};target.statuses.entries.removeIf{it.value.remainingTicks<=0}
        return TickResult(damage-absorbed,stunned,speed)
    }
}

/** Generic deterministic weighted loot roller shared by route-style games. */
object RouteLootRuntime {
    data class Reward(val type:String,val id:String,val amount:Int)
    fun roll(table:TdLootTable,nextInt:(Int)->Int):List<Reward> {
        require(table.rolls > 0 && table.entries.isNotEmpty())
        val total=table.entries.sumOf{it.weight}
        require(total > 0)
        return buildList {
            repeat(table.rolls) {
                var value=nextInt(total)
                val entry=table.entries.first { candidate -> value-=candidate.weight; value<0 }
                add(Reward(entry.type,entry.id,entry.amount))
            }
        }
    }
}

/** Generic deterministic TriggerDefinition dispatcher for route-mode actors. */
class RouteTriggerRuntime {
    data class State(val counts:Map<String,Int> = emptyMap(),val lastTick:Map<String,Long> = emptyMap(),val once:Set<String> = emptySet())
    private val counts=linkedMapOf<String,Int>();private val lastTick=linkedMapOf<String,Long>();private val once=linkedSetOf<String>()
    fun snapshot()=State(counts.toMap(),lastTick.toMap(),once.toSet())
    fun restore(state:State){counts.clear();counts.putAll(state.counts);lastTick.clear();lastTick.putAll(state.lastTick);once.clear();once.addAll(state.once)}
    fun fire(triggers:List<TriggerDefinition>,event:BattleEvent,target:RouteEffectRuntime.Target,element:String,baseDamage:Double,tick:Long,roll:()->Double):Int {
        var dealt=0
        triggers.filter{it.event()==event}.sortedWith(compareByDescending<TriggerDefinition>{it.priority()}.thenBy{it.id()}).forEach{trigger->
            val count=(counts[trigger.id()]?:0)+1;counts[trigger.id()]=count
            val previous=lastTick[trigger.id()];val ready=count%trigger.every()==0&&(previous==null||tick-previous>=trigger.cooldownMs())&&(!trigger.oncePerCombat()||trigger.id() !in once)
            val conditions=trigger.conditions().all{condition->when(condition.kind()){"target_hp_below"->target.hp<=condition.value();"target_has_status"->condition.key() in target.statuses;else->false}}
            if(ready&&conditions&&roll()<=trigger.chance()){dealt+=RouteEffectRuntime.apply(trigger.effects(),target,element,baseDamage);lastTick[trigger.id()]=tick;if(trigger.oncePerCombat())once+=trigger.id()}
        }
        return dealt
    }
}
