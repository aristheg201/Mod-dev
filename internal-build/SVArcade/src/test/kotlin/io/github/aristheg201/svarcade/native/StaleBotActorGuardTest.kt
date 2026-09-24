package io.github.aristheg201.svarcade.native
import io.github.aristheg201.svarcade.native.game.NativeBotDifficulty
import kotlin.test.*
class StaleBotActorGuardTest {
 @Test fun queuedCalculationIsRejectedAfterHumanReclaimAtActorApplyTime(){val session="s";val seat="p";NativeBotRuntime.takeover(session,seat,NativeBotDifficulty.HARD);val calculatedGeneration=NativeBotRuntime.controllerGeneration(session,seat);val sourceRevision=9L;NativeBotRuntime.reclaim(session,seat);assertFalse(NativeGameEngineRuntime.botCommandCurrent(session,session,true,NativeBotRuntime.isTakeover(session,seat),calculatedGeneration,NativeBotRuntime.controllerGeneration(session,seat),sourceRevision,sourceRevision));NativeBotRuntime.forgetSession(session)}
 @Test fun revisionMutationAlsoInvalidatesQueuedCalculation(){assertFalse(NativeGameEngineRuntime.botCommandCurrent("s","s",true,true,2,2,9,10));assertTrue(NativeGameEngineRuntime.botCommandCurrent("s","s",true,true,2,2,9,9))}
}
