package io.github.aristheg201.svhub.native.game

import kotlin.test.*

class TowerDefenseDefinitionTest {
 @Test fun bundledMapTowersEnemiesExplicitWavesAndBossesAreExternalized(){val d=TowerDefenseDefinitions.bundled;assertEquals(12,d.width);assertEquals(8,d.height);assertTrue(d.path.size>20);assertEquals(6,d.towers.size);assertEquals(4,d.enemies.size);assertEquals(20,d.waves.size);assertTrue(d.waves[4].groups.any{it.enemy in d.bosses.map(TdBossDefinition::id)});assertTrue(d.bosses.isNotEmpty())}
 @Test fun nonDefaultDefinitionControlsGeometryUpgradesTimingDamageAndRecovery(){
  val tower=TdTowerDefinition("odd","Odd","cobblemon:ditto","Arcane",3,7,9.0,1,"transform","strongest",setOf("boss","ground"),upgrades=listOf(TdUpgradeLevel(1),TdUpgradeLevel(2,17,3.0,4.0,1)))
  val enemy=TdEnemyDefinition("tiny",11,.01,2,7,setOf("ground"),listOf(TdResistance("Arcane",.25)))
  val boss=TdBossDefinition("odd_boss",31,.01,19,9,phases=listOf(TdBossPhase(.4,enrageMultiplier=2.0)))
  val d=TowerDefenseDefinition("test:odd",5,4,listOf(0,1,2,3,4),mapOf(0 to listOf(1),1 to listOf(2),2 to listOf(3),3 to listOf(4)),listOf(5,6,7),0,4,23,13,10,.42,listOf(tower),listOf(enemy),listOf(boss),listOf(TdWaveDefinition(1,listOf(TdSpawnGroup("odd_boss",1,9,4)),37)),1)
  TowerDefenseDefinitions.validate(d,"test.json");val seats=listOf(NativeSeat("a","A"));val s=TowerDefenseSession(seats,4,"td-test",definition=d)
  assertEquals(5,s.viewFor("a").boardWidth);assertTrue(s.act("a","deploy",mapOf("type" to "odd","slot" to "5")).accepted);assertTrue(s.act("a","start_wave",emptyMap()).accepted)
  val snap=s.snapshotState();val restored=TowerDefenseSession(seats,4,"td-test",snap,d);assertEquals(s.viewFor("a").board,restored.viewFor("a").board);assertEquals("test:odd",restored.viewFor("a").fields["definition"])
 }
 @Test fun validatorRejectsImplicitOrUnknownWaveContentWithPath(){val bad=TowerDefenseDefinition(id="x",width=4,height=4,path=listOf(0,1),buildZones=listOf(2),spawn=0,core=1,towers=listOf(TdTowerDefinition(id="t",upgrades=listOf(TdUpgradeLevel(1)))),enemies=listOf(TdEnemyDefinition(id="e")),waves=listOf(TdWaveDefinition(1,listOf(TdSpawnGroup("missing"))))) ;val e=assertFailsWith<IllegalArgumentException>{TowerDefenseDefinitions.validate(bad,"bad.json")};assertContains(e.message!!,"bad.json x.waves.1.groups[0]")}
}
