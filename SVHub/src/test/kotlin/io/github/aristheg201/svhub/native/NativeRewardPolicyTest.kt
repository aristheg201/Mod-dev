package io.github.aristheg201.svhub.native
import kotlin.test.*
import java.util.UUID
class NativeRewardPolicyTest{
 @Test fun `hard bot win grants full reward`(){val id=UUID.randomUUID();val a=NativeRewardPolicy.calculate(NativeRewardCompletion("s","chess","bot_hard",12_000,listOf(NativeRewardParticipant(id,NativeRewardOutcome.WIN,3))),NativeRewardRules()).single();assertTrue(a.eligible);assertEquals(50L,a.arcadeTokens)}
 @Test fun `easy bot reward is reduced`(){val id=UUID.randomUUID();val a=NativeRewardPolicy.calculate(NativeRewardCompletion("s","uno","bot_easy",12_000,listOf(NativeRewardParticipant(id,NativeRewardOutcome.WIN,3))),NativeRewardRules()).single();assertEquals(25L,a.arcadeTokens)}
 @Test fun `forfeit never grants reward`(){val id=UUID.randomUUID();val a=NativeRewardPolicy.calculate(NativeRewardCompletion("s","tft","pvp",120_000,listOf(NativeRewardParticipant(id,NativeRewardOutcome.FORFEIT,99,true))),NativeRewardRules()).single();assertFalse(a.eligible);assertEquals(0L,a.arcadeTokens);assertEquals(0,a.gachaTickets)}
 @Test fun `anti farm blocks zero participation quick finish`(){val id=UUID.randomUUID();val a=NativeRewardPolicy.calculate(NativeRewardCompletion("s","tower_defense","solo",1_000,listOf(NativeRewardParticipant(id,NativeRewardOutcome.WIN,0))),NativeRewardRules()).single();assertFalse(a.eligible)}
}
