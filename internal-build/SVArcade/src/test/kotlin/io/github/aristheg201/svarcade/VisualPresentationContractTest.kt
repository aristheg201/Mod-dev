package io.github.aristheg201.svarcade

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Source boundary checks complement actual client smoke assertions, without loading OpenGL in JUnit. */
class VisualPresentationContractTest {
    private fun source(name:String)=Files.readString(Path.of("src/client/kotlin/io/github/aristheg201/svarcade/client/nativeui/$name.kt"))

    @Test fun `store and result smoke use production controls`() {
        val harness=source("VisualSmokeHarness")
        listOf("StoreVisualSmokeScreen","ResultVisualSmokeScreen").forEach { name ->
            val render=harness.substringAfter("private class $name").substringBefore("companion object")
            assertTrue(render.contains("NativeControlRenderer.draw"),"$name must paint real controls")
            assertFalse(Regex("control\\s*=\\s*\\{[_, ]*->\\s*}").containsMatchIn(render.substringBefore("){scene->")))
        }
        assertTrue(source("NativePlatformScreen").contains("NativeControlRenderer.draw"))
    }
    @Test fun `exclusive surfaces exit before normal TFT HUD is rendered`() {
        val renderer=source("TftGameRenderer")
        val scene=renderer.substringAfter("if(sceneOnly){").substringBefore("if(augments.isNotEmpty())")
        val augment=renderer.substringAfter("if(augments.isNotEmpty()){").substringBefore("if(phase==\"draft\"&&draft.isNotEmpty()){")
        val carousel=renderer.substringAfter("if(phase==\"draft\"&&draft.isNotEmpty()){").substringBefore("renderBoard(gui,font,resolved.board")
        listOf(scene,augment,carousel).forEach { branch ->
            assertTrue(branch.contains("return"))
            listOf("renderHud(","renderFooter(","renderTraits(","renderPlayers(","renderItemRail(").forEach { assertFalse(branch.contains(it)) }
        }
        assertTrue(renderer.contains("showUnitOverlays = showBoardLabel"))
        assertTrue(renderer.indexOf("if (!showBoardLabel) return")<renderer.indexOf("fun equippedIcons"))
        listOf("CosmeticStoreRenderer","SkinShowcaseRenderer").forEach { assertFalse(source(it).contains("TftGameRenderer")) }
        val result=source("NativePlatformScreen").substringAfter("private fun renderGameResult").substringBefore("private fun ")
        assertTrue(result.contains("sceneOnly=true"))
        assertTrue(source("NativeBoardSceneRenderer").contains("showUnitOverlays = view.get(\"finished\")?.asBoolean != true"))
    }
}
