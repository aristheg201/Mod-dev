package vn.svframe.svrtp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.*;
import net.minecraft.world.phys.Vec3;
import org.slf4j.*;
import java.nio.file.Path;
import java.math.BigInteger;
import java.util.*;

public final class SVRTP implements ModInitializer {
    static final Logger LOG=LoggerFactory.getLogger("svrtp");
    static final TicketType<UUID> TICKET=TicketType.create("svrtp",Comparator.comparing(UUID::toString),100);
    final Path configFile=FabricLoader.getInstance().getConfigDir().resolve("svrtp.json");
    Config config;
    Journal journal;
    MinecraftServer server;
    final Map<UUID,Job> jobs=new LinkedHashMap<>();
    final Map<String,ArrayDeque<BlockPos>> cache=new HashMap<>();
    final Map<UUID,Long> lastCommand=new HashMap<>();
    int ioPending;
    long completed,failed,rejectedCandidates,loadedChunks,totalLatencyMillis,peakConcurrent;
    boolean healthy=true,configValid=true;
    @Override public void onInitialize() {
        try {config=Config.load(configFile);}catch(Exception e) {LOG.error("Invalid svrtp configuration; commands are disabled",e);configValid=false;config=new Config();}
        ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;try {journal=Journal.load(s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("svrtp/journal.json"));}
            catch(Exception e) {healthy=false;LOG.error("Cannot load RTP transaction journal; refusing paid RTP",e);}
            LOG.info("SVRTP 1.0.0: server-side chest menu, pregenerated-only async loading, ChunkyBorder={}",Integrations.installed("chunkyborder"));});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{for(var job:new ArrayList<>(jobs.values()))fail(job,"Server đang dừng.","Server is stopping.");});
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerTickEvents.START_SERVER_TICK.register(s->{
            // Catch a delayed border/plugin correction before the next entity tick can
            // apply falling/void damage. This observes only our pending transfers.
            for(var j:new ArrayList<>(jobs.values()))if(j.arriving)try {verify(j);}
            catch(Exception e) {LOG.error("Arrival validation failed player={}",j.player.getUUID(),e);fail(j,"Không thể xác nhận điểm đến.","Arrival could not be verified.");}
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,s)->{
            var j=jobs.get(handler.player.getUUID());if(j!=null)fail(j,"Yêu cầu đã bị hủy.","Request cancelled.");lastCommand.remove(handler.player.getUUID());});
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            if(dispatcher.getRoot().getChild("rtp")!=null) {LOG.error("Command /rtp already exists. SVRTP will not replace it.");return;}
            var root=Commands.literal("rtp").executes(c->{open(c.getSource().getPlayerOrException(),null);return 1;});
            for(String alias:List.of("resource","nether","end"))root.then(Commands.literal(alias).executes(c->{open(c.getSource().getPlayerOrException(),alias);return 1;}));
            root.then(Commands.literal("reload").requires(c->c.getEntity()==null?c.hasPermission(2):c.getEntity() instanceof ServerPlayer p && Integrations.permission(p,config.permissions.admin)).executes(c->{
                try {var next=Config.load(configFile);for(var j:new ArrayList<>(jobs.values()))fail(j,"Cấu hình đang được tải lại.","Configuration is reloading.");config=next;configValid=true;cache.clear();c.getSource().sendSuccess(()->Component.literal(text("Đã tải lại cấu hình SVRTP.","SVRTP configuration reloaded.")),false);}
                catch(Exception e) {LOG.error("Rejected SVRTP reload; keeping previous configuration",e);c.getSource().sendFailure(Component.literal(text("Cấu hình không hợp lệ; giữ bản trước đó.","Invalid configuration; previous settings kept.")));}return 1;}));
            root.then(Commands.literal("status").requires(c->c.getEntity()==null?c.hasPermission(2):c.getEntity() instanceof ServerPlayer p && Integrations.permission(p,config.permissions.admin)).executes(c->{
                for(var e:config.destinations.entrySet()) {var d=e.getValue();boolean exists=dimension(d)!=null;c.getSource().sendSuccess(()->Component.literal(e.getKey()+": "+d.dimension+" enabled="+d.enabled+" valid="+exists),false);}
                c.getSource().sendSuccess(()->Component.literal("SVRTP completed="+completed+" failed="+failed+" candidatesRejected="+rejectedCandidates+" asyncLoads="+loadedChunks+" pending="+jobs.size()+" ioPending="+ioPending+" peak="+peakConcurrent+" meanLatencyMs="+(completed==0?0:totalLatencyMillis/completed)),false);return 1;}));
            dispatcher.register(root);
        });
    }
    String text(String vi,String en) {return config.language.equals("en")?en:vi;}
    void tell(ServerPlayer p,String vi,String en) {p.sendSystemMessage(Component.literal(text(vi,en)));}
    boolean allowed(ServerPlayer p,String alias) {
        if(!Integrations.permission(p,config.permissions.use))return false;
        return alias==null || Integrations.permission(p,switch(alias) {case "resource"->config.permissions.resource;case "nether"->config.permissions.nether;default->config.permissions.end;});
    }
    void open(ServerPlayer p,String alias) {
        if(!eligible(p))return;
        if(!allowed(p,alias)) {tell(p,"Bạn không có quyền dùng điểm đến này.","You do not have permission to use this destination.");return;}
        if(jobs.containsKey(p.getUUID())) {tell(p,"Bạn đã có yêu cầu đang xử lý.","You already have a pending request.");return;}
        Component title=alias==null?(config.resourcePackGui?Component.literal("\uE000\uE001").withStyle(s->s.withColor(0xFFFFFF).withFont(ResourceLocation.fromNamespaceAndPath("svrtp","gui"))):Component.literal("RTP")):Component.literal(text("RTP "+alias+" — Chọn tiền tệ","RTP "+alias+" — Choose currency"));
        p.openMenu(new SimpleMenuProvider((id,inv,player)->new RtpMenu(id,p,this,alias),title));
    }
    ServerLevel dimension(Config.Destination d) {
        if(server==null || d.dimension.isBlank())return null;
        var id=ResourceLocation.tryParse(d.dimension);return id==null?null:server.getLevel(ResourceKey.create(Registries.DIMENSION,id));
    }
    void request(ServerPlayer p,String alias,String currency) {
        try {requestValidated(p,alias,currency);}
        catch(RuntimeException | LinkageError e) {LOG.error("RTP request rejected before charge player={} alias={}",p.getUUID(),alias,e);tell(p,"RTP chưa khả dụng. Bạn chưa bị trừ tiền.","RTP is unavailable. No payment was taken.");}
    }
    private void requestValidated(ServerPlayer p,String alias,String currency) {
        long now=System.currentTimeMillis();
        if(now-lastCommand.getOrDefault(p.getUUID(),0L)<1000) {tell(p,"Hãy chờ một chút trước khi gửi lại.","Please wait before trying again.");return;}
        lastCommand.put(p.getUUID(),now);
        if(!healthy || !configValid || journal==null) {tell(p,"RTP đang tạm ngưng để kiểm tra dữ liệu.","RTP is paused for a data check.");return;}
        if(!allowed(p,alias)) {tell(p,"Bạn không có quyền dùng điểm đến này.","You do not have permission to use this destination.");return;}
        if(journal.receipts.containsKey(p.getUUID())) {tell(p,"Giao dịch trước cần quản trị viên đối chiếu. Bạn chưa bị trừ thêm tiền.","Your previous transaction needs administrator reconciliation. No additional payment was taken.");return;}
        var d=config.destinations.get(alias);var world=dimension(d);
        if(!d.enabled || world==null) {tell(p,"Dimension này chưa được cấu hình hoặc không khả dụng.","This dimension is unconfigured or unavailable.");return;}
        if(world.getDragonFight()!=null && world.getDragonFight().saveData().needsStateScanning()) {
            // Vanilla's first End entry can synchronously scan/generate the dragon arena.
            // RTP must not trigger that cold initialization under normal player load.
            tell(p,"The End chưa được khởi tạo. Quản trị viên cần chuẩn bị thế giới trước khi mở RTP.","The End is not initialized. An administrator must prepare it before enabling RTP.");return;
        }
        if(jobs.containsKey(p.getUUID()) || jobs.size()>=config.performance.maxConcurrentRequests || ioPending>=config.performance.maxConcurrentRequests) {
            tell(p,"Hệ thống đang xử lý yêu cầu khác. Hãy thử lại sau.","The system is busy. Try again shortly.");return;
        }
        long remaining=journal.cooldowns.getOrDefault(p.getUUID(),0L)-now;
        if(remaining>0) {tell(p,"Bạn cần chờ thêm "+((remaining+999)/1000)+" giây.","Please wait "+((remaining+999)/1000)+" seconds.");return;}
        if(!eligible(p))return;
        if(!SafeLanding.origin(p.serverLevel(),p)) {tell(p,"Hãy đứng trên nền an toàn, rộng và khô trước khi RTP.","Stand on a wide, dry, safe floor before using RTP.");return;}
        var amount=BigInteger.valueOf(currency.equals("beastcoin")?config.prices.beastCoin:config.prices.cobbleDollars);
        try {if(Integrations.wallet(p,currency).balance().compareTo(amount)<0) {tell(p,"Bạn không đủ tiền cho lựa chọn này.","You do not have enough money for this option.");return;}}
        catch(Exception e) {LOG.warn("RTP economy unavailable player={} currency={}",p.getUUID(),currency,e);tell(p,"Loại tiền này hiện chưa khả dụng.","This currency is currently unavailable.");return;}
        Job j=new Job(p,world,alias,currency,amount,d,config);jobs.put(p.getUUID(),j);peakConcurrent=Math.max(peakConcurrent,jobs.size());
        tell(p,"Đang tìm vị trí an toàn...","Searching for a safe location...");
    }
    boolean eligible(ServerPlayer p) {
        if(Integrations.battling(p)) {tell(p,"Không thể dịch chuyển khi đang chiến đấu.","You cannot teleport during a battle.");return false;}
        if(!p.isAlive() || p.isPassenger() || p.isSleeping() || p.isFallFlying() || p.isChangingDimension()) {
            tell(p,"Bạn chưa thể dịch chuyển trong trạng thái hiện tại.","You cannot teleport in your current state.");return false;}
        return true;
    }
    private void tick(MinecraftServer s) {
        int work=config.performance.maxValidationWorkPerTick;
        for(var j:new ArrayList<>(jobs.values())) {
            try {
                if(s.getPlayerList().getPlayer(j.player.getUUID())!=j.player || !j.player.isAlive()) {fail(j,"Yêu cầu đã bị hủy.","Request cancelled.");continue;}
                if(j.arriving) {verify(j);continue;}
                if(j.player.serverLevel()!=j.originWorld || Integrations.battling(j.player)) {fail(j,"Trạng thái đã thay đổi; RTP bị hủy.","Your state changed; RTP cancelled.");continue;}
                if(System.nanoTime()-j.started>j.rules.performance.requestTimeoutSeconds*1_000_000_000L) {fail(j,"Không tìm được vị trí phù hợp. Hãy thử lại sau.","No suitable location was found. Try again later.");continue;}
                if(j.waiting || work<=0)continue;
                work--;
                if(j.chunk==null) {nextCandidate(j);continue;}
                if(!j.target.dimensionType().hasCeiling()) {
                    int y=j.chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,j.x&15,j.z&15)+1;
                    if(SafeLanding.valid(j.target,j.player,j.x,y,j.z))commit(j,new BlockPos(j.x,y,j.z));else reject(j);
                } else {
                    // One bounded section per work unit; no whole-column loop on a tick.
                    int end=Math.max(j.target.getMinBuildHeight()+1,j.scanY-16);
                    boolean found=false;
                    for(int y=j.scanY;y>end;y--)if(SafeLanding.valid(j.target,j.player,j.x,y,j.z)) {commit(j,new BlockPos(j.x,y,j.z));found=true;break;}
                    if(!found) {j.scanY=end;if(end<=j.target.getMinBuildHeight()+1)reject(j);}
                }
            }catch(Exception e) {LOG.error("RTP failed player={} alias={} attempts={}",j.player.getUUID(),j.alias,j.attempts,e);fail(j,"Không thể hoàn tất RTP. Hãy thử lại sau.","RTP could not be completed. Try again later.");}
        }
    }
    private void nextCandidate(Job j) {
        if(j.attempts++>=j.rules.performance.maxAttemptsPerRequest) {fail(j,"Không tìm được vị trí phù hợp. Hãy thử lại sau.","No suitable location was found. Try again later.");return;}
        var candidates=cache.computeIfAbsent(j.alias,k->new ArrayDeque<>());
        BlockPos cached=candidates.pollFirst();
        if(cached!=null && insideRadius(j,cached.getX(),cached.getZ())) {j.x=cached.getX();j.z=cached.getZ();}
        else {
            double min=j.destination.minRadius,max=j.destination.maxRadius;
            double radius=Math.sqrt(min*min+Math.random()*(max*max-min*min)),angle=Math.random()*Math.PI*2;
            int rawX=(int)Math.floor(j.destination.centerX+Math.cos(angle)*radius),rawZ=(int)Math.floor(j.destination.centerZ+Math.sin(angle)*radius);
            j.x=(rawX&~15)+3+(int)(Math.random()*10);j.z=(rawZ&~15)+3+(int)(Math.random()*10);
        }
        if(!insideRadius(j,j.x,j.z) || !Integrations.border(j.target,j.x+.5,j.z+.5,3)) {rejectedCandidates++;return;}
        var pos=new ChunkPos(j.x>>4,j.z>>4);var loaded=j.target.getChunkSource().getChunkNow(pos.x,pos.z);
        if(loaded!=null) {hold(j,pos);j.chunk=loaded;j.scanY=Math.min(119,j.target.getMaxBuildHeight()-6);return;}
        if(ioPending>=j.rules.performance.maxConcurrentRequests) {j.attempts--;return;}
        j.waiting=true;ioPending++;
        // Disk read is asynchronous. Only existing FULL terrain may acquire a loading ticket.
        java.util.concurrent.CompletableFuture<Boolean> read;
        try {
            // A FULL center alone is insufficient: activating it may request its
            // neighbours. Verify a bounded 5x5 pregenerated neighbourhood first.
            var reads=new ArrayList<java.util.concurrent.CompletableFuture<Boolean>>();
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
                var neighbour=new ChunkPos(pos.x+dx,pos.z+dz);
                reads.add(j.target.getChunkSource().chunkMap.read(neighbour).thenApply(nbt->
                        nbt.isPresent() && nbt.get().getString("Status").equals("minecraft:full")));
            }
            read=java.util.concurrent.CompletableFuture.allOf(reads.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .thenApply(unused->reads.stream().allMatch(f->f.getNow(false)));
        }
        catch(RuntimeException e) {ioPending--;j.waiting=false;throw e;}
        read.whenComplete((pregenerated,error)->server.execute(()->{
            ioPending--;if(jobs.get(j.player.getUUID())!=j)return;
            if(error!=null) {j.waiting=false;LOG.warn("RTP chunk read rejected dimension={} chunk={}",j.target.dimension().location(),pos,error);reject(j);return;}
            if(!Boolean.TRUE.equals(pregenerated)) {j.waiting=false;reject(j);return;}
            hold(j,pos);ioPending++;
            java.util.concurrent.CompletableFuture<ChunkResult<net.minecraft.world.level.chunk.ChunkAccess>> load;
            try {load=j.target.getChunkSource().getChunkFuture(pos.x,pos.z,ChunkStatus.FULL,true);}
            catch(RuntimeException e) {ioPending--;j.waiting=false;LOG.warn("RTP async load could not start dimension={} chunk={}",j.target.dimension().location(),pos,e);reject(j);return;}
            load.whenComplete((result,loadError)->server.execute(()->{
                ioPending--;if(jobs.get(j.player.getUUID())!=j)return;
                j.waiting=false;
                if(loadError!=null || result==null || !(result.orElse(null) instanceof LevelChunk chunk)) {reject(j);return;}
                loadedChunks++;j.chunk=chunk;j.scanY=Math.min(119,j.target.getMaxBuildHeight()-6);
            }));
        }));
    }
    private boolean insideRadius(Job j,int x,int z) {
        double dx=x+.5-j.destination.centerX,dz=z+.5-j.destination.centerZ,r2=dx*dx+dz*dz;
        return r2>=((double)j.destination.minRadius*j.destination.minRadius) && r2<=((double)j.destination.maxRadius*j.destination.maxRadius);
    }
    private void hold(Job j,ChunkPos pos) {j.ticket=pos;j.target.getChunkSource().addRegionTicket(TICKET,pos,0,j.player.getUUID());}
    private void release(Job j) {if(j.ticket!=null) {j.target.getChunkSource().removeRegionTicket(TICKET,j.ticket,0,j.player.getUUID());j.ticket=null;}
        if(j.originTicket!=null) {j.originWorld.getChunkSource().removeRegionTicket(TICKET,j.originTicket,0,j.player.getUUID());j.originTicket=null;}j.chunk=null;}
    private void reject(Job j) {rejectedCandidates++;release(j);}
    private void commit(Job j,BlockPos pos)throws Exception {
        if(!eligible(j.player) || !SafeLanding.valid(j.target,j.player,pos.getX(),pos.getY(),pos.getZ()) || !SafeLanding.origin(j.originWorld,j.player)) {fail(j,"Vị trí hoặc trạng thái đã thay đổi. RTP bị hủy.","Your position or state changed. RTP cancelled.");return;}
        // Capture the latest safe source immediately before the charge, not where the search began.
        j.origin=j.player.position();j.yaw=j.player.getYRot();j.pitch=j.player.getXRot();
        j.originTicket=new ChunkPos(j.player.blockPosition());j.originWorld.getChunkSource().addRegionTicket(TICKET,j.originTicket,0,j.player.getUUID());
        j.wallet=Integrations.wallet(j.player,j.currency);
        var receipt=new Journal.Receipt(j.currency,j.amount.toString());journal.receipts.put(j.player.getUUID(),receipt);journal.save();
        if(!j.wallet.debit(j.amount)) {journal.receipts.remove(j.player.getUUID());journal.save();fail(j,"Bạn không đủ tiền cho lựa chọn này.","You do not have enough money for this option.");return;}
        j.charged=true;receipt.state="charged";journal.save();
        j.landing=pos;j.arriving=true;j.arrivalTick=server.getTickCount();
        j.player.teleportTo(j.target,pos.getX()+.5,pos.getY(),pos.getZ()+.5,j.yaw,j.pitch);
        LOG.info("RTP transfer player={} source={} target={} landing={} currency={} amount={}",j.player.getUUID(),j.originWorld.dimension().location(),j.target.dimension().location(),pos,j.currency,j.amount);
        verify(j);
    }
    private void verify(Job j)throws Exception {
        var p=j.player;
        boolean correct=p.serverLevel()==j.target && p.position().distanceToSqr(Vec3.atBottomCenterOf(j.landing))<(server.getTickCount()==j.arrivalTick?4:64)
                && SafeLanding.valid(j.target,p,p.blockPosition().getX(),p.blockPosition().getY(),p.blockPosition().getZ());
        if(!correct) {
            var source=new BlockPos((int)Math.floor(j.origin.x),(int)Math.floor(j.origin.y),(int)Math.floor(j.origin.z));
            if(SafeLanding.valid(j.originWorld,p,source.getX(),source.getY(),source.getZ()))p.teleportTo(j.originWorld,j.origin.x,j.origin.y,j.origin.z,j.yaw,j.pitch);
            else LOG.error("RTP rollback location became unsafe player={} source={}; administrator attention required",p.getUUID(),j.originWorld.dimension().location());
            fail(j,"Điểm đến bị thay đổi hoặc không an toàn. Phí RTP được hoàn nếu giao dịch thành công.","The arrival was displaced or unsafe. A successful RTP charge will be refunded.");return;
        }
        if(server.getTickCount()-j.arrivalTick<20)return;
        journal.receipts.get(p.getUUID()).state="completing";journal.save();
        long expiry=System.currentTimeMillis()+j.destination.cooldownSeconds*1000L;
        journal.cooldowns.put(p.getUUID(),expiry);journal.receipts.remove(p.getUUID());journal.save();
        completed++;totalLatencyMillis+=(System.nanoTime()-j.started)/1_000_000;
        int limit=j.rules.performance.safeLocationCacheSize;
        if(limit>0) {var q=cache.computeIfAbsent(j.alias,k->new ArrayDeque<>());q.addLast(j.landing);while(cache.values().stream().mapToInt(ArrayDeque::size).sum()>limit) {var nonempty=cache.values().stream().filter(a->!a.isEmpty()).findFirst().orElseThrow();nonempty.removeFirst();}}
        release(j);jobs.remove(p.getUUID());tell(p,"Đã dịch chuyển đến "+j.alias+".","Teleported to "+j.alias+".");
        LOG.info("RTP complete player={} target={} attempts={} latencyMs={} cooldownUntil={}",p.getUUID(),j.target.dimension().location(),j.attempts,(System.nanoTime()-j.started)/1_000_000,expiry);
    }
    private void fail(Job j,String vi,String en) {
        if(jobs.get(j.player.getUUID())!=j)return;
        if(j.charged) {
            if(j.arriving) {
                var source=BlockPos.containing(j.origin);
                if(SafeLanding.valid(j.originWorld,j.player,source.getX(),source.getY(),source.getZ()))j.player.teleportTo(j.originWorld,j.origin.x,j.origin.y,j.origin.z,j.yaw,j.pitch);
                else LOG.error("Cannot restore safe source for cancelled paid RTP player={}",j.player.getUUID());
            }
            try {
                var receipt=journal.receipts.get(j.player.getUUID());receipt.state="refunding";journal.save();
                var before=j.wallet.balance();j.wallet.credit(j.amount);
                if(!j.wallet.balance().equals(before.add(j.amount)))throw new IllegalStateException("Economy did not confirm exact refund");
                receipt.state="refunded";journal.save();journal.receipts.remove(j.player.getUUID());journal.save();
                tell(j.player,"Đã hoàn phí RTP.","Your RTP fee has been refunded.");
            }catch(Exception e) {healthy=false;LOG.error("RTP refund needs reconciliation player={} amount={} currency={}",j.player.getUUID(),j.amount,j.currency,e);tell(j.player,"Chưa xác nhận được hoàn phí. Quản trị viên cần đối chiếu giao dịch.","Refund could not be confirmed. An administrator must reconcile the transaction.");}
        }
        failed++;release(j);jobs.remove(j.player.getUUID());tell(j.player,vi,en);
        LOG.info("RTP rejected player={} alias={} attempts={} charged={} reason={}",j.player.getUUID(),j.alias,j.attempts,j.charged,en);
    }
    private static final class Job {
        final ServerPlayer player;final ServerLevel target,originWorld;final String alias,currency;final BigInteger amount;
        final Config.Destination destination;final Config rules;final long started=System.nanoTime();
        int attempts,x,z,scanY,arrivalTick;boolean waiting,charged,arriving;ChunkPos ticket,originTicket;LevelChunk chunk;BlockPos landing;
        Vec3 origin;float yaw,pitch;Integrations.Wallet wallet;
        Job(ServerPlayer p,ServerLevel world,String alias,String currency,BigInteger amount,Config.Destination d,Config c) {
            player=p;target=world;originWorld=p.serverLevel();this.alias=alias;this.currency=currency;this.amount=amount;destination=d;rules=c;
        }
    }
}
