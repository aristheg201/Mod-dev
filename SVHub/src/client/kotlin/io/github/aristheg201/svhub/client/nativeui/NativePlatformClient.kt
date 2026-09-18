package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.network.NativeCloseC2S
import io.github.aristheg201.svhub.native.network.NativeCloseS2C
import io.github.aristheg201.svhub.native.network.NativeOpenS2C
import io.github.aristheg201.svhub.native.network.NativeStateS2C
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object NativePlatformClient {
    private val gson = Gson()
    private val closedViews = linkedSetOf<String>()
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(NativeOpenS2C.TYPE) { payload, context ->
            context.client().execute {
                val minecraft = Minecraft.getInstance()
                val current = minecraft.screen as? NativePlatformScreen
                val replacesClosed = payload.replacesViewId.isNotBlank() && payload.replacesViewId in closedViews
                val accepted = !replacesClosed && (current == null || current.viewId == payload.replacesViewId || current.viewId == payload.viewId)
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
                if (current is NativePlatformScreen && current.viewId == payload.viewId) { markClosed(payload.viewId);minecraft.setScreen(null) }
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }
    fun reset(){closedViews.clear()}
    fun markClosed(viewId:String){if(viewId.isBlank())return;closedViews+=viewId;while(closedViews.size>64){val it=closedViews.iterator();if(it.hasNext()){it.next();it.remove()}else break}}
    private fun reject(viewId:String){markClosed(viewId);ClientPlayNetworking.send(NativeCloseC2S(viewId))}
    private fun decode(raw:String):JsonObject = runCatching { gson.fromJson(raw, JsonObject::class.java) ?: JsonObject() }.getOrDefault(JsonObject())
}
