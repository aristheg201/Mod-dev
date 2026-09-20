package io.github.aristheg201.svhub.command

import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import io.github.aristheg201.svhub.SVHubRuntime
import io.github.aristheg201.svhub.network.SVHubNetwork
import io.github.aristheg201.svhub.native.NativeArcadeService
import io.github.aristheg201.svhub.native.NativeGameEngineRuntime
import io.github.aristheg201.svhub.native.network.NativePlatformNetwork
import io.github.aristheg201.svhub.native.NativeBotRuntime
import io.github.aristheg201.svhub.permission.SVHubPermissions
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object SVHubCommands {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("svhub")
                    .requires { SVHubPermissions.check(it, SVHubPermissions.OPEN, 0) }
                    .executes { ctx ->
                        SVHubNetwork.open(ctx.source.playerOrException, "home", false)
                        1
                    }
                    .then(
                        Commands.literal("open")
                            .executes { ctx ->
                                SVHubNetwork.open(ctx.source.playerOrException, "home", false)
                                1
                            }
                            .then(
                                Commands.argument("page", StringArgumentType.word())
                                    .suggests { _, builder ->
                                        SVHubRuntime.store.snapshot().content.pages.forEach { page ->
                                            builder.suggest(page.id)
                                            if (page.route != page.id) builder.suggest(page.route)
                                        }
                                        builder.buildFuture()
                                    }
                                    .executes { ctx ->
                                        SVHubNetwork.open(ctx.source.playerOrException, StringArgumentType.getString(ctx, "page"), false)
                                        1
                                    }
                            )
                    )
                    .then(
                        Commands.literal("editor")
                            .requires { SVHubPermissions.check(it, SVHubPermissions.EDITOR, 2) }
                            .executes { ctx ->
                                SVHubNetwork.open(ctx.source.playerOrException, "home", true)
                                1
                            }
                    )
                    .then(
                        Commands.literal("reload")
                            .requires { SVHubPermissions.check(it, SVHubPermissions.ADMIN_RELOAD, 3) }
                            .executes { ctx ->
                                val source = ctx.source
                                source.sendSuccess({ Component.literal("SVHub reload queued.") }, false)
                                SVHubRuntime.store.reloadAsync().whenComplete { result, error ->
                                    SVHubRuntime.server?.execute {
                                        if (error != null) {
                                            source.sendFailure(Component.literal(error.message ?: "SVHub reload failed"))
                                        } else {
                                            source.sendSuccess({ Component.literal(result.message) }, true)
                                            if (result.ok) {
                                                SVHubNetwork.broadcastHello()
                                                SVHubNetwork.broadcastPlayerSnapshots()
                                            }
                                        }
                                    }
                                }
                                1
                            }
                    )
                    .then(
                        Commands.literal("debug")
                            .requires { SVHubPermissions.check(it, SVHubPermissions.ADMIN_DEBUG, 3) }
                            .executes { ctx ->
                                val content = SVHubRuntime.store.snapshot().content
                                ctx.source.sendSuccess(
                                    {
                                        Component.literal(
                                            "SVHub ready=${SVHubRuntime.store.isReady()}, rev=${content.revision}, pages=${content.pages.size}, themes=${content.themes.size}, assets=${content.assets.size}, history=${SVHubRuntime.store.history(256).size}"
                                        )
                                    },
                                    false
                                )
                                1
                            }
                    )
                    .then(
                        Commands.literal("debug").requires { SVHubPermissions.check(it, SVHubPermissions.ADMIN_DEBUG, 3) }
                            .then(Commands.literal("arcade").executes { ctx -> val m=NativeArcadeService.metrics();ctx.source.sendSuccess({Component.literal("arcade=${m.activeArcade} tft=${m.activeTft} td=${m.activeTd} humans=${m.humans} bots=${m.bots} disconnected=${m.disconnectedHumans}")},false);1 }
                                .then(Commands.literal("matches").executes { ctx -> val m=NativeGameEngineRuntime.metrics();ctx.source.sendSuccess({Component.literal("sessions=${m.sessions} perSessionQueues=${m.sessionQueueDepths}")},false);1 })
                                .then(Commands.literal("async").executes { ctx -> val e=NativeGameEngineRuntime.metrics();val b=NativeBotRuntime.metrics();ctx.source.sendSuccess({Component.literal("workerQueue=${e.workerQueueDepth} avgUs=${e.averageLatencyMicros} maxUs=${e.maxLatencyMicros} staleBot=${e.staleBotResults} botQueue=${b.queueDepth} botAvgUs=${b.averageDecisionMicros} botMaxUs=${b.maxDecisionMicros}")},false);1 })
                                .then(Commands.literal("network").executes { ctx -> val n=NativePlatformNetwork.metrics();ctx.source.sendSuccess({Component.literal("subscriptions=${n.subscriptions} full=${n.fullSnapshots} deltas=${n.deltaPackets} noOp=${n.noOpFlushes} components=${n.componentsReplicated} serializedPayloadBytes=${n.serializedPayloadBytes} avgDeltaBytes=${n.averageDeltaBytes} maxDeltaBytes=${n.maximumDeltaBytes} packetsPerSecond=${n.packetsPerSecond} serializedBytesPerSecond=${n.bytesPerSecond}")},false);1 })
                                .then(Commands.literal("matchmaking").executes { ctx -> val m=NativeArcadeService.metrics();ctx.source.sendSuccess({Component.literal("queues=${m.matchmakingQueues} collectionRemainingMs=${m.matchmakingWaitMs}")},false);1 })
                            )
                    )
                    .then(
                        Commands.literal("history")
                            .requires { SVHubPermissions.check(it, SVHubPermissions.EDITOR_HISTORY, 2) }
                            .executes { ctx ->
                                val entries = SVHubRuntime.store.history(10)
                                if (entries.isEmpty()) {
                                    ctx.source.sendSuccess({ Component.literal("SVHub chưa có history.") }, false)
                                } else {
                                    entries.forEach { entry ->
                                        ctx.source.sendSuccess(
                                            { Component.literal("rev=${entry.revision} • saved=${java.time.Instant.ofEpochMilli(entry.savedAtEpochMs)}") },
                                            false
                                        )
                                    }
                                }
                                entries.size.coerceAtLeast(1)
                            }
                    )
                    .then(
                        Commands.literal("rollback")
                            .requires { SVHubPermissions.check(it, SVHubPermissions.ADMIN_ROLLBACK, 3) }
                            .then(
                                Commands.argument("revision", LongArgumentType.longArg(0L))
                                    .suggests { _, builder ->
                                        SVHubRuntime.store.history(30).forEach { builder.suggest(it.revision.toString()) }
                                        builder.buildFuture()
                                    }
                                    .executes { ctx ->
                                        val source = ctx.source
                                        val revision = LongArgumentType.getLong(ctx, "revision")
                                        source.sendSuccess({ Component.literal("SVHub rollback $revision queued.") }, false)
                                        SVHubRuntime.store.rollbackAsync(revision).whenComplete { result, error ->
                                            SVHubRuntime.server?.execute {
                                                if (error != null) {
                                                    source.sendFailure(Component.literal(error.message ?: "SVHub rollback failed"))
                                                } else {
                                                    source.sendSuccess({ Component.literal(result.message) }, true)
                                                    if (result.ok) {
                                                        SVHubNetwork.broadcastHello()
                                                        SVHubNetwork.broadcastPlayerSnapshots()
                                                    }
                                                }
                                            }
                                        }
                                        1
                                    }
                            )
                    )
            )

            dispatcher.register(
                Commands.literal("hub")
                    .requires { SVHubPermissions.check(it, SVHubPermissions.OPEN, 0) }
                    .executes { ctx ->
                        SVHubNetwork.open(ctx.source.playerOrException, "home", false)
                        1
                    }
            )
            dispatcher.register(
                Commands.literal("wiki")
                    .requires { SVHubPermissions.check(it, SVHubPermissions.OPEN, 0) }
                    .executes { ctx ->
                        SVHubNetwork.open(ctx.source.playerOrException, "home", false)
                        1
                    }
            )
        }
    }
}
