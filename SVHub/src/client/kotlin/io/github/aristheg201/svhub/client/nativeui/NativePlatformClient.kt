package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.network.NativeOpenS2C
import io.github.aristheg201.svhub.native.network.NativeStateS2C
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object NativePlatformClient {
    private val gson = Gson()
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(NativeOpenS2C.TYPE) { payload, context ->
            context.client().execute {
                val state = decode(payload.state)
                Minecraft.getInstance().setScreen(NativePlatformScreen(payload.module, state, ""))
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(NativeStateS2C.TYPE) { payload, context ->
            context.client().execute {
                val state = decode(payload.state)
                val current = Minecraft.getInstance().screen
                if (current is NativePlatformScreen && current.module == payload.module) current.applyState(state, payload.message)
                else Minecraft.getInstance().setScreen(NativePlatformScreen(payload.module, state, payload.message))
            }
        }
    }
    private fun decode(raw:String):JsonObject = runCatching { gson.fromJson(raw, JsonObject::class.java) ?: JsonObject() }.getOrDefault(JsonObject())
}
