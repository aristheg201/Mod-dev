package io.github.aristheg201.svhub.client.nativeui

import kotlin.test.Test
import kotlin.test.assertEquals

class TftPresentationStateTest {
    private val bounds=ArenaRegion(-10f,-10f,10f,10f)

    @Test fun transientAndMovementStatesReturnToIdle(){
        val ui=TftUiState()
        assertEquals("ROUND_START",ui.tactician(ArenaPoint(0f,0f),bounds,"ROUND_START",1_000L).state)
        assertEquals("ROUND_START",ui.tactician(ArenaPoint(0f,0f),bounds,"IDLE",1_500L).state)
        assertEquals("IDLE",ui.tactician(ArenaPoint(0f,0f),bounds,"IDLE",2_100L).state)
        assertEquals("RUN",ui.tactician(ArenaPoint(5f,0f),bounds,"IDLE",2_200L).state)
        assertEquals("WALK",ui.tactician(ArenaPoint(.2f,0f),bounds,"IDLE",4_700L).state)
    }

    @Test fun emotePickupAndTerminalStatesAreExplicit(){
        val ui=TftUiState()
        assertEquals("EMOTE",ui.tactician(ArenaPoint(0f,0f),bounds,"EMOTE",1_000L).state)
        assertEquals("EMOTE",ui.tactician(ArenaPoint(0f,0f),bounds,"IDLE",1_500L).state)
        assertEquals("PICKUP_REACTION",ui.tactician(ArenaPoint(0f,0f),bounds,"PICKUP_REACTION",2_300L).state)
        assertEquals("VICTORY",ui.tactician(ArenaPoint(0f,0f),bounds,"VICTORY",3_500L).state)
        assertEquals("VICTORY",ui.tactician(ArenaPoint(0f,0f),bounds,"IDLE",9_000L).state)
    }
}
