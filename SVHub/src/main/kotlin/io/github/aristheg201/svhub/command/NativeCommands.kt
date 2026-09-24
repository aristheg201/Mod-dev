package io.github.aristheg201.svhub.command
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import io.github.aristheg201.svhub.native.NativePlatform
import io.github.aristheg201.svhub.native.NativeProfileStore
import io.github.aristheg201.svhub.native.SkiesSkinsBridge
import io.github.aristheg201.svhub.permission.SVHubPermissions
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
object NativeCommands{
 private val currencies=listOf("arcade","ticket");private val modules=listOf("dashboard","gacha","skins","arcade","companions","wallet","store")
 fun register(){CommandRegistrationCallback.EVENT.register{dispatcher,_,_->dispatcher.register(Commands.literal("svnative").requires{SVHubPermissions.check(it,SVHubPermissions.OPEN,0)}.executes{ctx->NativePlatform.open(ctx.source.playerOrException,"dashboard");1}.then(Commands.literal("open").then(Commands.argument("module",StringArgumentType.word()).suggests{_,b->modules.forEach(b::suggest);b.buildFuture()}.executes{ctx->NativePlatform.open(ctx.source.playerOrException,StringArgumentType.getString(ctx,"module"));1})).then(Commands.literal("balance").executes{ctx->val p=ctx.source.playerOrException;val pr=NativeProfileStore.get(p.uuid);if(pr==null)ctx.source.sendFailure(Component.literal("Native profile chưa sẵn sàng."))else ctx.source.sendSuccess({Component.literal("Arcade=${pr.arcadeTokens} Ticket=${pr.gachaTickets} Skins=${SkiesSkinsBridge.ownedCount(p)} (SkiesSkins)")},false);1}).then(Commands.literal("grant").requires{SVHubPermissions.check(it,SVHubPermissions.ADMIN_DEBUG,3)}.then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("currency",StringArgumentType.word()).suggests{_,b->currencies.forEach(b::suggest);b.buildFuture()}.then(Commands.argument("amount",LongArgumentType.longArg(1L,1_000_000_000L)).executes{ctx->val target=EntityArgument.getPlayer(ctx,"player");val currency=StringArgumentType.getString(ctx,"currency").lowercase();val amount=LongArgumentType.getLong(ctx,"amount");if(currency !in currencies){ctx.source.sendFailure(Component.literal("Currency không hợp lệ."));return@executes 0};if(!NativeProfileStore.mutate(target.uuid){it.credit(currency,amount)}){ctx.source.sendFailure(Component.literal("Profile chưa load."));return@executes 0};ctx.source.sendSuccess({Component.literal("Granted $amount $currency to ${target.gameProfile.name}")},true);NativePlatform.refresh(target,"wallet");1})))))} }
}
