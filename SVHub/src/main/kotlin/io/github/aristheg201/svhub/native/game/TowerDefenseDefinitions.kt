package io.github.aristheg201.svhub.native.game

import com.google.gson.Gson
import io.github.aristheg201.svhub.engine.EffectDefinition
import io.github.aristheg201.svhub.engine.TriggerDefinition
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

data class TdPoint(val x:Int=0,val y:Int=0)
data class TdUpgradeLevel(val level:Int=1,val cost:Int=0,val damageMultiplier:Double=1.0,val rangeBonus:Double=0.0,val cooldown:Int?=null,val effects:List<EffectDefinition> = emptyList())
data class TdTowerDefinition(val id:String="",val name:String="",val species:String="",val element:String="",val cost:Int=1,val damage:Int=1,val range:Double=1.0,val cooldown:Int=1,val moveId:String="",val targetMode:String="first",val targetFilters:Set<String> = emptySet(),val effects:List<EffectDefinition> = emptyList(),val triggers:List<TriggerDefinition> = emptyList(),val upgrades:List<TdUpgradeLevel> = emptyList())
data class TdResistance(val type:String="",val multiplier:Double=1.0)
data class TdEnemyDefinition(val id:String="",val hp:Int=1,val speed:Double=.2,val reward:Int=1,val leakDamage:Int=1,val tags:Set<String> = emptySet(),val resistances:List<TdResistance> = emptyList())
data class TdBossPhase(val hpRatio:Double=1.0,val effects:List<EffectDefinition> = emptyList(),val adds:List<String> = emptyList(),val enrageMultiplier:Double=1.0)
data class TdBossDefinition(val id:String="",val hp:Int=1,val speed:Double=.1,val reward:Int=1,val leakDamage:Int=1,val tags:Set<String> = setOf("boss"),val resistances:List<TdResistance> = emptyList(),val phases:List<TdBossPhase> = emptyList())
data class TdSpawnGroup(val enemy:String="",val count:Int=1,val intervalTicks:Int=3,val initialDelayTicks:Int=0)
data class TdWaveDefinition(val number:Int=1,val groups:List<TdSpawnGroup> = emptyList(),val clearGold:Int=0,val lootTable:String?=null)
data class TdDifficulty(val id:String="normal",val hpMultiplier:Double=1.0,val speedMultiplier:Double=1.0,val rewardMultiplier:Double=1.0,val livesMultiplier:Double=1.0)
data class TdEndlessRules(val enabled:Boolean=false,val repeatFromWave:Int=1,val hpMultiplierPerCycle:Double=1.0)
data class TowerDefenseDefinition(
    val id:String="",val width:Int=12,val height:Int=8,val path:List<Int> = emptyList(),val navigationEdges:Map<Int,List<Int>> = emptyMap(),
    val buildZones:List<Int> = emptyList(),val spawn:Int=0,val core:Int=0,val startingGold:Int=120,val startingLives:Int=20,
    val simulationStepMs:Long=200,val sellRatio:Double=.7,val towers:List<TdTowerDefinition> = emptyList(),
    val enemies:List<TdEnemyDefinition> = emptyList(),val bosses:List<TdBossDefinition> = emptyList(),val waves:List<TdWaveDefinition> = emptyList(),
    val victoryWave:Int=20,val victoryCondition:String="clear_all_waves",val endless:TdEndlessRules=TdEndlessRules(),
    val difficulties:List<TdDifficulty> = listOf(TdDifficulty()),val defaultDifficulty:String="normal",val clearLootTable:String?=null
)

object TowerDefenseDefinitions {
    private val gson=Gson()
    val bundled:TowerDefenseDefinition by lazy {
        val stream=TowerDefenseDefinitions::class.java.getResourceAsStream("/data/svhub/tower_defense/default.json") ?: error("Missing bundled TD definition")
        validate(stream.reader().use { gson.fromJson(it,TowerDefenseDefinition::class.java) },"data/svhub/tower_defense/default.json")
    }
    private val published=AtomicReference<TowerDefenseDefinition>()
    private val loader=Executors.newSingleThreadExecutor{r->Thread(r,"SVHub-TD-Definition").apply{isDaemon=true}}
    @Volatile private var external:Path?=null
    fun start(root:Path){external=root.resolve("default.json");published.compareAndSet(null,bundled);reloadAsync()}
    fun active():TowerDefenseDefinition=published.get()?:bundled
    /** File IO, parsing, reference checks and semantic compilation happen before the tiny CAS publication. */
    fun reloadAsync():CompletableFuture<TowerDefenseDefinition> = CompletableFuture.supplyAsync({
        val path=external;val candidate=if(path!=null&&Files.isRegularFile(path)) Files.newBufferedReader(path).use{gson.fromJson(it,TowerDefenseDefinition::class.java)} else bundled
        validate(candidate,path?.toString()?:"bundled");candidate
    },loader).thenApply{candidate->published.set(candidate);candidate}
    fun validate(d:TowerDefenseDefinition,source:String="<memory>"):TowerDefenseDefinition {
        fun check(ok:Boolean,path:String,reason:String){require(ok){"$source ${d.id}.$path: $reason"}}
        check(d.id.isNotBlank(),"id","is blank");check(d.width in 4..64&&d.height in 4..64,"geometry","dimensions must be 4..64")
        val cells=0 until d.width*d.height;check(d.path.size>=2&&d.path.all{it in cells},"path","contains invalid cells")
        check(d.spawn==d.path.first()&&d.core==d.path.last(),"spawn/core","must match path endpoints")
        check(d.buildZones.isNotEmpty()&&d.buildZones.all{it in cells&&it !in d.path},"buildZones","must be valid non-path cells")
        check(d.towers.map{it.id}.distinct().size==d.towers.size&&d.towers.isNotEmpty(),"towers","IDs must be unique")
        d.towers.forEach { t->check(t.targetMode in setOf("first","last","strongest","weakest","nearest"),"towers.${t.id}.targetMode","unsupported");check(t.upgrades.map{it.level}==t.upgrades.map{it.level}.sorted().distinct(),"towers.${t.id}.upgrades","levels must be ordered and unique") }
        val enemyIds=(d.enemies.map{it.id}+d.bosses.map{it.id}).toSet();check(enemyIds.size==d.enemies.size+d.bosses.size,"enemies","IDs must be unique")
        check(d.waves.isNotEmpty()&&d.waves.map{it.number}==(1..d.waves.size).toList(),"waves","must be explicit and contiguous")
        d.waves.forEach { w->w.groups.forEachIndexed{i,g->check(g.enemy in enemyIds&&g.count>0&&g.intervalTicks>=0,"waves.${w.number}.groups[$i]","invalid enemy/count/timing")} }
        check(d.victoryWave in 1..d.waves.size,"victoryWave","must reference explicit wave");return d
    }
}
