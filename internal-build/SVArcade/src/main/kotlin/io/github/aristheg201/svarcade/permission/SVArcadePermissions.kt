package io.github.aristheg201.svarcade.permission

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import java.lang.reflect.Method

object SVArcadePermissions {
    const val OPEN = "svarcade.open"
    const val EDITOR = "svarcade.editor"
    const val EDITOR_PUBLISH = "svarcade.editor.publish"
    const val EDITOR_ALL = "svarcade.editor.all"
    const val EDITOR_CREATE = "svarcade.editor.page.create"
    const val EDITOR_ASSETS = "svarcade.editor.assets"
    const val EDITOR_SETTINGS = "svarcade.editor.settings"
    const val EDITOR_HISTORY = "svarcade.editor.history"
    const val ADMIN_RELOAD = "svarcade.admin.reload"
    const val ADMIN_DEBUG = "svarcade.admin.debug"
    const val ADMIN_ROLLBACK = "svarcade.admin.rollback"

    private val fabricPermissionsCheck: Method? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching {
            val clazz = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions")
            clazz.methods.firstOrNull { method ->
                method.name == "check" && method.parameterCount == 3 &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType
            }
        }.getOrNull()
    }

    fun pageEditor(pageId: String) = "svarcade.editor.page.$pageId"

    fun has(player: ServerPlayer, node: String, fallbackLevel: Int = 0): Boolean =
        check(player.createCommandSourceStack(), node, fallbackLevel)

    fun check(source: CommandSourceStack, node: String, fallbackLevel: Int = 0): Boolean {
        val method = fabricPermissionsCheck ?: return source.hasPermission(fallbackLevel)
        return runCatching { method.invoke(null, source, node, fallbackLevel) as? Boolean }
            .getOrNull() ?: source.hasPermission(fallbackLevel)
    }
}
