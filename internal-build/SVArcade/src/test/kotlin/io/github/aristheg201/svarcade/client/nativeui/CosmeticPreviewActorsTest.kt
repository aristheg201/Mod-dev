package io.github.aristheg201.svarcade.client.nativeui

import kotlin.test.*

class CosmeticPreviewActorsTest {
    @Test fun tacticianPreviewRequiresBothRealRenderPaths() {
        val id = "svarcade:greninja"
        val arena = CosmeticPreviewActors.arena(id)
        val portrait = CosmeticPreviewActors.portrait(id)
        assertNotEquals(arena, portrait)
        assertFalse(CosmeticPreviewActors.ready(id,true,{false},{it == portrait}))
        assertFalse(CosmeticPreviewActors.ready(id,true,{it == arena},{false}))
        assertFalse(CosmeticPreviewActors.ready(id,true,{it == arena},{it == arena}), "Legacy portrait identity must not satisfy the hero portrait")
        assertTrue(CosmeticPreviewActors.ready(id,true,{it == arena},{it == portrait}))
    }
    @Test fun arenaPreviewRequiresItsEmbeddedCompanionWithoutInventingAPortrait() {
        val id = "svarcade:pikachu"
        assertFalse(CosmeticPreviewActors.ready(id,false,{false},{true}))
        assertTrue(CosmeticPreviewActors.ready(id,false,{it == CosmeticPreviewActors.arena(id)},{error("No portrait rendered")}))
    }
}
