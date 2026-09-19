package io.github.aristheg201.svhub.native.game

import com.google.gson.Gson

data class TdWaveRules(val baseEnemies:Int=5,val enemiesPerWave:Int=2,val bossEvery:Int=5,val clearGold:Int=15,val clearGoldPerWave:Int=2)
data class TdTowerDefinition(val id:String="",val name:String="",val species:String="",val element:String="",val cost:Int=1,val damage:Int=1,val range:Double=1.0,val cooldown:Int=1,val moveId:String="")
data class TdEnemyDefinition(val id:String="",val hp:Int=1,val speed:Double=.2,val reward:Int=1,val weakness:String="")
data class TowerDefenseDefinition(val id:String="",val width:Int=12,val height:Int=8,val startingGold:Int=120,val startingLives:Int=20,val victoryWave:Int=20,val path:List<Int> = emptyList(),val wave:TdWaveRules=TdWaveRules(),val towers:List<TdTowerDefinition> = emptyList(),val enemies:List<TdEnemyDefinition> = emptyList(),val bosses:List<String> = emptyList())
object TowerDefenseDefinitions{
 private val gson=Gson();val bundled:TowerDefenseDefinition by lazy{val d=TowerDefenseDefinitions::class.java.getResourceAsStream("/data/svhub/tower_defense/default.json")!!.reader().use{gson.fromJson(it,TowerDefenseDefinition::class.java)};require(d.width in 4..32&&d.height in 4..32&&d.path.isNotEmpty()&&d.path.all{it in 0 until d.width*d.height}&&d.towers.isNotEmpty()&&d.enemies.isNotEmpty());d}
}
