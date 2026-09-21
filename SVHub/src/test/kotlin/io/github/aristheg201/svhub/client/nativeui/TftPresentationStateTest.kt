package io.github.aristheg201.svhub.client.nativeui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TftPresentationStateTest {
    private val bounds=ArenaRegion(-10f,-10f,10f,10f)

    @Test fun transientAndMovementStatesReturnToIdle(){
        val ui=TftUiState()
        val t=System.currentTimeMillis()
        assertEquals("ROUND_START",ui.tactician(ArenaPoint(0f,0f),bounds,"ROUND_START",t).state)
        assertEquals("ROUND_START",ui.tactician(ArenaPoint(0f,0f),bounds,"IDLE",t+500).state)
        assertEquals("IDLE",ui.tactician(ArenaPoint(0f,0f),bounds,"IDLE",t+1_000).state)
        val running=ui.tactician(ArenaPoint(5f,0f),bounds,"IDLE",t+1_100)
        assertEquals("RUN",running.state)
        assertEquals("WALK",ui.tactician(ArenaPoint(running.point.x+.1f,0f),bounds,"IDLE",t+1_150).state)
    }

    @Test fun emotePickupAndTerminalStatesAreExplicit(){
        val ui=TftUiState()
        val t=System.currentTimeMillis()
        assertEquals("EMOTE",ui.tactician(ArenaPoint(0f,0f),bounds,"EMOTE",t).state)
        assertEquals("EMOTE",ui.tactician(ArenaPoint(0f,0f),bounds,"IDLE",t+500).state)
        assertEquals("PICKUP_REACTION",ui.tactician(ArenaPoint(0f,0f),bounds,"PICKUP_REACTION",t+1_300).state)
        assertEquals("VICTORY",ui.tactician(ArenaPoint(0f,0f),bounds,"VICTORY",t+2_300).state)
        assertEquals("VICTORY",ui.tactician(ArenaPoint(0f,0f),bounds,"IDLE",t+9_000).state)
    }

    @Test fun itemDragOwnsIdentityUntilReleaseOrStateChange(){
        val ui=TftUiState()
        ui.selectedOrigin="bench"
        ui.selectedIndex=2
        ui.beginItemDrag(3,"full:rapid_fire")
        assertTrue(ui.isItemDragging())
        assertEquals(3,ui.selectedItem)
        assertEquals("full:rapid_fire",ui.selectedItemIdentity)
        assertNull(ui.selectedOrigin)
        assertNull(ui.selectedIndex)
        ui.clearItem()
        assertTrue(!ui.isItemDragging())
        assertNull(ui.selectedItem)
        assertNull(ui.selectedItemIdentity)
    }

    @Test fun restoredItemEventEstablishesBaselineAndOnlyNewSerialPlays(){
        val ui=TftUiState()
        assertNull(ui.observeItemEvent(7,"combine:a+b->c:u1"))
        assertNull(ui.observeItemEvent(7,"combine:a+b->c:u1"))
        val next=assertNotNull(ui.observeItemEvent(8,"combine:a+b->c:u1"))
        assertEquals(8,next.serial)
        assertNull(ui.observeItemEvent(8,"combine:a+b->c:u1"))
    }
}
