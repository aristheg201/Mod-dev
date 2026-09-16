package io.github.aristheg201.svhub.permission

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import java.lang.reflect.Method

object SVHubPermissions {
    const val OPEN = "svhub.open"
    const val EDITOR = "svhub.editor"
    const val EDITOR_PUBLISH = "svhub.editor.publish"
    const val EDITOR_ALL = "svhub.editor.all"
    const val EDITOR_CREATE = "svhub.editor.page.create"
    const val EDITOR_ASSETS = "svhub.editor.assets"
    const val EDITOR_SETTINGS = "svhub.editor.settings"
    const val EDITOR_HISTORY = "svhub.editor.history"
    const val ADMIN_RELOAD = "svhub.admin.reload"
    const val ADMIN_DEBUG = "svhub.admin.debug"
    const val ADMIN_ROLLBACK = "svhub.admin.rollback"

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

    fun pageEditor(pageId: String) = "svhub.editor.page.$pageId"

    fun has(player: ServerPlayer, node: String, fallbackLevel: Int = 0): Boolean =
        check(player.createCommandSourceStack(), node, fallbackLevel)

    fun check(source: CommandSourceStack, node: String, fallbackLevel: Int = 0): Boolean {
        val method = fabricPermissionsCheck ?: return source.hasPermission(fallbackLevel)
        return runCatching { method.invoke(null, source, node, fallbackLevel) as? Boolean }
            .getOrNull() ?: source.hasPermission(fallbackLevel)
    }
}
