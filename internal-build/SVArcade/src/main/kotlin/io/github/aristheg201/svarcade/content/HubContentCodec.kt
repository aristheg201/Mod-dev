package io.github.aristheg201.svarcade.content

import com.google.gson.GsonBuilder

object HubContentCodec {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    fun encode(content: HubContent): String = gson.toJson(content)
    fun decode(json: String): HubContent = gson.fromJson(json, HubContent::class.java)
}
