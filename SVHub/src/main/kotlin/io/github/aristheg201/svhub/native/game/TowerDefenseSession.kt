package io.github.aristheg201.svhub.native.game

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.engine.BattleEvent
import kotlin.math.abs
import kotlin.random.Random

/** Deterministic TD simulation over a virtual route; all content and cadence comes from the pinned definition. */
class TowerDefenseSession(
    override val seats:List<NativeSeat>, seed:Long=Random.nextLong(), override val sessionId:String=NativeIds.session("td"),
    private val restoreState:JsonObject?=null, private val definition:TowerDefenseDefinition=TowerDefenseDefinitions.active()
):NativeGameSession {
    override val gameId="tower_defense"
    private val towersById=definition.towers.associateBy{it.id};private val enemiesById=(definition.enemies+definition.bosses.map{TdEnemyDefinition(it.id,it.hp,it.speed,it.reward,it.leakDamage,it.tags,it.resistances)}).associateBy{it.id}
    private val rng=NativeStatefulRandom(seed);private val towers=linkedMapOf<Int,Tower>();private val enemies=mutableListOf<Enemy>();private val spawnQueue=ArrayDeque<PendingSpawn>();private val triggerRuntime=RouteTriggerRuntime();private val settledRewards=linkedSetOf<String>();private val rewardLog=mutableListOf<String>();private val candidatePathCache=linkedMapOf<String,List<Int>>()
    private var gold=definition.startingGold;private var lives=definition.startingLives;private var wave=0;private var running=false;private var waveStartPending=false;private var spawnCooldown=0;private var revision=0L;private var result:String?=null;private var winner:String?=null;private var lastStepAt=System.currentTimeMillis();private var logicalTick=0L;private var nextEnemyId=1;private val log=ArrayDeque<String>()
    init{require(seats.isNotEmpty());TowerDefenseDefinitions.validate(definition);restoreState?.let(::restoreSnapshot)}
    override val finished get()=result!=null;override val winnerSeatId get()=winner
    override fun viewFor(viewerId:String):NativeGameView {
        val board=MutableList(definition.width*definition.height){""}
        definition.path.forEachIndexed { i,p ->
            board[p]=when(i){0->"path:start";definition.path.lastIndex->"path:goal";else->"path"}
        }
        towers.forEach { (slot,tower) ->
            val def=towersById[tower.type]
            board[slot]="tower:${tower.type}:${tower.level}:${tower.fireSerial}:${tower.targetEnemyId?:-1}:${def?.moveId.orEmpty()}:${def?.animationSemantic.orEmpty()}"
        }
        enemies.sortedBy { it.progress }.forEach { enemy ->
            val slot=definition.path[enemy.progress.toInt().coerceIn(0,definition.path.lastIndex)]
            val encoded="enemy:${enemy.id}:${enemy.kind}:${enemy.hp.coerceAtLeast(0)}:${enemy.maxHp}:${enemy.progress}"
            board[slot]=sequenceOf(board[slot],encoded).filter(String::isNotBlank).joinToString(",")
        }
        val cards=towersById.values.map { def ->
            NativeCardView(
                def.id,def.name,"${def.cost}g • DMG ${def.damage} • Range ${def.range}",def.element.lowercase(),def.cost,
                mapOf(
                    "species" to def.species,
                    "targetMode" to def.targetMode,
                    "damage" to def.damage.toString(),
                    "range" to def.range.toString(),
                    "element" to def.element,
                    "maxLevel" to (def.upgrades.maxOfOrNull { it.level } ?: 1).toString()
                )
            )
        }
        val actions=listOf(
            NativeActionView("start_wave","Bắt đầu wave ${wave+1}",enabled=!finished&&!running),
            NativeActionView("resign","Kết thúc",enabled=!finished)
        )
        val resultView=if(!finished) null else NativeGameResultPresentation(
            outcome=if(winner==viewerId)"victory" else "defeat",
            reason=if(winner==viewerId)"defense_complete" else "defense_failed",
            backdrop="tower_defense",
            stats=listOf(
                NativeResultLine("wave",wave.toString()),
                NativeResultLine("lives",lives.coerceAtLeast(0).toString()),
                NativeResultLine("gold",gold.toString()),
                NativeResultLine("towers",towers.size.toString())
            ),
            rewards=listOf(NativeResultLine("loot_count",rewardLog.size.toString())),
            progression=listOf(NativeResultLine("waves_cleared",wave.toString()))
        )
        return NativeGameView(
            sessionId,gameId,"Pokémon Tower Defense",if(finished)"finished" else if(running)"wave" else "prepare",
            "Wave $wave",
            result?:if(running)"${enemies.size+spawnQueue.size} enemies remaining" else "Prepare your defense",
            definition.width,definition.height,board,cards,actions,
            linkedMapOf(
                "gold" to "$gold","lives" to "$lives","wave" to "$wave","running" to "$running",
                "path" to definition.path.joinToString(","),"buildZones" to definition.buildZones.joinToString(","),"definition" to definition.id,
                "enemyEncoding" to "v2","towerEncoding" to "v2","rewards" to rewardLog.joinToString(",")
            ),
            log.toList().takeLast(14),revision,finished,
            winner?.let { id -> seats.firstOrNull { it.id==id }?.name },
            resultView
        )
    }

    override fun act(viewerId:String,action:String,args:Map<String,String>):NativeGameResult{if(viewerId !in seats.map{it.id})return NativeGameResult(false,message="Spectator");if(finished)return NativeGameResult(false,message="Game finished");return when(action){"start_wave"->startWave();"deploy"->deploy(args["type"],args["slot"]?.toIntOrNull());"upgrade"->upgrade(args["slot"]?.toIntOrNull());"sell"->sell(args["slot"]?.toIntOrNull());"resign"->{finish("Defense ended",null);NativeGameResult(true,true,"Ended")};else->NativeGameResult(false,message="Unknown TD action")}}
    override fun tick(nowMillis:Long):Boolean{if(!running||finished){lastStepAt=nowMillis;return false};val elapsed=(nowMillis-lastStepAt).coerceIn(0,1000);if(elapsed<definition.simulationStepMs)return false;repeat((elapsed/definition.simulationStepMs).toInt().coerceIn(1,4)){step()};lastStepAt=nowMillis;return true}
    override fun snapshotState(nowMillis:Long)=NativeGamePersistence.toJson(Snapshot(towers.toMap(),enemies.toList(),spawnQueue.toList(),gold,lives,wave,running,spawnCooldown,revision,result,winner,nextEnemyId,log.toList(),rng.state,logicalTick,triggerRuntime.snapshot(),settledRewards.toSet(),rewardLog.toList(),waveStartPending))
    private fun restoreSnapshot(state:JsonObject){val s=NativeGamePersistence.fromJson(state,Snapshot::class.java);towers.clear();s.towers.filterKeys{it in definition.buildZones}.forEach{(slot,t)->if(t.type in towersById)towers[slot]=t};enemies.clear();enemies+=s.enemies.filter{it.kind in enemiesById&&it.hp>0};spawnQueue.clear();spawnQueue+=s.spawnQueue.orEmpty().filter{it.enemy in enemiesById};gold=s.gold.coerceAtLeast(0);lives=s.lives.coerceAtLeast(0);wave=s.wave.coerceIn(0,definition.waves.size);running=s.running&&s.result==null;spawnCooldown=s.spawnCooldown.coerceAtLeast(0);revision=s.revision.coerceAtLeast(0);result=s.result;winner=s.winner;nextEnemyId=maxOf(s.nextEnemyId,(enemies.maxOfOrNull{it.id}?:0)+1);log+=s.log.takeLast(40);rng.restore(s.rngState);logicalTick=s.logicalTick;triggerRuntime.restore(s.triggerState?:RouteTriggerRuntime.State());settledRewards.clear();settledRewards.addAll(s.settledRewards.orEmpty());rewardLog.clear();rewardLog.addAll(s.rewardLog.orEmpty());waveStartPending=s.waveStartPending;lastStepAt=System.currentTimeMillis()}
    private fun startWave():NativeGameResult{
        if(running)return NativeGameResult(false,message="Wave already running")
        val w=definition.waves.getOrNull(wave)?:return NativeGameResult(false,message="No configured wave")
        wave=w.number
        spawnQueue.clear()
        w.groups.forEach{g->repeat(g.count){i->spawnQueue+=PendingSpawn(g.enemy,if(i==0)g.initialDelayTicks else g.intervalTicks)}}
        spawnCooldown=spawnQueue.firstOrNull()?.delay?:0
        running=true
        waveStartPending=true
        bump("Wave $wave bắt đầu")
        return NativeGameResult(true,true,"Wave started")
    }
    private fun deploy(type:String?,slot:Int?):NativeGameResult{val d=type?.let(towersById::get)?:return NativeGameResult(false,message="Unknown tower");val s=slot?:return NativeGameResult(false,message="Missing slot");if(s !in definition.buildZones||s in towers)return NativeGameResult(false,message="Cannot deploy there");if(gold<d.cost)return NativeGameResult(false,message="Not enough gold");gold-=d.cost;towers[s]=Tower(d.id,1,0);bump("Deploy ${d.name}");return NativeGameResult(true,true,"Deployed")}
    private fun upgrade(slot:Int?):NativeGameResult{val t=towers[slot?:-1]?:return NativeGameResult(false,message="Tower not found");val d=towersById.getValue(t.type);val next=d.upgrades.firstOrNull{it.level==t.level+1}?:return NativeGameResult(false,message="Max level");if(gold<next.cost)return NativeGameResult(false,message="Not enough gold");gold-=next.cost;t.level=next.level;bump("Upgrade ${d.name}");return NativeGameResult(true,true,"Upgraded")}
    private fun sell(slot:Int?):NativeGameResult{val t=towers.remove(slot?:-1)?:return NativeGameResult(false,message="Tower not found");val d=towersById.getValue(t.type);val invested=d.cost+d.upgrades.filter{it.level in 2..t.level}.sumOf{it.cost};val refund=(invested*definition.sellRatio).toInt();gold+=refund;bump("Sell ${d.name} +${refund}g");return NativeGameResult(true,true,"Sold")}
    private fun step(){
        logicalTick++
        var lifecycleTarget:Enemy?=null
        if(spawnQueue.isNotEmpty()){
            if(spawnCooldown<=0){
                val pending=spawnQueue.removeFirst()
                lifecycleTarget=spawn(pending.enemy)
                spawnCooldown=spawnQueue.firstOrNull()?.delay?:0
                if(waveStartPending){fireLifecycle(BattleEvent.ON_WAVE_START,lifecycleTarget!!);waveStartPending=false}
            }else spawnCooldown--
        }
        val progressIndex=enemies.filter{it.hp>0}.groupBy{it.progress.toInt()}
        towers.forEach{(slot,tower)->
            if(tower.cooldown>0){tower.cooldown--;return@forEach}
            val towerDefinition=towersById.getValue(tower.type)
            val upgrade=towerDefinition.upgrades.firstOrNull{it.level==tower.level}?:TdUpgradeLevel()
            val radius=towerDefinition.range+upgrade.rangeBonus
            val candidates=candidatePathIndices(slot,radius).asSequence()
                .flatMap{pathIndex->progressIndex[pathIndex].orEmpty().asSequence()}
                .map{enemy->RouteTargetSelectors.Candidate(enemy,enemy.progress,enemy.hp.toDouble(),enemy.maxHp.toDouble(),distance(slot,enemy.progress),enemy.tags,enemy.id.toString())}
                .filter{it.distance<=radius}.toList()
            val target=RouteTargetSelectors.select(towerDefinition.targetMode,candidates,towerDefinition.targetFilters)
            if(target!=null){
                val alive=target.hp>0
                triggerRuntime.fire(towerDefinition.triggers,BattleEvent.ON_ATTACK,target,towerDefinition.element,towerDefinition.damage*upgrade.damageMultiplier,logicalTick){rng.nextDouble()}
                RouteEffectRuntime.apply(towerDefinition.effects+upgrade.effects,target,towerDefinition.element,towerDefinition.damage*upgrade.damageMultiplier)
                triggerRuntime.fire(towerDefinition.triggers,BattleEvent.ON_HIT,target,towerDefinition.element,towerDefinition.damage*upgrade.damageMultiplier,logicalTick){rng.nextDouble()}
                fireLifecycle(BattleEvent.ON_HP_THRESHOLD,target)
                if(alive&&target.hp<=0)triggerRuntime.fire(towerDefinition.triggers,BattleEvent.ON_KILL,target,towerDefinition.element,towerDefinition.damage*upgrade.damageMultiplier,logicalTick){rng.nextDouble()}
                tower.fireSerial++
                tower.targetEnemyId=target.id
                tower.cooldown=upgrade.cooldown?:towerDefinition.cooldown
            }
        }
        enemies.forEach(::applyBossPhases)
        val dead=enemies.filter{it.hp<=0}.toList()
        dead.forEach{enemy->gold+=enemy.reward;lifecycleTarget=enemy;enemies.remove(enemy)}
        enemies.toList().forEach{enemy->
            val movement=RouteStatusRuntime.tick(enemy)
            if(!movement.stunned)enemy.progress+=enemy.speed*movement.speedMultiplier
            if(enemy.progress>=definition.path.lastIndex+.95){
                fireLifecycle(BattleEvent.ON_LEAK,enemy)
                lives-=enemy.leakDamage
                lifecycleTarget=enemy
                enemies.remove(enemy)
                if(lives<=0){finish("Defeat",null);return}
            }
        }
        if(spawnQueue.isEmpty()&&enemies.isEmpty()){
            lifecycleTarget?.let{fireLifecycle(BattleEvent.ON_WAVE_END,it)}
            running=false
            val configured=definition.waves.first{it.number==wave}
            gold+=configured.clearGold
            settleLoot("wave:$wave",configured.lootTable)
            bump("Clear wave $wave")
            if(wave>=definition.victoryWave){
                settleLoot("victory",definition.clearLootTable)
                finish("Victory",seats.first().id)
            }
        }
        revision++
    }
    private fun applyBossPhases(enemy:Enemy){
        val boss=definition.bosses.firstOrNull{it.id==enemy.kind}?:return
        boss.phases.sortedByDescending{it.hpRatio}.forEachIndexed{index,phase->
            if(enemy.hp.toDouble()/enemy.maxHp<=phase.hpRatio&&enemy.phases.add(index)){
                fireLifecycle(BattleEvent.ON_BOSS_PHASE,enemy)
                RouteEffectRuntime.apply(phase.effects,enemy,"",0.0)
                phase.adds.forEach(::spawn)
                if(phase.enrageMultiplier!=1.0){
                    fireLifecycle(BattleEvent.ON_ENRAGE,enemy)
                    enemy.speed*=phase.enrageMultiplier
                }
            }
        }
    }

    private fun fireLifecycle(event:BattleEvent,target:Enemy){
        towers.values.mapNotNull{tower->towersById[tower.type]}.forEach{towerDefinition->
            triggerRuntime.fire(towerDefinition.triggers,event,target,towerDefinition.element,towerDefinition.damage.toDouble(),logicalTick){rng.nextDouble()}
        }
    }

    private fun settleLoot(settlementId:String,tableId:String?){
        if(tableId==null||!settledRewards.add(settlementId))return
        val table=definition.lootTables.firstOrNull{it.id==tableId}?:return
        RouteLootRuntime.roll(table){bound->rng.nextInt(bound)}.forEach{reward->
            when(reward.type){"gold"->gold+=reward.amount;else->rewardLog+="${reward.type}:${reward.id}:${reward.amount}"}
        }
    }
    private fun spawn(kind:String):Enemy{val d=enemiesById.getValue(kind);return Enemy(nextEnemyId++,d.id,d.hp,d.hp,d.speed,d.reward,d.leakDamage,d.tags,d.resistances,0.0).also(enemies::add)}
    private fun candidatePathIndices(slot:Int,radius:Double):List<Int>{
        val key=slot.toString()+":"+java.lang.String.format(java.util.Locale.ROOT,"%.3f",radius)
        return candidatePathCache.getOrPut(key){
            definition.path.indices.filter{pathIndex->distance(slot,pathIndex.toDouble())<=radius+1.0}
        }
    }
    private fun distance(slot:Int,progress:Double):Double{val p=definition.path[progress.toInt().coerceIn(0,definition.path.lastIndex)];return abs(slot%definition.width-p%definition.width)+abs(slot/definition.width-p/definition.width).toDouble()}
    private fun finish(r:String,w:String?){result=r;winner=w;running=false;bump(r)};private fun bump(m:String){revision++;log+=m;while(log.size>40)log.removeFirst()}
    private data class PendingSpawn(val enemy:String,val delay:Int)
    private data class Tower(var type:String,var level:Int,var cooldown:Int,var fireSerial:Long=0,var targetEnemyId:Int?=null)
    private data class Enemy(val id:Int,val kind:String,override var hp:Int,val maxHp:Int,var speed:Double,val reward:Int,val leakDamage:Int,val tags:Set<String>,override val resistances:List<TdResistance>,var progress:Double,override var shield:Int=0,override val statuses:MutableMap<String,RouteEffectRuntime.Status> = linkedMapOf(),val phases:MutableSet<Int> = linkedSetOf()):RouteEffectRuntime.Target
    private data class Snapshot(val towers:Map<Int,Tower>,val enemies:List<Enemy>,val spawnQueue:List<PendingSpawn>?,val gold:Int,val lives:Int,val wave:Int,val running:Boolean,val spawnCooldown:Int,val revision:Long,val result:String?,val winner:String?,val nextEnemyId:Int,val log:List<String>,val rngState:Long,val logicalTick:Long=0,val triggerState:RouteTriggerRuntime.State?=RouteTriggerRuntime.State(),val settledRewards:Set<String>?=emptySet(),val rewardLog:List<String>?=emptyList(),val waveStartPending:Boolean=false)
}
