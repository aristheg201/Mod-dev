package io.github.aristheg201.svarcade.compat

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CobblemonPoseSanitizerTest {
    @Test
    fun `drops blank conditional animation that crashes JsonPose and preserves valid entries`() {
        val source = """
            {
              "poses": {
                "battle-hover": {
                  "poseTypes": ["HOVER"],
                  "isBattle": true,
                  "animations": [
                    "q.look('head')",
                    {"animation":"","condition":"q.is_holding_item"},
                    {"animation":"q.bedrock('test','hover')","condition":"q.is_holding_item"}
                  ],
                  "namedAnimations": {
                    "cry":"",
                    "physical":"q.bedrock_primary('test','physical')",
                    "special":"",
                    "recoil":""
                  }
                }
              }
            }
        """.trimIndent()

        val result = CobblemonPoseSanitizer.sanitizeWithStats(source)
        assertTrue(result.changed)
        assertEquals(1, result.removedAnimations)
        assertEquals(3, result.removedNamedAnimations)

        val pose = JsonParser.parseString(result.json).asJsonObject
            .getAsJsonObject("poses").getAsJsonObject("battle-hover")
        val animations = pose.getAsJsonArray("animations")
        assertEquals(2, animations.size())
        assertEquals("q.look('head')", animations[0].asString)
        assertEquals("q.bedrock('test','hover')", animations[1].asJsonObject.get("animation").asString)
        assertEquals(
            "q.bedrock_primary('test','physical')",
            pose.getAsJsonObject("namedAnimations").get("physical").asString
        )
    }

    @Test
    fun `leaves a valid poser unchanged`() {
        val source = """{"poses":{"standing":{"poseTypes":["STAND"],"animations":["q.look('head')"]}}}"""
        val result = CobblemonPoseSanitizer.sanitizeWithStats(source)
        assertFalse(result.changed)
        assertEquals(source, result.json)
    }

    @Test
    fun `normalizes legacy quirk animation and removes blank quirk hooks`() {
        val source = """
            {
              "poses": {
                "standing": {
                  "quirks": [
                    {"animation":"","condition":"true"},
                    {"animation":"q.bedrock_quirk('test','blink')","condition":"true"}
                  ]
                }
              }
            }
        """.trimIndent()
        val result = CobblemonPoseSanitizer.sanitizeWithStats(source)
        assertTrue(result.changed)
        val quirks = JsonParser.parseString(result.json).asJsonObject
            .getAsJsonObject("poses").getAsJsonObject("standing").getAsJsonArray("quirks")
        assertEquals(0, quirks[0].asJsonObject.getAsJsonArray("animations").size())
        assertEquals(
            "q.bedrock_quirk('test','blink')",
            quirks[1].asJsonObject.getAsJsonArray("animations")[0].asString
        )
    }
}
