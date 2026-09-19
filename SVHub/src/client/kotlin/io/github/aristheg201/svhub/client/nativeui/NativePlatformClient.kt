package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.network.NativeCloseC2S
import io.github.aristheg201.svhub.native.network.NativeCloseS2C
import io.github.aristheg201.svhub.native.network.NativeOpenS2C
import io.github.aristheg201.svhub.native.network.NativeStateS2C
import io.github.aristheg201.svhub.native.network.NativeTftPreviewS2C
import io.github.aristheg201.svhub.native.network.NativeTftPreviewResultC2S
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.native.game.tft.PokemonAnimationSemantic
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft

object NativePlatformClient {
    private val gson = Gson()
    private val closedViews = linkedSetOf<String>()
    private data class PreviewHud(val view:PokemonView,val instanceId:String,val message:String,val expiresAt:Long)
    @Volatile private var previewHud:PreviewHud?=null
    fun register() {
        HudRenderCallback.EVENT.register { gui, _ ->
            val preview=previewHud ?: return@register
            val now=System.currentTimeMillis()
            if(now>=preview.expiresAt){previewHud=null;return@register}
            val minecraft=Minecraft.getInstance()
            val x=minecraft.window.guiScaledWidth-86
            val y=96
            gui.fill(x-68,y-72,x+68,y+48,0xCC091215.toInt())
            PokemonModelRenderer.renderScene(gui,preview.view,preview.instanceId,x,y,112,175f,.9f,22f,1400.0,false)
            gui.drawCenteredString(minecraft.font,preview.view.displayName,x,y-66,0xFFF2F6F4.toInt())
            gui.drawCenteredString(minecraft.font,minecraft.font.plainSubstrByWidth(preview.message,128),x,y+31,0xFF91A6A1.toInt())
        }
        ClientPlayNetworking.registerGlobalReceiver(NativeOpenS2C.TYPE) { payload, context ->
            context.client().execute {
                val minecraft = Minecraft.getInstance()
                val current = minecraft.screen as? NativePlatformScreen
                // A freshly-created server view is allowed to replace a view the player just
                // closed. Reject the closed view itself, not every future view that references it.
                // The old check used replacesViewId and made a fast close -> reopen race reject
                // the legitimate new Hub view forever from the user's perspective.
                val explicitlyClosed = payload.viewId in closedViews
                val accepted = !explicitlyClosed && (current == null || current.viewId == payload.replacesViewId || current.viewId == payload.viewId)
                if (!accepted) { reject(payload.viewId); return@execute }
                if (payload.replacesViewId.isNotBlank()) closedViews.remove(payload.replacesViewId)
                closedViews.remove(payload.viewId)
                current?.prepareForServerReplacement()
                minecraft.setScreen(NativePlatformScreen(payload.module, decode(payload.state), "", payload.viewId))
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(NativeStateS2C.TYPE) { payload, context ->
            context.client().execute {
                val current = Minecraft.getInstance().screen
                if (current is NativePlatformScreen && current.viewId == payload.viewId && current.module == payload.module) current.applyState(decode(payload.state), payload.message)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(NativeCloseS2C.TYPE) { payload, context ->
            context.client().execute {
                val minecraft = Minecraft.getInstance();val current = minecraft.screen
                if (current is NativePlatformScreen && current.viewId == payload.viewId) {
                    markClosed(payload.viewId)
                    current.prepareForServerReplacement()
                    minecraft.setScreen(null)
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(NativeTftPreviewS2C.TYPE) { payload, context ->
            context.client().execute {
                val semantic=runCatching{PokemonAnimationSemantic.valueOf(payload.semantic.uppercase())}.getOrNull()
                val view=PokemonView(
                    key="svhub-preview:${payload.requestId}",
                    route="",
                    speciesId=payload.species,
                    aspects=payload.aspects.split(',').filter(String::isNotBlank).toSet(),
                    displayName=payload.label,
                    dexNumber=0,
                    fakemon=payload.species.substringBefore(':')!="cobblemon"
                )
                val message=if(semantic==null){
                    "${payload.label}: REJECTED semantic=${payload.semantic}"
                }else{
                    val diagnostics=PokemonModelRenderer.diagnostics(view)
                    val preview=PokemonModelRenderer.previewAnimation(view,"diagnostic:${payload.requestId}",semantic)
                    previewHud=PreviewHud(view,"diagnostic:${payload.requestId}","${semantic.name} → ${preview.selected?:"poser-default"}",System.currentTimeMillis()+4_000L)
                    buildString{
                        append(payload.label).append(": outcome=").append(preview.outcome)
                        append(" poser=").append(diagnostics.poser?:"UNOBSERVABLE")
                        append(" texture=").append(diagnostics.texture?:"UNOBSERVABLE")
                        append(" layers=").append(diagnostics.layers)
                        append(" labels=").append(preview.available)
                        append(" selected=").append(preview.selected?:"poser-default")
                        diagnostics.reason?.let{append(" reason=").append(it)}
                    }
                }
                ClientPlayNetworking.send(NativeTftPreviewResultC2S(payload.requestId,message))
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }
    fun reset(){closedViews.clear();previewHud=null;ArenaPresentationRuntime.stopAll()}
    fun markClosed(viewId:String){if(viewId.isBlank())return;closedViews+=viewId;while(closedViews.size>64){val it=closedViews.iterator();if(it.hasNext()){it.next();it.remove()}else break}}
    private fun reject(viewId:String){markClosed(viewId);ClientPlayNetworking.send(NativeCloseC2S(viewId))}
    private fun decode(raw:String):JsonObject = runCatching { gson.fromJson(raw, JsonObject::class.java) ?: JsonObject() }.getOrDefault(JsonObject())
}
