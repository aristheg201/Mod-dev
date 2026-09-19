package io.github.aristheg201.svhub.native.game

import kotlin.test.*

class TowerDefenseDefinitionTest {
 @Test fun bundledMapTowersEnemiesWavesAndBossesAreExternalized(){val d=TowerDefenseDefinitions.bundled;assertEquals(12,d.width);assertEquals(8,d.height);assertTrue(d.path.size>20);assertEquals(6,d.towers.size);assertEquals(4,d.enemies.size);assertEquals(5,d.wave.bossEvery);assertTrue(d.bosses.isNotEmpty())}
 @Test fun sessionUsesConfiguredGeometryAndRecovers(){val seats=listOf(NativeSeat("a","A"));val s=TowerDefenseSession(seats,seed=4);assertEquals(TowerDefenseDefinitions.bundled.width,s.viewFor("a").boardWidth);val restored=NativeGameRestorer.restore("tower_defense",seats,s.sessionId,s.snapshotState());assertEquals(s.viewFor("a").board,restored.viewFor("a").board)}
}
