package io.github.aristheg201.svarcade.command

import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import io.github.aristheg201.svarcade.SVArcadeRuntime
import io.github.aristheg201.svarcade.network.SVArcadeNetwork
import io.github.aristheg201.svarcade.native.NativeArcadeService
import io.github.aristheg201.svarcade.native.NativeGameEngineRuntime
import io.github.aristheg201.svarcade.native.network.NativePlatformNetwork
import io.github.aristheg201.svarcade.native.NativeBotRuntime
import io.github.aristheg201.svarcade.permission.SVArcadePermissions
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object SVArcadeCommands {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("svarcade")
                    .requires { SVArcadePermissions.check(it, SVArcadePermissions.OPEN, 0) }
                    .executes { ctx ->
                        SVArcadeNetwork.open(ctx.source.playerOrException, "home", false)
                        1
                    }
                    .then(
                        Commands.literal("open")
                            .executes { ctx ->
                                SVArcadeNetwork.open(ctx.source.playerOrException, "home", false)
                                1
                            }
                            .then(
                                Commands.argument("page", StringArgumentType.word())
                                    .suggests { _, builder ->
                                        SVArcadeRuntime.store.snapshot().content.pages.forEach { page ->
                                            builder.suggest(page.id)
                                            if (page.route != page.id) builder.suggest(page.route)
                                        }
                                        builder.buildFuture()
                                    }
                                    .executes { ctx ->
                                        SVArcadeNetwork.open(ctx.source.playerOrException, StringArgumentType.getString(ctx, "page"), false)
                                        1
                                    }
                            )
                    )
                    .then(
                        Commands.literal("editor")
                            .requires { SVArcadePermissions.check(it, SVArcadePermissions.EDITOR, 2) }
                            .executes { ctx ->
                                SVArcadeNetwork.open(ctx.source.playerOrException, "home", true)
                                1
                            }
                    )
                    .then(
                        Commands.literal("reload")
                            .requires { SVArcadePermissions.check(it, SVArcadePermissions.ADMIN_RELOAD, 3) }
                            .executes { ctx ->
                                val source = ctx.source
                                source.sendSuccess({ Component.literal("SVArcade reload queued.") }, false)
                                SVArcadeRuntime.store.reloadAsync().whenComplete { result, error ->
                                    SVArcadeRuntime.server?.execute {
                                        if (error != null) {
                                            source.sendFailure(Component.literal(error.message ?: "SVArcade reload failed"))
                                        } else {
                                            source.sendSuccess({ Component.literal(result.message) }, true)
                                            if (result.ok) {
                                                SVArcadeNetwork.broadcastHello()
                                                SVArcadeNetwork.broadcastPlayerSnapshots()
                                            }
                                        }
                                    }
                                }
                                1
                            }
                    )
                    .then(
                        Commands.literal("debug")
                            .requires { SVArcadePermissions.check(it, SVArcadePermissions.ADMIN_DEBUG, 3) }
                            .executes { ctx ->
                                val content = SVArcadeRuntime.store.snapshot().content
                                ctx.source.sendSuccess(
                                    {
                                        Component.literal(
                                            "SVArcade ready=${SVArcadeRuntime.store.isReady()}, rev=${content.revision}, pages=${content.pages.size}, themes=${content.themes.size}, assets=${content.assets.size}, history=${SVArcadeRuntime.store.history(256).size}"
                                        )
                                    },
                                    false
                                )
                                1
                            }
                    )
                    .then(
                        Commands.literal("debug").requires { SVArcadePermissions.check(it, SVArcadePermissions.ADMIN_DEBUG, 3) }
                            .then(Commands.literal("arcade").executes { ctx -> val m=NativeArcadeService.metrics();ctx.source.sendSuccess({Component.literal("arcade=${m.activeArcade} tft=${m.activeTft} td=${m.activeTd} humans=${m.humans} bots=${m.bots} disconnected=${m.disconnectedHumans}")},false);1 }
                                .then(Commands.literal("matches").executes { ctx -> val m=NativeGameEngineRuntime.metrics();ctx.source.sendSuccess({Component.literal("sessions=${m.sessions} perSessionQueues=${m.sessionQueueDepths}")},false);1 })
                                .then(Commands.literal("async").executes { ctx -> val e=NativeGameEngineRuntime.metrics();val b=NativeBotRuntime.metrics();ctx.source.sendSuccess({Component.literal("workerQueue=${e.workerQueueDepth} avgUs=${e.averageLatencyMicros} maxUs=${e.maxLatencyMicros} staleBot=${e.staleBotResults} botQueue=${b.queueDepth} botAvgUs=${b.averageDecisionMicros} botMaxUs=${b.maxDecisionMicros}")},false);1 })
                                .then(Commands.literal("network").executes { ctx -> val n=NativePlatformNetwork.metrics();ctx.source.sendSuccess({Component.literal("subscriptions=${n.subscriptions} full=${n.fullSnapshots} deltas=${n.deltaPackets} noOp=${n.noOpFlushes} components=${n.componentsReplicated} serializedPayloadBytes=${n.serializedPayloadBytes} avgDeltaBytes=${n.averageDeltaBytes} maxDeltaBytes=${n.maximumDeltaBytes} packetsPerSecond=${n.packetsPerSecond} serializedBytesPerSecond=${n.bytesPerSecond}")},false);1 })
                                .then(Commands.literal("matchmaking").executes { ctx -> val m=NativeArcadeService.metrics();ctx.source.sendSuccess({Component.literal("queues=${m.matchmakingQueues} collectionRemainingMs=${m.matchmakingWaitMs}")},false);1 })
                            )
                    )
                    .then(
                        Commands.literal("history")
                            .requires { SVArcadePermissions.check(it, SVArcadePermissions.EDITOR_HISTORY, 2) }
                            .executes { ctx ->
                                val entries = SVArcadeRuntime.store.history(10)
                                if (entries.isEmpty()) {
                                    ctx.source.sendSuccess({ Component.literal("SVArcade chưa có history.") }, false)
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
                            .requires { SVArcadePermissions.check(it, SVArcadePermissions.ADMIN_ROLLBACK, 3) }
                            .then(
                                Commands.argument("revision", LongArgumentType.longArg(0L))
                                    .suggests { _, builder ->
                                        SVArcadeRuntime.store.history(30).forEach { builder.suggest(it.revision.toString()) }
                                        builder.buildFuture()
                                    }
                                    .executes { ctx ->
                                        val source = ctx.source
                                        val revision = LongArgumentType.getLong(ctx, "revision")
                                        source.sendSuccess({ Component.literal("SVArcade rollback $revision queued.") }, false)
                                        SVArcadeRuntime.store.rollbackAsync(revision).whenComplete { result, error ->
                                            SVArcadeRuntime.server?.execute {
                                                if (error != null) {
                                                    source.sendFailure(Component.literal(error.message ?: "SVArcade rollback failed"))
                                                } else {
                                                    source.sendSuccess({ Component.literal(result.message) }, true)
                                                    if (result.ok) {
                                                        SVArcadeNetwork.broadcastHello()
                                                        SVArcadeNetwork.broadcastPlayerSnapshots()
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
                    .requires { SVArcadePermissions.check(it, SVArcadePermissions.OPEN, 0) }
                    .executes { ctx ->
                        SVArcadeNetwork.open(ctx.source.playerOrException, "home", false)
                        1
                    }
            )
            dispatcher.register(
                Commands.literal("wiki")
                    .requires { SVArcadePermissions.check(it, SVArcadePermissions.OPEN, 0) }
                    .executes { ctx ->
                        SVArcadeNetwork.open(ctx.source.playerOrException, "home", false)
                        1
                    }
            )
        }
    }
}
