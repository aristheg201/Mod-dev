package io.github.aristheg201.svhub.client.nativeui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VanillaCompanionModelRendererTest {
    @Test fun namespacedAndBareMinecraftEntityIdsResolveIdentically(){
        val bare=VanillaCompanionModelRenderer.resolveEntityId("axolotl")
        val namespaced=VanillaCompanionModelRenderer.resolveEntityId("minecraft:axolotl")
        assertEquals("minecraft:axolotl",bare.toString())
        assertEquals(bare,namespaced)
    }

    @Test fun malformedDoubleNamespaceIsRejectedInsteadOfCrashing(){
        assertNull(VanillaCompanionModelRenderer.resolveEntityId("minecraft:minecraft:axolotl"))
    }
}
