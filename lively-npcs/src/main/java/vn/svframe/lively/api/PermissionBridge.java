package vn.svframe.lively.api;
import net.minecraft.server.command.ServerCommandSource;
@FunctionalInterface public interface PermissionBridge {boolean has(ServerCommandSource source,String node,int fallbackLevel);static PermissionBridge vanilla(){return(source,node,fallback)->source.hasPermissionLevel(fallback);}}
