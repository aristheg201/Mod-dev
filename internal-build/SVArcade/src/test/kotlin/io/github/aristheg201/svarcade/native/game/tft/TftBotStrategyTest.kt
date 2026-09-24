package io.github.aristheg201.svarcade.native.game.tft
import io.github.aristheg201.svarcade.native.game.*
import kotlin.test.*
class TftBotStrategyTest {
 private fun view(strategy:String,cards:List<NativeCardView>)=NativeGameView("s","tft","TFT","planning",boardWidth=9,boardHeight=6,cards=cards,fields=mapOf("gold" to "20","unitCap" to "1","boardCount" to "1","bench" to "","botStrategy" to strategy,"xpNext" to "10","level" to "4","streak" to "0"))
 @Test fun productionStrategiesLoadAndReferenceTeams(){val set=TftSetRegistry.bundled("kanto_rising");assertEquals(5,set.botStrategies.size);assertTrue(set.botStrategies.all{it.preferredTeams.isNotEmpty()&&it.fallbackTeams.isNotEmpty()&&it.transitionRules.isNotEmpty()})}
 @Test fun preferredTeamChangesLegalShopOrderDeterministically(){val strategy="s~preferred~fallback~trait~caster~~~~~~";val cards=listOf(NativeCardView("shop:0","a",value=1,meta=mapOf("team" to "fallback","traits" to "x","role" to "tank")),NativeCardView("shop:1","b",value=1,meta=mapOf("team" to "preferred","traits" to "trait","role" to "caster")));val one=TftBotPlanner.plan(view(strategy,cards),NativeBotDifficulty.HARD);val two=TftBotPlanner.plan(view(strategy,cards),NativeBotDifficulty.HARD);assertEquals(one,two);assertEquals("1",one.candidates.first{it.action=="buy"}.args["index"]);assertTrue(one.candidates.none{it.action.contains("spawn")||it.action.contains("pool")||it.action=="carousel_pick"})}
 @Test fun configuredEconomyProfilesProduceDifferentInterestPolicy(){val cards=emptyList<NativeCardView>();val saver=TftBotPlanner.plan(view("s~~~~~~~interestFloor=30~~~",cards),NativeBotDifficulty.HARD);val roller=TftBotPlanner.plan(view("s~~~~~~~interestFloor=0~~~",cards),NativeBotDifficulty.HARD);assertFalse(saver.candidates.any{it.action=="refresh"});assertTrue(roller.candidates.any{it.action=="refresh"})}
 @Test fun hardBotUsesConfiguredPlayerHalfAndHpForEmergencyPolicy(){
  val bench="0~u1~x~cobblemon:pikachu~1~~0~1~caster"
  val strategy="s~~~~~~~interestFloor=30,emergencyHp=30~~~"
  val v=NativeGameView("s","tft","TFT","planning",boardWidth=7,boardHeight=8,fields=mapOf("gold" to "12","hp" to "20","unitCap" to "2","boardCount" to "0","bench" to bench,"botStrategy" to strategy,"xpNext" to "10","level" to "4","streak" to "-1","boardColumns" to "7","boardRows" to "4"))
  val plan=TftBotPlanner.plan(v,NativeBotDifficulty.HARD)
  assertTrue(plan.candidates.filter{it.action=="deploy"}.all{(it.args["slot"]?.toIntOrNull()?:99) in 0 until 28})
 }
}
