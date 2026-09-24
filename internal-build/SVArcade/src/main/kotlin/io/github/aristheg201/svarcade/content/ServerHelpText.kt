package io.github.aristheg201.svarcade.content
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
object ServerHelpText {
    private val bundled by lazy { javaClass.getResourceAsStream("/data/svarcade/server-help.json")!!.bufferedReader().use { it.readText() } }
    private var configured: com.google.gson.JsonObject? = null
    private val defaults by lazy { JsonParser.parseString(bundled).asJsonObject }
    fun start(path: Path) { if(!Files.exists(path)) { Files.createDirectories(path.parent);Files.writeString(path,bundled) };configured=JsonParser.parseString(Files.readString(path)).asJsonObject }
    fun get(key: String): String = (configured ?: defaults).get(key)?.asString.orEmpty()
}
