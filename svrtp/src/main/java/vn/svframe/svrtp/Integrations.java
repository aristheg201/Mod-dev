package vn.svframe.svrtp;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import java.math.*;

final class Integrations {
    static boolean installed(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    static boolean permission(ServerPlayer player,String node) {
        if(installed("luckperms")) {
            try {return Luck.allowed(player,node);}
            catch(RuntimeException | LinkageError e) {SVRTP.LOG.error("Permission provider unavailable player={} node={}",player.getUUID(),node,e);return false;}
        }
        return player.hasPermissions(2);
    }
    private static final class Luck {
        static boolean allowed(ServerPlayer p,String node) {
            var user=net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(p.getUUID());
            // An unavailable user/provider must not bypass permissions.
            if(user==null)return false;
            return user.getCachedData().getPermissionData(net.luckperms.api.LuckPermsProvider.get().getContextManager().getQueryOptions(user).orElse(net.luckperms.api.query.QueryOptions.defaultContextualOptions()))
                    .checkPermission(node).asBoolean();
        }
    }
    static boolean battling(ServerPlayer p) { return installed("cobblemon") && Battles.active(p); }
    private static final class Battles {
        static boolean active(ServerPlayer p) { return com.cobblemon.mod.common.battles.BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(p)!=null; }
    }
    static boolean border(ServerLevel world,double x,double z,double halfWidth) {
        if(!world.getWorldBorder().isWithinBounds(x-halfWidth,z-halfWidth) || !world.getWorldBorder().isWithinBounds(x+halfWidth,z+halfWidth))return false;
        return !installed("chunkyborder") || Borders.inside(world,x,z,halfWidth);
    }
    private static final class Borders {
        static final boolean VERSION_VALID=versions();
        static boolean versions() {
            // Both upstream JARs ship /version.properties. Their shared classloader can read
            // the other mod's file, so ChunkyBorder.getTargetVersion() may become INVALID.
            // Use Fabric's per-mod metadata instead of that colliding global resource.
            var loader=FabricLoader.getInstance();
            String chunky=loader.getModContainer("chunky").orElseThrow().getMetadata().getVersion().getFriendlyString();
            String border=loader.getModContainer("chunkyborder").orElseThrow().getMetadata().getVersion().getFriendlyString();
            boolean supported=chunky.equals("1.4.23") && border.equals("1.2.18");
            SVRTP.LOG.info("ChunkyBorder adapter versions chunky={} chunkyborder={} supported={}",chunky,border,supported);
            return supported;
        }
        static boolean inside(ServerLevel world,double x,double z,double margin) {
            if(!VERSION_VALID)throw new IllegalStateException("Unsupported ChunkyBorder adapter version; update SVRTP adapter before enabling RTP");
            var cb=org.popcraft.chunkyborder.ChunkyBorderProvider.get();
            var border=cb.getBorder(new org.popcraft.chunky.platform.FabricWorld(world).getName());
            if(border.isEmpty())return true;
            var shape=border.get().getBorder();
            return shape.isBounding(x-margin,z-margin) && shape.isBounding(x-margin,z+margin)
                    && shape.isBounding(x+margin,z-margin) && shape.isBounding(x+margin,z+margin);
        }
    }
    interface Wallet { BigInteger balance(); boolean debit(BigInteger amount); void credit(BigInteger amount); }
    static Wallet wallet(ServerPlayer p,String currency) {
        if(currency.equals("beastcoin") && installed("beconomy"))return new Beast(p);
        if(currency.equals("cobbledollars") && installed("cobbledollars"))return new Dollars(p);
        throw new IllegalStateException("Requested economy is unavailable");
    }
    private static final class Beast implements Wallet {
        final ServerPlayer player;final org.krripe.beconomy.api.EconomyAPI api;final String currency;
        Beast(ServerPlayer p) {
            player=p;if(!org.krripe.beconomy.api.BEconomy.INSTANCE.isInitialized())throw new IllegalStateException("BEconomy unavailable");
            api=org.krripe.beconomy.api.BEconomy.INSTANCE.getAPI();
            currency=api.getCurrencyList().stream().map(c->c.getCurrencyType()).filter(c->c.equalsIgnoreCase("BeastCoin")).findFirst().orElseThrow();
        }
        public BigInteger balance() { check();return api.getBalance(player.getUUID(),currency).toBigInteger(); }
        public boolean debit(BigInteger n) {check();return api.subtractBalance(player.getUUID(),new BigDecimal(n),currency);}
        public void credit(BigInteger n) {check();api.addBalance(player.getUUID(),new BigDecimal(n),currency);}
        void check() {if(!api.currencyExists(currency))throw new IllegalStateException("Missing BeastCoin currency");}
    }
    private static final class Dollars implements Wallet {
        final fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer account;
        Dollars(ServerPlayer p) {
            if(!(p instanceof fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer a))throw new IllegalStateException("CobbleDollars account unavailable");
            account=a;
        }
        public BigInteger balance() {return account.cobbleDollars$getCobbleDollars();}
        public boolean debit(BigInteger n) {var b=balance();if(b.compareTo(n)<0)return false;account.cobbleDollars$setCobbleDollars(b.subtract(n));return true;}
        public void credit(BigInteger n) {account.cobbleDollars$setCobbleDollars(balance().add(n));}
    }
}
