package dev.svextract.mission;

import dev.svextract.SvExtract;
import dev.svextract.Content;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import dev.svextract.compat.Integrations;
import dev.svextract.compat.CobblemonAdapter;
import dev.svextract.compat.CompanionPolicy;
import dev.svextract.compat.BEconomyAdapter;
import dev.svextract.compat.SVFrameAdapter;
import dev.svextract.compat.SVFrameMobsAdapter;
import dev.svextract.compat.ActorGunAdapter;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Hand;
import dev.svextract.config.ServerConfig;
import dev.svextract.config.LootConfig;
import dev.svextract.config.WeightedLoot;
import dev.svextract.network.Protocol;
import dev.svextract.performance.Telemetry;
import dev.svextract.persistence.*;
import dev.svextract.world.*;
import dev.svextract.match.LobbyParties;
import dev.svextract.match.GameModes;
import dev.svextract.match.DefusalMatch;
import dev.svextract.match.VersusMatch;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class MissionRuntime implements AutoCloseable {
    private record LootMint(String item,int count) {}
    public static final RegistryKey<World> HAVEN=RegistryKey.of(RegistryKeys.WORLD,Identifier.of("svextract","haven9"));
    public static final RegistryKey<World> RAID=RegistryKey.of(RegistryKeys.WORLD,Identifier.of("svextract","raid"));
    public static final RegistryKey<World> SKYRIM=RegistryKey.of(RegistryKeys.WORLD,Identifier.of("svextract","skyrim"));
    private static final class Session {
        final UUID raid,player;
        final InstancePool.Slot slot;
        final ServerConfig config;
        final ObjectiveGraph graph;
        final List<MobEntity> enemies=new ArrayList<>();
        final List<MobEntity> allies=new ArrayList<>();
        final GameModes.Definition mode;
        final int difficulty,friendlyBots;
        final Map<UUID,Long> reloads=new HashMap<>();
        final Map<UUID,Integer> reloadAmmo=new HashMap<>();
        final Map<UUID,Long> botRespawns=new LinkedHashMap<>();
        final Map<UUID,Boolean> respawnTeams=new HashMap<>();
        final Map<UUID,Long> nextShots=new HashMap<>();
        final Map<UUID,Vec3d> lastPaths=new HashMap<>();
        int navigationCursor,enemySpawnCursor;
        long nextEnemySpawnAt;
        DefusalMatch defusal;
        VersusMatch versus;
        final Map<UUID,UUID> seats=new HashMap<>();
        final List<UUID> alliedSeats=new ArrayList<>(),hostileSeats=new ArrayList<>();
        Vec3d droppedBomb,humanChannelOrigin;
        boolean humanDefusing;
        int humanPlantSite=-1,renderedRound;

        final Set<UUID> eliminatedBots=new HashSet<>();
        int teamScore,enemyScore,hostilesPending;
        long respawnAt=-1,offlineUntil=-1,lastCheckpoint;
        boolean checkpointPending;
        boolean activationRequested;
        byte[] reconnectInventory;
        Vec3d reconnectPosition;
        net.minecraft.world.GameMode previousGameMode;
        String openLootCache;
        long lastLootRequest=-100,lastLootClaim=-100;
        final Set<String> openedCaches=new HashSet<>();
        final Set<String> discoveredLandmarks=new LinkedHashSet<>();
        final List<NbtCompound> loot=new ArrayList<>();
        final Map<UUID,LootMint> lootIdentities=new HashMap<>();
        final Map<String,WeightedLoot.Entry> rolledLoot=new HashMap<>();
        RaidJournal.Entry entry;
        long started,extractAt=-1,lastNoise=-1000;
        BlockPos extractionPosition;
        int threat;
        boolean active;
        int alliesPending;
        ItemStack companionGun=ItemStack.EMPTY;
        LivingEntity visualCompanion;
        int gunCursor;
        Protocol.Hud lastHud;
        Session(UUID raid,UUID player,InstancePool.Slot slot,ServerConfig config,long tick,GameModes.Definition mode,int difficulty,int friendlyBots){
            this.raid=raid;this.player=player;this.slot=slot;this.config=config;started=tick;
            this.mode=mode;this.difficulty=difficulty;this.friendlyBots=friendlyBots;
            this.graph=new ObjectiveGraph(mode.id().equals("operations")
                ? ObjectiveGraph.blackglassOperations():ObjectiveGraph.deadSignal());
        }
    }
    private record Work<T>(CompletableFuture<T> future,Consumer<T> success,Consumer<Throwable> failure) {
        void finish(){try{success.accept(future.join());}catch(Exception e){failure.accept(e);}}
    }
    private final MinecraftServer server;
    private final ServerWorld haven,raid,skyrim;
    private final boolean skyrimInstalled;
    private boolean skyrimReady;
    private boolean hubPreparationFailed;
    private ChunkPreparation skyrimPreparation,havenPreparation;
    private final int skyrimX,skyrimY,skyrimZ;
    private final InstancePool pool;
    private final RaidJournal journal;
    private final Telemetry telemetry;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Set<UUID> locked=java.util.concurrent.ConcurrentHashMap.newKeySet(),verified=new HashSet<>();
    private final Set<UUID> recoveryReady=new HashSet<>(),stashInFlight=new HashSet<>();
    private final Map<UUID,Long> stashRequests=new HashMap<>();
    private final dev.svextract.config.StashConfig stashConfig;
    private final DefusalMatch.Rules defusalRules;
    private final List<MatchPurchase> matchShop;
    private final Set<UUID> frozen=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public boolean frozen(UUID player){return frozen.contains(player);}

    private final CobblemonAdapter cobblemon=new CobblemonAdapter();
    private final CompanionPolicy companions=new CompanionPolicy();
    private final BEconomyAdapter economy=new BEconomyAdapter();
    private final SVFrameAdapter rpg=new SVFrameAdapter();
    private final SVFrameMobsAdapter mobs=new SVFrameMobsAdapter();
    private final ActorGunAdapter actorGuns=new ActorGunAdapter();
    private final LobbyParties lobby=new LobbyParties();
    private final GameModes gameModes;
    private final Map<UUID,Session> botOwners=new HashMap<>();
    private final Map<UUID,Long> lobbyRequests=new HashMap<>();
    private boolean lobbyReady,lobbyWritePending;
    private final LootConfig lootConfig;
    private final Map<UUID,Long> handshakes=new HashMap<>(),readCommands=new HashMap<>();
    private final List<Work<?>> work=new ArrayList<>();
    private record Delivery(RaidJournal.Entry entry,int objectives){}
    private final Map<UUID,Delivery> deferredDeliveries=new HashMap<>();
    private final List<StationBlueprint.Change> havenPlan;
    private final Map<String,Object> havenMapProvenance;
    private int havenCursor;
    private boolean havenReady,internalTeleport;
    private ServerConfig config;
    private long tick, tickStart;
    private int durablePending;
    private final String entityEpoch="svextract_epoch_"+UUID.randomUUID();
    private double recentP95;

    public MissionRuntime(MinecraftServer server,ServerConfig config) throws Exception {
        this.server=server;this.config=config;
        stashConfig=dev.svextract.config.StashConfig.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("svextract/stash.json"));
        defusalRules=DefusalMatch.Rules.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("svextract/defusal.json"));
        matchShop=loadMatchShop(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("svextract/defusal-shop.json"));
        HavenTemplate havenTemplate=HavenTemplate.load();havenPlan=havenTemplate.changes();havenMapProvenance=havenTemplate.provenance();
        haven=Objects.requireNonNull(server.getWorld(HAVEN),"SVEXTRACT Haven-9 dimension is missing");
        raid=Objects.requireNonNull(server.getWorld(RAID),"SVEXTRACT raid dimension is missing");
        skyrim=server.getWorld(SKYRIM);
        Path skyManifest=server.getSavePath(WorldSavePath.ROOT).resolve("dimensions/svextract/skyrim/svextract-map-source.json");
        skyrimInstalled=skyrim!=null&&Files.isRegularFile(skyManifest);
        int[] skySpawn={0,65,0};
        if(skyrimInstalled){
            if(Files.size(skyManifest)>65536)throw new IllegalStateException("Skyrim source manifest exceeds budget");
            var json=com.google.gson.JsonParser.parseString(Files.readString(skyManifest)).getAsJsonObject();
            var position=json.getAsJsonArray("spawn");
            if(position==null||position.size()!=3)throw new IllegalStateException("Imported Skyrim map manifest must have three spawn coordinates");
            for(int i=0;i<3;i++)skySpawn[i]=position.get(i).getAsInt();
            if(Math.abs((long)skySpawn[0])>29_000_000||Math.abs((long)skySpawn[2])>29_000_000||skySpawn[1]<-63||skySpawn[1]>317)throw new IllegalStateException("Imported Skyrim spawn is outside valid world bounds");
        }
        skyrimX=skySpawn[0];skyrimY=skySpawn[1];skyrimZ=skySpawn[2];
        gameModes=GameModes.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("svextract/modes.json"));
        lootConfig=LootConfig.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("svextract/loot.json"));
        for(var cache:lootConfig.caches()){
            for(var choice:cache.choices()){
                Identifier item=Identifier.of(choice.item());
                if(!net.minecraft.registry.Registries.ITEM.containsId(item)||net.minecraft.registry.Registries.ITEM.get(item)==net.minecraft.item.Items.AIR)throw new IllegalArgumentException("loot.json cache "+cache.id()+": unknown item "+item);
                if(choice.count()>net.minecraft.registry.Registries.ITEM.get(item).getMaxCount())throw new IllegalArgumentException("loot.json cache "+cache.id()+": count exceeds item stack limit");
            }
        }
        telemetry=new Telemetry(config.performance().telemetrySamples());
        Path dir=server.getSavePath(WorldSavePath.ROOT).resolve("svextract");Files.createDirectories(dir);
        journal=new RaidJournal(dir.resolve("journal.sqlite"),config.performance().databaseQueueCapacity(),
                config.performance().databaseShutdownSeconds(),config.performance().maxSnapshotBytes());
        for(ServerWorld w:List.of(haven,raid)) {
            // Only owned dimensions: release tickets retained by a crashed prior process.
            for(long c:w.getForcedChunks().toLongArray()){ChunkPos pos=new ChunkPos(c);w.setChunkForced(pos.x,pos.z,false);}
            // Vanilla dimensions share the save's game rules. Never change them here:
            // that would also alter permanent survival-world spawning and fire ticks.
        }
        pool=new InstancePool(raid,config,telemetry,MapTemplates.blackglass(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()));
        try{cobblemon.initialize();}catch(Exception e){SvExtract.LOGGER.error("Cobblemon compatibility unavailable; deployments with Cobblemon will fail closed",e);}
        try{economy.initialize();}catch(Exception e){SvExtract.LOGGER.error("BEconomy balance discovery unavailable",e);}
        try{rpg.initialize();}catch(Exception e){SvExtract.LOGGER.error("SVFrameMMO API unavailable; transitions with it installed will fail closed",e);}
        try{mobs.initialize();}catch(Exception e){SvExtract.LOGGER.error("SVFrameMobs mission spawn API unavailable",e);}
        try{actorGuns.initialize();}catch(Exception e){SvExtract.LOGGER.error("Point Blank actor gun APIs unavailable",e);}
        watch(journal.pending(),n->durablePending=n,e->SvExtract.LOGGER.error("Cannot query pending raids",e));
        watch(journal.lobby(),state->{lobby.restore(state);lobbyReady=true;},e->SvExtract.LOGGER.error("Lobby membership unavailable; invites remain disabled",e));
    }
    private <T> void watch(CompletableFuture<T> f,Consumer<T> success,Consumer<Throwable> failure){work.add(new Work<>(f,success,failure));}
    public boolean locked(UUID id){return locked.contains(id);}
    public boolean participating(UUID id){return sessions.containsKey(id);}
    public boolean restricted(UUID id){return locked(id)||participating(id);}
    public boolean mayTeleport(ServerPlayerEntity player,ServerWorld destination,double x,double y,double z){
        if(internalTeleport)return true;
        if(locked(player.getUuid()))return false;
        Session s=sessions.get(player.getUuid());
        if(s==null)return destination!=raid;
        return s.active&&destination==raid&&s.slot.contains(x,y,z);
    }
    public boolean allowCompanion(UUID owner,UUID pokemon){return !restricted(owner)||(!locked(owner)&&companions.allows(owner,pokemon));}
    public void startTick(){tickStart=System.nanoTime();}
    public void entityLoaded(Entity e){
        if((e.getCommandTags().contains("svextract_enemy")||e.getCommandTags().contains("svextract_ally"))&&!e.getCommandTags().contains(entityEpoch))e.discard();
    }

    public void join(ServerPlayerEntity player){
        UUID id=player.getUuid();locked.add(id);verified.remove(id);recoveryReady.remove(id);handshakes.put(id,tick);
        if(!ServerPlayNetworking.canSend(player,Protocol.Hello.ID)){
            player.networkHandler.disconnect(Text.literal("Install SVEXTRACT "+SvExtract.VERSION+" on this client to join this server."));return;
        }
        ServerPlayNetworking.send(player,Protocol.hello());
        watch(journal.live(id),entry->{
            ServerPlayerEntity online=server.getPlayerManager().getPlayer(id);if(online==null)return;
            Session connected=sessions.get(id);
            if(entry.isPresent()&&connected!=null&&connected.active&&connected.raid.equals(entry.get().raid())&&connected.offlineUntil>=tick){
                try{
                    if(connected.reconnectInventory!=null)InventorySnapshot.restore(online,connected.reconnectInventory,config.performance().maxSnapshotBytes());
                    Vec3d location=connected.reconnectPosition==null?new Vec3d(connected.slot.originX,65,0):connected.reconnectPosition;
                    teleport(online,raid,location.x,location.y,location.z,online.getYaw(),online.getPitch());rpg.restoreStats(online,false);
                    connected.offlineUntil=-1;if(verified.contains(id))locked.remove(id);sendHud(online,connected,true);
                }catch(Exception e){failSafe(id,e);}
            }
            else if(entry.isPresent())recover(online,entry.get());
            else {
                if(online.getWorld().getRegistryKey().equals(RAID)||online.getWorld()==haven&&havenReady&&(online.getY()<64||online.getY()>256))returnToHaven(online);
                recoverStash(online);
            }
        },e->failSafe(id,e));
    }
    public void hello(ServerPlayerEntity player,Protocol.Hello hello){
        UUID id=player.getUuid();
        if(!handshakes.containsKey(id)||verified.contains(id))return;
        if(hello.protocol()!=Protocol.VERSION||!SvExtract.VERSION.equals(hello.modVersion())) {
            player.networkHandler.disconnect(Text.literal("SVEXTRACT mismatch: server "+SvExtract.VERSION+" protocol "+Protocol.VERSION+", client "+hello.modVersion()+" protocol "+hello.protocol()+". Install the matching JAR."));return;
        }
        verified.add(id);handshakes.remove(id);
        // The journal query may still be pending. Keep the inventory lock until its completion.
        watch(journal.live(id),entry->{
            Session s=sessions.get(id);
            if(entry.isEmpty()&&s==null&&recoveryReady.contains(id)&&!stashInFlight.contains(id)||entry.isPresent()&&s!=null&&s.active&&s.offlineUntil<0&&s.respawnAt<0&&s.raid.equals(entry.get().raid()))locked.remove(id);
        },e->failSafe(id,e));
        if(!sessions.containsKey(id))openLobby(player,true);
    }
    public void lobbyAction(ServerPlayerEntity player,Protocol.LobbyAction action){
        UUID id=player.getUuid();
        if(!verified.contains(id)||action.action()<0||action.action()>10)return;
        Long last=lobbyRequests.get(id);int interval=Math.max(2,config.performance().commandCooldownTicks()/10);
        if(last!=null&&tick-last<interval)return;lobbyRequests.put(id,tick);
        try{
            switch(action.action()){
                case 0->openLobby(player,true);
                case 1->{
                    if(gameModes.get(action.selection()).equipmentRisk()&&!action.acceptEquipmentRisk())throw new IllegalArgumentException("Confirm equipment risk before deploying: death, timeout and abandonment lose deployed gear.");
                    deploy(player,action.selection(),action.difficulty(),action.friendlyBots());openLobby(player,false);
                }
                case 2->{ServerPlayerEntity target=server.getPlayerManager().getPlayer(UUID.fromString(action.target()));if(target==null)throw new IllegalArgumentException("Player is offline");invite(player,target);openLobby(player,false);}
                case 3->{acceptInvite(player);openLobby(player,false);}
                case 4->{leaveSquad(player);openLobby(player,false);}
                case 5->abort(player);
                case 6->{skyrim(player);openLobby(player,false);}
                case 7->{haven(player);openLobby(player,false);}
                case 8->{/* Screen closed; no persistent change. */}
                case 9->openVersusShop(player);
                case 10->buyVersus(player,action.selection());
            }
        }catch(Exception e){player.sendMessage(Text.literal(e.getMessage()==null?"Lobby action rejected":e.getMessage()),false);openLobby(player,false);}
    }
    public void openLobby(ServerPlayerEntity player,boolean open){
        if(!verified.contains(player.getUuid())||!ServerPlayNetworking.canSend(player,Protocol.LobbyState.ID))return;
        Map<String,Object> state=new LinkedHashMap<>();
        state.put("player",player.getName().getString());state.put("atHub",isHub(player.getServerWorld()));
        state.put("worlds",List.of(Map.of("id","haven","installed",true,"prepared",havenReady),Map.of("id","skyrim","installed",skyrimInstalled,"prepared",skyrimReady)));
        state.put("locked",restricted(player.getUuid()));state.put("prepared",havenReady&&pool.slots().stream().anyMatch(s->s.state==InstancePool.State.READY));
        state.put("pointBlank",actorGuns.ready());state.put("modes",gameModes.all());
        try{state.put("profile",rpg.profile(player));state.put("classes",rpg.classIds());}catch(Exception e){state.put("profile",Map.of("class","UNAVAILABLE","level",0));}
        List<Map<String,String>> players=new ArrayList<>();
        for(ServerPlayerEntity online:server.getPlayerManager().getPlayerList()){
            if(online!=player&&players.size()<32)players.add(Map.of("id",online.getUuidAsString(),"name",online.getName().getString()));
        }
        state.put("online",players);
        state.put("squad",lobby.squad(player.getUuid()).map(group->group.members().stream().map(member->{
            ServerPlayerEntity online=server.getPlayerManager().getPlayer(member);return online==null?member.toString().substring(0,8)+" (offline)":online.getName().getString();
        }).toList()).orElse(List.of(player.getName().getString())));
        try{UUID inviter=lobby.inviter(player.getUuid(),System.nanoTime());ServerPlayerEntity online=server.getPlayerManager().getPlayer(inviter);state.put("invite",online==null?"":online.getName().getString());}catch(Exception ignored){state.put("invite","");}
        List<String> balances=new ArrayList<>();
        try{if(economy.ready())for(var currency:economy.currencies()){if(balances.size()==4)break;balances.add(currency.name()+": "+economy.balance(player.getUuid(),currency.id()).toPlainString());}}
        catch(Exception e){SvExtract.LOGGER.warn("Lobby economy read failed",e);}
        state.put("balances",balances);
        watch(journal.progress(player.getUuid()),progress->{
            state.put("extractions",progress.extractions());state.put("reputation",progress.aegisReputation());
            ServerPlayerEntity online=server.getPlayerManager().getPlayer(player.getUuid());
            if(online==null||!verified.contains(player.getUuid()))return;
            state.put("locked",restricted(online.getUuid()));state.put("atHub",isHub(online.getServerWorld()));
            state.put("prepared",havenReady&&pool.slots().stream().anyMatch(slot->slot.state==InstancePool.State.READY));
            state.put("readyMaps",pool.slots().stream().filter(slot->slot.state==InstancePool.State.READY).map(slot->slot.map).distinct().toList());
            String json=new com.google.gson.Gson().toJson(state);
            if(json.length()>16384)throw new IllegalStateException("Lobby payload exceeded limit");
            ServerPlayNetworking.send(online,new Protocol.LobbyState(json,open));telemetry.packet(json.length());
        },e->SvExtract.LOGGER.error("Lobby snapshot unavailable",e));
    }
    public void disconnect(ServerPlayerEntity player){
        UUID id=player.getUuid();handshakes.remove(id);verified.remove(id);recoveryReady.remove(id);stashRequests.remove(id);deferredDeliveries.remove(id);readCommands.remove(id);lobbyRequests.remove(id);
        Session s=sessions.get(id);
        if(s!=null&&s.active&&!s.mode.id().equals("training")){
            s.offlineUntil=tick+s.mode.reconnectSeconds()*20L;s.reconnectPosition=player.getPos();
            for(MobEntity actor:s.enemies){actor.setAiDisabled(true);actor.getNavigation().stop();}
            for(MobEntity actor:s.allies){actor.setAiDisabled(true);actor.getNavigation().stop();}
            try{
                s.reconnectInventory=InventorySnapshot.capture(player,s.config.performance().maxSnapshotBytes());
                checkpoint(player,s);
            }catch(Exception e){SvExtract.LOGGER.error("Disconnected raid snapshot unavailable; durable ownership retained",e);}
        }else if(s!=null){s.active=false;dispose(s);sessions.remove(id);}
        // Recovery always consults the durable journal on next login. Never award on disconnect.
        locked.remove(id);
        if(s==null||!s.active)companions.release(id);
    }
    private void failSafe(UUID id,Throwable e){
        locked.add(id);SvExtract.LOGGER.error("SVEXTRACT transaction pending for {}. Inventory remains protected.",id,e);
        ServerPlayerEntity p=server.getPlayerManager().getPlayer(id);
        if(p!=null)p.sendMessage(Text.literal("SVEXTRACT persistence failed. Inventory is locked for safety; reconnect to recover. Ask an administrator to inspect the log if it persists."),false);
    }
    public void deploy(ServerPlayerEntity player){
        deploy(player,"training",1,config.mission().friendlyBots());
    }
    public void deploy(ServerPlayerEntity player,String modeId,int difficulty,int friendlyBots){
        GameModes.Definition mode;
        try{mode=gameModes.get(modeId);}catch(IllegalArgumentException e){player.sendMessage(Text.literal(e.getMessage()),false);return;}
        if(difficulty<0||difficulty>2||friendlyBots<0||friendlyBots>4){player.sendMessage(Text.literal("Invalid bot selection"),false);return;}
        if(mode.id().equals("versus"))friendlyBots=4;
        else if(friendlyBots>2){player.sendMessage(Text.literal("Operations supports at most two AI squadmates"),false);return;}
        UUID id=player.getUuid();
        if(lobby.squad(id).map(group->group.members().size()>1).orElse(false)){
            player.sendMessage(Text.literal("Shared squad deployment is not available in this rebuild. Leave the squad to start a private bot match."),false);return;
        }
        if(mode.id().equals("versus")&&(!actorGuns.ready()||config.performance().maxEnemiesPerInstance()<5)){
            player.sendMessage(Text.literal("Versus needs a compatible Point Blank adapter and an enemy budget of at least five."),false);return;
        }
        if(!verified.contains(id)||restricted(id)){message(player,"svextract.error.busy");return;}
        if(!isHub(player.getServerWorld())||!havenReady||(player.getWorld()==skyrim&&!skyrimReady)){message(player,"svextract.error.haven");return;}
        if(config.requirePointBlank()&&!Integrations.pointBlankLoaded()){message(player,"svextract.error.pointblank");return;}
        if(player.currentScreenHandler!=player.playerScreenHandler||!player.currentScreenHandler.getCursorStack().isEmpty()){
            message(player,"svextract.error.inventory");return;
        }
        if(player.hasVehicle()||player.isSpectator()||player.isCreative()){message(player,"svextract.error.gamemode");return;}
        if(telemetry.snapshot().p95Ms()>config.performance().admissionP95Ms()){message(player,"svextract.error.load");return;}
        try{companions.select(id,cobblemon.partyIds(player));cobblemon.recallParty(player);}
        catch(Exception e){player.sendMessage(Text.literal(e.getMessage()),false);return;}
        Optional<InstancePool.Slot> claim=pool.claim(mode.map());
        if(claim.isEmpty()){message(player,"svextract.error.capacity");return;}
        Session s=new Session(UUID.randomUUID(),id,claim.get(),config,tick,mode,difficulty,friendlyBots);
        s.previousGameMode=player.interactionManager.getGameMode();
        byte[] snapshot;
        try {snapshot=InventorySnapshot.capture(player,config.performance().maxSnapshotBytes());}
        catch(Exception e){pool.cleanup(s.slot);player.sendMessage(Text.literal(e.getMessage()),false);return;}
        sessions.put(id,s);locked.add(id);player.closeHandledScreen();
        RaidJournal.ReturnPoint home=player.getWorld()==skyrim?new RaidJournal.ReturnPoint(SKYRIM.getValue().toString(),skyrimX,skyrimY,skyrimZ,180,0):new RaidJournal.ReturnPoint(HAVEN.getValue().toString(),0,65,4,180,0);
        watch(journal.reserve(s.raid,id,snapshot,home,mode.id()),entry->{
            s.entry=entry;durablePending++;
            if(server.getPlayerManager().getPlayer(id)==null||sessions.get(id)!=s)return;
        },e->failSafe(id,e));
    }
    private void activatePrepared(Session s){
        if(s.activationRequested||s.entry==null||s.slot.state!=InstancePool.State.ACTIVE)return;
        s.activationRequested=true;UUID id=s.player;
        watch(journal.activate(s.raid),active->{
                s.entry=active;ServerPlayerEntity p=server.getPlayerManager().getPlayer(id);
                if(p==null)return;
                if(sessions.get(id)!=s)return;
                try{rpg.restoreStats(p,false);teleport(p,raid,s.slot.originX,65,0,0,0);rpg.restoreStats(p,false);}
                catch(Exception e){failSafe(id,e);return;}
                s.started=tick;s.active=true;locked.remove(id);
                s.alliesPending=s.friendlyBots;s.hostilesPending=Math.min(s.mode.hostileBots(),s.config.performance().maxEnemiesPerInstance());
                if(s.mode.id().equals("versus")){
                    try{if(s.difficulty==2)startDefusal(p,s);else startTeamVersus(p,s);}catch(Exception e){failSafe(id,e);return;}
                }
                message(p,s.mode.id().equals("training")?"svextract.mission.briefing":s.mode.id().equals("versus")?"svextract.versus.briefing":"svextract.operations.briefing");sendHud(p,s,true);
        },e->failSafe(id,e));
    }
    public boolean interact(ServerPlayerEntity player,BlockPos position){
        Session s=sessions.get(player.getUuid());
        if(s==null||!s.active||locked(player.getUuid())||player.getWorld()!=raid)return false;
        if(s.mode.id().equals("versus"))return s.defusal!=null&&interactDefusal(player,s,position);
        if(player.getPos().squaredDistanceTo(Vec3d.ofCenter(position))>25)return false;
        for(var cache:lootConfig.caches())if(s.slot.at(cache.x(),cache.y(),cache.z()).equals(position)){openLoot(player,s,cache);return true;}
        if(s.slot.map.equals("blackglass_industrial")&&position.equals(s.slot.at(43,65,-35))){beginExtraction(player,s,position);return true;}
        for(ObjectiveGraph.Node n:s.graph.nodes()) {
            if(!s.slot.at(n.x(),n.y(),n.z()).equals(position))continue;
            if(!s.graph.available(n.id())&&!(s.mode.optionalObjectives()&&n.kind()==ObjectiveGraph.Kind.EXTRACTION)){message(player,"svextract.objective.locked");return true;}
            if(n.kind()==ObjectiveGraph.Kind.EXTRACTION){
                if(!s.mode.optionalObjectives()&&!hasCore(player,s)){message(player,"svextract.error.core");return true;}
                beginExtraction(player,s,position);
            } else {
                if(n.kind()==ObjectiveGraph.Kind.RECOVERY){
                    ItemStack core=new ItemStack(Content.DATA_CORE);NbtCompound identity=new NbtCompound();identity.putUuid("svextract_raid",s.raid);core.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(identity));
                    if(!player.getInventory().insertStack(core)){message(player,"svextract.error.full");return true;}
                }
                if(!s.graph.complete(n.id()))return true;
                if(n.id().equals("power"))try(WorldMutationScope ignored=WorldMutationScope.enter()){
                    pool.beforeMutation(s.slot,position);
                    raid.setBlockState(position,Blocks.REDSTONE_LAMP.getDefaultState().with(net.minecraft.block.RedstoneLampBlock.LIT,true),Block.NOTIFY_LISTENERS);
                }
                player.sendMessage(Text.translatable("svextract.objective.completed",Text.translatable("svextract.objective."+n.id())),false);
            }
            sendHud(player,s,true);return true;
        }
        return false;
    }
    private void beginExtraction(ServerPlayerEntity player,Session s,BlockPos position){
        if(s.extractAt>=0)return;
        s.extractionPosition=position.toImmutable();s.extractAt=tick+s.config.mission().extractionSeconds()*20L;s.threat=Math.max(s.threat,2);
        message(player,"svextract.extraction.started");raid.playSound(null,position,Content.EVACUATION,SoundCategory.BLOCKS,0.8f,1);sendHud(player,s,true);
    }
    private void openLoot(ServerPlayerEntity player,Session s,LootConfig.Cache cache){openLoot(player,s,cache,"");}
    private void openLoot(ServerPlayerEntity player,Session s,LootConfig.Cache cache,String status){
        if(status.isEmpty()&&tick-s.lastLootRequest<4)return;s.lastLootRequest=tick;s.openLootCache=cache.id();
        try{
            WeightedLoot.Entry rolled=s.rolledLoot.computeIfAbsent(cache.id(),id->lootConfig.roll(cache,s.raid));
            ItemStack item=new ItemStack(net.minecraft.registry.Registries.ITEM.get(Identifier.of(rolled.item())),rolled.count());actorGuns.initializeLoot(item);
            var bytes=new java.io.ByteArrayOutputStream();try(var out=new java.io.DataOutputStream(bytes)){net.minecraft.nbt.NbtIo.writeCompound((NbtCompound)item.encode(server.getRegistryManager()),out);}
            if(bytes.size()>60*1024)throw new IllegalStateException("Loot preview exceeds packet budget");
            ServerPlayNetworking.send(player,new Protocol.LootState(cache.id(),bytes.toByteArray(),s.openedCaches.contains(cache.id()),status));telemetry.packet(bytes.size()+64);
        }catch(Exception e){player.sendMessage(Text.literal("Loot preview unavailable; no items moved"),false);SvExtract.LOGGER.error("Loot preview failed",e);}
    }
    public void claimLoot(ServerPlayerEntity player,Protocol.LootClaim request){
        Session s=sessions.get(player.getUuid());
        if(s==null||!s.active||locked(s.player)||s.defusal!=null||player.getWorld()!=raid||!request.cache().equals(s.openLootCache)||tick-s.lastLootClaim<4)return;
        s.lastLootClaim=tick;s.lastLootRequest=Math.min(s.lastLootRequest,tick-4);
        var cache=lootConfig.caches().stream().filter(c->c.id().equals(request.cache())).findFirst().orElse(null);
        if(cache==null||player.squaredDistanceTo(Vec3d.ofCenter(s.slot.at(cache.x(),cache.y(),cache.z())))>25)return;
        BlockPos position=s.slot.at(cache.x(),cache.y(),cache.z());
            if(s.openedCaches.contains(cache.id())){openLoot(player,s,cache);return;}
            if(!raid.getBlockState(position).isOf(Content.SUPPLY_CRATE)&&!raid.getBlockState(position).isOf(Blocks.BARREL)){player.sendMessage(Text.literal("Configured supply cache has no valid crate; administrator must fix loot.json."),false);return;}
            try{
                WeightedLoot.Entry rolled=s.rolledLoot.computeIfAbsent(cache.id(),id->lootConfig.roll(cache,s.raid));
                ItemStack stack=new ItemStack(net.minecraft.registry.Registries.ITEM.get(Identifier.of(rolled.item())),rolled.count());actorGuns.initializeLoot(stack);
                NbtCompound identity=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
                UUID lootIdentity=UUID.nameUUIDFromBytes((s.raid+":"+cache.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));identity.putUuid("svextract_item",lootIdentity);
                identity.putUuid("svextract_raid",s.raid);stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(identity));
                NbtCompound encoded=(NbtCompound)stack.encode(server.getRegistryManager());
                List<NbtCompound> candidate=new ArrayList<>(s.loot);candidate.add(encoded);
                if(s.mode.equipmentRisk())InventorySnapshot.withLoot(InventorySnapshot.capture(player,s.config.performance().maxSnapshotBytes()),List.of(encoded),s.config.performance().maxSnapshotBytes());
                else InventorySnapshot.withLoot(s.entry.reserved(),candidate,s.config.performance().maxSnapshotBytes());
                Text name=stack.getName();int count=stack.getCount();
                int empty=0;while(empty<36&&!player.getInventory().getStack(empty).isEmpty())empty++;
                if(empty==36){openLoot(player,s,cache,"Loadout inventory is full; no items moved");return;}
                // One new identity uses one empty slot; insertStack can partially insert on failure.
                player.getInventory().setStack(empty,stack);
                s.loot.add(encoded);s.lootIdentities.put(lootIdentity,new LootMint(rolled.item(),count));s.openedCaches.add(cache.id());
                player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();
                if(raid.getBlockState(position).isOf(Content.SUPPLY_CRATE))try(WorldMutationScope ignored=WorldMutationScope.enter()){
                    pool.beforeMutation(s.slot,position);raid.setBlockState(position,Content.SUPPLY_CRATE.getDefaultState().with(dev.svextract.world.SupplyCrateBlock.OPEN,true),Block.NOTIFY_LISTENERS);
                }
                player.sendMessage(Text.translatable("svextract.loot.collected",name,count),false);openLoot(player,s,cache);
            }catch(java.io.IOException e){openLoot(player,s,cache,"Inventory snapshot budget or capacity exceeded; no items moved");}
            catch(Exception e){openLoot(player,s,cache,"Loot claim failed; refresh the crate before retrying");SvExtract.LOGGER.error("Supply cache claim failed",e);}
    }
    private boolean hasCore(ServerPlayerEntity p,Session s){
        for(int i=0;i<p.getInventory().size();i++){
            ItemStack item=p.getInventory().getStack(i);
            if(item.isOf(Content.DATA_CORE)){
                NbtCompound data=item.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
                if(data.containsUuid("svextract_raid")&&s.raid.equals(data.getUuid("svextract_raid")))return true;
            }
        }
        return false;
    }
    public void noise(ServerPlayerEntity player,double radius){
        Session s=sessions.get(player.getUuid());
        if(s==null||!s.active||tick-s.lastNoise<s.config.performance().noiseCooldownTicks())return;
        s.lastNoise=tick;s.threat=Math.min(5,s.threat+1);
        double distance=Math.min(radius,s.config.mission().noiseRadius());
        // Cached bounded raid enemy set; no global entity scan, no extra projectiles or damage calls.
        for(MobEntity e:s.enemies)if(e.isAlive()&&e.squaredDistanceTo(player)<distance*distance){e.setAiDisabled(false);e.setTarget(player);}
    }
    public boolean allowDamage(ServerPlayerEntity player,DamageSource source,float amount){
        if(locked(player.getUuid()))return false;
        if(isHub(player.getServerWorld()))return false;
        Session s=sessions.get(player.getUuid());if(s==null||!s.active)return true;
        if(s.respawnAt>=0||s.defusal!=null&&s.defusal.phase()!=DefusalMatch.Phase.LIVE||s.versus!=null&&s.versus.phase()!=VersusMatch.Phase.ACTIVE)return false;
        if(s.defusal!=null){s.humanChannelOrigin=null;s.humanPlantSite=-1;s.humanDefusing=false;}
        if(source.getAttacker() instanceof MobEntity mob&&s.allies.contains(mob))return false;
        if(source.getAttacker() instanceof ServerPlayerEntity)return false;
        return true;
    }
    public boolean allowActorDamage(LivingEntity target,DamageSource source){
        Session s=botOwners.get(target.getUuid());if(s==null||!s.active)return true;
        if(s.defusal!=null&&s.defusal.phase()!=DefusalMatch.Phase.LIVE||s.versus!=null&&s.versus.phase()!=VersusMatch.Phase.ACTIVE)return false;
        Entity attacker=source.getAttacker();
        if(attacker==null)return true;
        boolean friendly=s.allies.contains(target);
        if(attacker instanceof ServerPlayerEntity p){if(s.defusal!=null&&s.humanChannelOrigin!=null)s.humanChannelOrigin=null;return !friendly&&p.getUuid().equals(s.player);}
        if(attacker instanceof MobEntity mob){
            boolean attackerFriendly=s.allies.contains(mob),attackerHostile=s.enemies.contains(mob);
            if(!attackerFriendly&&!attackerHostile)return true;
            return friendly!=attackerFriendly;
        }
        return true;
    }
    public boolean botDeath(LivingEntity target,DamageSource source){
        Session s=botOwners.get(target.getUuid());
        if(s==null||!s.active||!s.mode.id().equals("versus"))return true;
        if(s.defusal!=null){
            UUID victim=s.seats.get(target.getUuid());UUID killer=defusalSeat(s,source.getAttacker());
            if(victim!=null){if(victim.equals(s.defusal.carrier()))s.droppedBomb=target.getPos();s.defusal.eliminate(victim,killer);}
        }
        if(s.versus!=null){
            UUID victim=s.seats.get(target.getUuid()),killer=defusalSeat(s,source.getAttacker());
            if(victim!=null&&s.versus.elimination(victim,killer,tick)){
                s.botRespawns.put(victim,tick+s.versus.rules().respawnSeconds()*20L);
                s.respawnTeams.put(victim,s.allies.contains(target));
                s.teamScore=s.versus.score(0);s.enemyScore=s.versus.score(1);
            }
        }
        s.allies.remove(target);s.enemies.remove(target);s.seats.remove(target.getUuid());botOwners.remove(target.getUuid());s.reloads.remove(target.getUuid());s.reloadAmmo.remove(target.getUuid());s.nextShots.remove(target.getUuid());s.lastPaths.remove(target.getUuid());
        target.discard();return false;
    }
    public void death(ServerPlayerEntity player,DamageSource source){
        Session s=sessions.get(player.getUuid());
        if(s!=null&&s.active&&s.mode.id().equals("versus")){
            if(s.respawnAt>=0)return;
            if(s.defusal!=null){if(s.player.equals(s.defusal.carrier()))s.droppedBomb=player.getPos();s.defusal.eliminate(s.player,defusalSeat(s,source.getAttacker()));}
            if(s.versus!=null){s.versus.elimination(s.player,defusalSeat(s,source.getAttacker()),tick);s.teamScore=s.versus.score(0);s.enemyScore=s.versus.score(1);}
            s.respawnAt=s.versus!=null?tick+s.versus.rules().respawnSeconds()*20L:Long.MAX_VALUE;player.setHealth(1);
            player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);sendHud(player,s,true);return;
        }
        if(s!=null && s.active){player.setHealth(1);settle(player,s,"DEATH");}
    }
    public void abort(ServerPlayerEntity player){Session s=sessions.get(player.getUuid());if(s!=null&&s.active)settle(player,s,"ABORTED");}
    private void settle(ServerPlayerEntity player,Session s,String outcome){
        if(locked(player.getUuid())||!s.active||s.entry==null)return;
        s.active=false;locked.add(player.getUuid());player.closeHandledScreen();
        byte[] settlement;
        try{
            if(s.mode.equipmentRisk())settlement="EXTRACTED".equals(outcome)?currentInventory(player,s):InventorySnapshot.empty(s.config.performance().maxSnapshotBytes());
            else settlement="EXTRACTED".equals(outcome)?InventorySnapshot.withLoot(s.entry.reserved(),carriedLoot(player,s),s.config.performance().maxSnapshotBytes()):s.entry.reserved();
            if("EXTRACTED".equals(outcome))settlement=InventorySnapshot.secureRaidLoot(settlement,s.config.performance().maxSnapshotBytes(),s.raid);
        }
        catch(Exception e){failSafe(s.player,e);return;}
        watch(journal.prepareSettlement(s.raid,settlement,outcome),entry->{
            s.entry=entry;ServerPlayerEntity online=server.getPlayerManager().getPlayer(s.player);
            if(online!=null)deliver(online,entry,s.graph.completedIds().size());
        },e->failSafe(s.player,e));
    }
    private byte[] currentInventory(ServerPlayerEntity player,Session s)throws Exception{
        byte[] snapshot=InventorySnapshot.capture(player,s.config.performance().maxSnapshotBytes());
        if(!s.companionGun.isEmpty())snapshot=InventorySnapshot.withLoot(snapshot,List.of((NbtCompound)s.companionGun.encode(server.getRegistryManager())),s.config.performance().maxSnapshotBytes());
        return snapshot;
    }
    private void checkpoint(ServerPlayerEntity player,Session s)throws Exception{
        if(s.checkpointPending||s.entry==null||s.entry.state()!=RaidState.ACTIVE)return;
        byte[] snapshot=currentInventory(player,s);s.checkpointPending=true;s.lastCheckpoint=tick;
        watch(journal.checkpoint(s.raid,snapshot),entry->{s.entry=entry;s.checkpointPending=false;},e->{s.checkpointPending=false;failSafe(s.player,e);});
    }
    private void expireDisconnected(Session s){
        if(!s.active)return;s.active=false;locked.add(s.player);
        byte[] settlement;
        try{settlement=s.mode.equipmentRisk()?InventorySnapshot.empty(s.config.performance().maxSnapshotBytes()):s.entry.reserved();}
        catch(Exception e){failSafe(s.player,e);return;}
        watch(journal.prepareSettlement(s.raid,settlement,"TIMEOUT"),entry->{
            s.entry=entry;ServerPlayerEntity online=server.getPlayerManager().getPlayer(s.player);
            if(online!=null)deliver(online,entry,s.graph.completedIds().size());
            else{sessions.remove(s.player);dispose(s);}
        },e->failSafe(s.player,e));
    }
    private record MatchPurchase(String id,String gun,String ammo,int price){}
    private static final List<MatchPurchase> MATCH_SHOP=List.of(new MatchPurchase("glock17","pointblank:glock17","pointblank:ammo9mm",200),new MatchPurchase("mp5","pointblank:mp5","pointblank:ammo9mm",1500),new MatchPurchase("ak47","pointblank:ak47","pointblank:ammo762",2700),new MatchPurchase("m4a1","pointblank:m4a1","pointblank:ammo556",3100),new MatchPurchase("armor","minecraft:iron_chestplate","",650),new MatchPurchase("defuse_kit","minecraft:shears","",400));
    private static List<MatchPurchase> loadMatchShop(Path path)throws java.io.IOException{
        var json=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        if(!Files.exists(path)){Files.createDirectories(path.getParent());Files.writeString(path,json.toJson(MATCH_SHOP)+"\n");}
        if(Files.size(path)>16384)throw new java.io.IOException(path+": match shop exceeds 16 KiB");
        try{
            var products=java.util.Arrays.asList(json.fromJson(Files.readString(path),MatchPurchase[].class));Set<String> keys=new HashSet<>();
            if(products.isEmpty()||products.size()>6)throw new IllegalArgumentException("Match shop requires 1–6 products");
            for(var product:products){
                if(product==null||product.id()==null||!product.id().matches("[a-z0-9_]{1,24}")||!keys.add(product.id())||product.price()<1||product.price()>16000||product.gun()==null||!product.gun().matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||product.ammo()==null||(!Set.of("armor","defuse_kit").contains(product.id())&&!product.ammo().matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")))throw new IllegalArgumentException("Invalid or duplicate product, registry item or price");
            }
            return List.copyOf(products);
        }catch(RuntimeException e){throw new java.io.IOException(path+": "+e.getMessage(),e);}
    }
    private UUID defusalSeat(Session s,Entity entity){return entity instanceof ServerPlayerEntity p&&p.getUuid().equals(s.player)?s.player:entity==null?null:s.seats.get(entity.getUuid());}
    private BlockPos bombSite(Session s,int site){return site==0?s.slot.at(-10,65,20):s.slot.at(24,65,-20);}
    private Vec3d defusalObjective(Session s,UUID seat){
        if(seat==null||s.defusal==null)return null;
        if(s.defusal.planted()&&s.defusal.team(seat)!=s.defusal.attackingTeam())return Vec3d.ofBottomCenter(bombSite(s,s.defusal.site())).add(0,0,1);
        if(seat.equals(s.defusal.carrier()))return Vec3d.ofBottomCenter(bombSite(s,s.defusal.round()%2)).add(0,0,1);
        if(s.droppedBomb!=null&&s.defusal.team(seat)==s.defusal.attackingTeam())return s.droppedBomb;
        return null;
    }
    private LinkedHashMap<UUID,Integer> versusRoster(Session s){
        LinkedHashMap<UUID,Integer> roster=new LinkedHashMap<>();roster.put(s.player,0);
        for(int i=0;i<9;i++){
            UUID seat=UUID.nameUUIDFromBytes((s.raid+":versus-seat:"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(i<4)s.alliedSeats.add(seat);else s.hostileSeats.add(seat);roster.put(seat,i<4?0:1);
        }
        return roster;
    }
    private void startDefusal(ServerPlayerEntity player,Session s)throws Exception{
        s.defusal=new DefusalMatch(defusalRules,versusRoster(s),tick);resetDefusalRound(player,s);
    }
    private void startTeamVersus(ServerPlayerEntity player,Session s)throws Exception{
        LinkedHashMap<UUID,Integer> roster=versusRoster(s);
        List<VersusMatch.Seat> seats=new ArrayList<>();
        roster.forEach((id,team)->seats.add(new VersusMatch.Seat(id,team,!id.equals(s.player))));
        VersusMatch.Rules rules=s.difficulty==0?VersusMatch.Rules.tdm():VersusMatch.Rules.domination();
        s.versus=new VersusMatch(rules,seats,tick);
        issueVersusKit(player,s);player.changeGameMode(s.previousGameMode);rpg.restoreStats(player,true);
        if(!rpg.present())player.setHealth(player.getMaxHealth());
        s.alliesPending=4;s.hostilesPending=5;s.respawnAt=-1;
        teleport(player,raid,s.slot.originX-27,65,10,270,0);
        frozen.add(s.player);sendHud(player,s,true);
    }
    private boolean tickTeamVersus(ServerPlayerEntity player,Session s){
        if(s.versus==null){failSafe(s.player,new IllegalStateException("Versus score controller unavailable"));return true;}
        s.versus.tick(tick);
        if(s.versus.phase()==VersusMatch.Phase.ACTIVE)frozen.remove(s.player);
        if(s.versus.rules().mode()==VersusMatch.Mode.DOMINATION&&tick%20==0){
            for(int objective=0;objective<3;objective++){
                BlockPos point=objective==0?s.slot.at(-10,65,20):objective==1?s.slot.at(0,65,0):s.slot.at(24,65,-20);
                Vec3d location=Vec3d.ofCenter(point);int friendly=0,hostile=0;
                if(s.respawnAt<0&&player.squaredDistanceTo(location)<=49)friendly++;
                for(MobEntity bot:s.allies)if(bot.isAlive()&&bot.squaredDistanceTo(location)<=49)friendly++;
                for(MobEntity bot:s.enemies)if(bot.isAlive()&&bot.squaredDistanceTo(location)<=49)hostile++;
                if(friendly>0&&hostile==0)s.versus.capture(objective,0);
                else if(hostile>0&&friendly==0)s.versus.capture(objective,1);
            }
        }
        Iterator<Map.Entry<UUID,Long>> respawning=s.botRespawns.entrySet().iterator();
        while(respawning.hasNext()){
            var respawn=respawning.next();if(tick<respawn.getValue())continue;
            if(s.respawnTeams.remove(respawn.getKey())==Boolean.TRUE)s.alliesPending++;
            else s.hostilesPending++;
            respawning.remove();
        }
        if(s.respawnAt>=0&&tick>=s.respawnAt&&s.versus.phase()!=VersusMatch.Phase.FINISHED){
            try{
                issueVersusKit(player,s);player.changeGameMode(s.previousGameMode);rpg.restoreStats(player,true);
                if(!rpg.present())player.setHealth(player.getMaxHealth());
                teleport(player,raid,s.slot.originX-27,65,10,270,0);s.respawnAt=-1;
            }catch(Exception e){failSafe(s.player,e);return true;}
        }
        s.teamScore=s.versus.score(0);s.enemyScore=s.versus.score(1);
        if(s.versus.phase()==VersusMatch.Phase.FINISHED){
            settle(player,s,s.versus.winner()==0?"WON":s.versus.winner()==1?"LOST":"DRAW");return true;
        }
        return false;
    }
    private void resetDefusalRound(ServerPlayerEntity player,Session s)throws Exception{
        for(MobEntity actor:s.enemies){botOwners.remove(actor.getUuid());actor.discard();}
        for(MobEntity actor:s.allies){botOwners.remove(actor.getUuid());actor.discard();}
        s.enemies.clear();s.allies.clear();s.seats.clear();s.reloads.clear();s.reloadAmmo.clear();s.nextShots.clear();s.lastPaths.clear();s.botRespawns.clear();s.respawnTeams.clear();
        boolean newHalf=s.renderedRound>0&&s.defusal.attackingTeam()!=(s.renderedRound<=defusalRules.halfRounds()?0:1);
        if(s.renderedRound==0||s.respawnAt>=0||newHalf)issueVersusKit(player,s);
        for(int i=0;i<player.getInventory().size();i++)if(player.getInventory().getStack(i).isOf(Content.DATA_CORE))player.getInventory().setStack(i,ItemStack.EMPTY);
        if(s.player.equals(s.defusal.carrier())){
            ItemStack charge=new ItemStack(Content.DATA_CORE);charge.set(DataComponentTypes.CUSTOM_NAME,Text.translatable("svextract.versus.charge"));player.getInventory().setStack(8,charge);
        }
        player.changeGameMode(s.previousGameMode);rpg.restoreStats(player,true);if(!rpg.present())player.setHealth(player.getMaxHealth());player.getHungerManager().setFoodLevel(20);
        s.respawnAt=-1;s.humanChannelOrigin=null;s.humanPlantSite=-1;s.humanDefusing=false;s.droppedBomb=null;s.renderedRound=s.defusal.round();
        s.alliesPending=4;s.hostilesPending=5;frozen.add(s.player);
        int x=s.defusal.attackingTeam()==0?-27:27;teleport(player,raid,s.slot.originX+x,65,10,x<0?270:90,0);
        player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();sendHud(player,s,true);openVersusShop(player);
    }
    private boolean interactDefusal(ServerPlayerEntity player,Session s,BlockPos position){
        if(s.defusal==null||s.defusal.phase()!=DefusalMatch.Phase.LIVE||!s.defusal.alive(s.player)||player.squaredDistanceTo(Vec3d.ofCenter(position))>16)return false;
        for(int site=0;site<2;site++)if(bombSite(s,site).equals(position)){
            if(s.defusal.planted()&&site==s.defusal.site()&&s.defusal.team(s.player)!=s.defusal.attackingTeam())s.humanDefusing=true;
            else if(!s.defusal.planted()&&s.player.equals(s.defusal.carrier())&&player.getMainHandStack().isOf(Content.DATA_CORE)){s.humanDefusing=false;s.humanPlantSite=site;}
            else return false;
            s.humanChannelOrigin=player.getPos();return true;
        }
        return false;
    }
    private boolean tickDefusal(ServerPlayerEntity player,Session s){
        DefusalMatch match=s.defusal;if(match==null){failSafe(s.player,new IllegalStateException("Missing defusal controller"));return true;}
        if(tick-s.started>s.mode.timeoutSeconds()*20L){settle(player,s,"DRAW");return true;}
        UUID planter=null,defuser=null;int site=-1;
        if(s.humanChannelOrigin!=null){
            if(!match.alive(s.player)||player.squaredDistanceTo(s.humanChannelOrigin)>0.0625||player.hurtTime>0)s.humanChannelOrigin=null;
            else if(s.humanDefusing&&match.planted()&&player.squaredDistanceTo(Vec3d.ofCenter(bombSite(s,match.site())))<=16)defuser=s.player;
            else if(!s.humanDefusing&&s.humanPlantSite>=0&&player.squaredDistanceTo(Vec3d.ofCenter(bombSite(s,s.humanPlantSite)))<=16){planter=s.player;site=s.humanPlantSite;}
        }
        for(MobEntity actor:s.enemies){UUID seat=s.seats.get(actor.getUuid());if(seat==null||actor.hurtTime>0)continue;
            if(s.droppedBomb!=null&&actor.getPos().squaredDistanceTo(s.droppedBomb)<=4&&match.pickUpBomb(seat))s.droppedBomb=null;
            if(seat.equals(match.carrier()))for(int i=0;i<2;i++)if(actor.squaredDistanceTo(Vec3d.ofCenter(bombSite(s,i)))<=9){planter=seat;site=i;actor.getNavigation().stop();}
            if(match.planted()&&match.team(seat)!=match.attackingTeam()&&actor.squaredDistanceTo(Vec3d.ofCenter(bombSite(s,match.site())))<=9){defuser=seat;actor.getNavigation().stop();}
        }
        for(MobEntity actor:s.allies){UUID seat=s.seats.get(actor.getUuid());if(seat==null||actor.hurtTime>0)continue;
            if(s.droppedBomb!=null&&actor.getPos().squaredDistanceTo(s.droppedBomb)<=4&&match.pickUpBomb(seat))s.droppedBomb=null;
            if(seat.equals(match.carrier()))for(int i=0;i<2;i++)if(actor.squaredDistanceTo(Vec3d.ofCenter(bombSite(s,i)))<=9){planter=seat;site=i;actor.getNavigation().stop();}
            if(match.planted()&&match.team(seat)!=match.attackingTeam()&&actor.squaredDistanceTo(Vec3d.ofCenter(bombSite(s,match.site())))<=9){defuser=seat;actor.getNavigation().stop();}
        }
        if(s.droppedBomb!=null&&player.squaredDistanceTo(s.droppedBomb)<=4&&match.pickUpBomb(s.player)){
            s.droppedBomb=null;ItemStack charge=new ItemStack(Content.DATA_CORE);charge.set(DataComponentTypes.CUSTOM_NAME,Text.translatable("svextract.versus.charge"));player.getInventory().setStack(8,charge);player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();
        }
        int oldRound=match.round();DefusalMatch.Phase oldPhase=match.phase();match.tick(tick,planter,site,defuser);
        s.teamScore=match.score(0);s.enemyScore=match.score(1);
        if(match.phase()!=DefusalMatch.Phase.BUY)frozen.remove(s.player);
        if(match.round()!=oldRound)try{resetDefusalRound(player,s);}catch(Exception e){failSafe(s.player,e);return true;}
        if(oldPhase==DefusalMatch.Phase.BUY&&match.phase()==DefusalMatch.Phase.LIVE)teleport(player,raid,player.getX(),player.getY(),player.getZ(),player.getYaw(),player.getPitch());
        if(oldPhase!=match.phase()){
            player.sendMessage(Text.translatable("svextract.versus.round_state",match.round(),match.phase().name(),s.teamScore,s.enemyScore),false);
            if(match.phase()!=DefusalMatch.Phase.LIVE)for(MobEntity actor:java.util.stream.Stream.concat(s.enemies.stream(),s.allies.stream()).toList()){actor.setAiDisabled(true);actor.getNavigation().stop();}
        }
        if(match.phase()==DefusalMatch.Phase.FINISHED){settle(player,s,match.winner()==0?"WON":match.winner()==1?"LOST":"DRAW");return true;}
        return false;
    }
    private void openVersusShop(ServerPlayerEntity player){
        Session s=sessions.get(player.getUuid());if(s==null||s.defusal==null||!s.active)return;
        Map<String,Object> state=new LinkedHashMap<>();state.put("kind","versus_buy");state.put("cash",s.defusal.cash(s.player));state.put("round",s.defusal.round());state.put("phase",s.defusal.phase().name());state.put("alive",s.defusal.alive(s.player));state.put("products",matchShop);
        ServerPlayNetworking.send(player,new Protocol.LobbyState(new com.google.gson.Gson().toJson(state),true));
    }
    private void buyVersus(ServerPlayerEntity player,String selection){
        Session s=sessions.get(player.getUuid());if(s==null||s.defusal==null||locked(s.player))return;
        MatchPurchase product=matchShop.stream().filter(p->p.id().equals(selection)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown match purchase"));
        if(s.defusal.phase()!=DefusalMatch.Phase.BUY||!s.defusal.alive(s.player)||s.defusal.cash(s.player)<product.price())throw new IllegalArgumentException("Purchases require a living player, buy phase and sufficient match cash");
        try{
            boolean armor=product.id().equals("armor"),kit=product.id().equals("defuse_kit");
            if(kit&&(s.defusal.team(s.player)==s.defusal.attackingTeam()||s.defusal.hasKit(s.player)))throw new IllegalArgumentException("Defuse kits are available once per round to defenders");
            if(armor&&!player.getEquippedStack(EquipmentSlot.CHEST).isEmpty())throw new IllegalArgumentException("Chest armor is already equipped");
            ItemStack equipment;
            if(armor||kit){Identifier item=Identifier.of(product.gun());if(!net.minecraft.registry.Registries.ITEM.containsId(item))throw new IllegalStateException("Missing configured equipment");equipment=new ItemStack(net.minecraft.registry.Registries.ITEM.get(item));}
            else equipment=actorGuns.missionLoan(product.gun());
            ItemStack ammunition=ItemStack.EMPTY;
            if(!armor&&!kit){Identifier ammo=Identifier.of(product.ammo());if(!net.minecraft.registry.Registries.ITEM.containsId(ammo))throw new IllegalStateException("Missing configured Point Blank ammunition");ammunition=new ItemStack(net.minecraft.registry.Registries.ITEM.get(ammo),64);}
            if(!s.defusal.purchase(s.player,product.price()))throw new IllegalStateException("Purchase rejected; no equipment granted");
            if(armor)player.equipStack(EquipmentSlot.CHEST,equipment);
            else if(kit){s.defusal.giveKit(s.player);player.getInventory().setStack(3,equipment);}
            else{player.getInventory().setStack(0,equipment);player.getInventory().setStack(1,ammunition);}
            player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();openVersusShop(player);
        }catch(Exception e){player.sendMessage(Text.literal("Match purchase failed: "+e.getMessage()),false);}
    }
    private void issueVersusKit(ServerPlayerEntity player,Session s)throws Exception{
        player.getInventory().clear();
        player.getInventory().setStack(0,actorGuns.missionLoan(s.config.mission().botGun()));
        Identifier ammunition=Identifier.of("pointblank","ammo9mm");
        if(!net.minecraft.registry.Registries.ITEM.containsId(ammunition))throw new IllegalArgumentException("Point Blank 9mm ammunition is missing");
        player.getInventory().setStack(1,new ItemStack(net.minecraft.registry.Registries.ITEM.get(ammunition),64));
        player.getInventory().setStack(2,new ItemStack(net.minecraft.item.Items.COOKED_BEEF,8));
        player.equipStack(EquipmentSlot.CHEST,ItemStack.EMPTY);
        player.getInventory().selectedSlot=0;player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();rpg.restoreStats(player,true);
        if(!rpg.present())player.setHealth(player.getMaxHealth());
    }
    private void recover(ServerPlayerEntity player,RaidJournal.Entry entry){
        locked.add(player.getUuid());
        Session old=sessions.remove(player.getUuid());if(old!=null)dispose(old);
        if(entry.state()==RaidState.SETTLING)deliver(player,entry,0);
        else {
            byte[] restored=entry.reserved();
            if(entry.mode().equals("operations")&&entry.state()==RaidState.ACTIVE)try{restored=InventorySnapshot.withoutRaidLoot(entry.checkpoint()==null?entry.reserved():entry.checkpoint(),config.performance().maxSnapshotBytes(),entry.raid());}
            catch(Exception e){failSafe(entry.player(),e);return;}
            watch(journal.prepareSettlement(entry.raid(),restored,"RECOVERED"),e->{
            ServerPlayerEntity online=server.getPlayerManager().getPlayer(entry.player());if(online!=null)deliver(online,e,0);
        },e->failSafe(entry.player(),e));
        }
    }
    private void deliver(ServerPlayerEntity player,RaidJournal.Entry entry,int completed){
        if(!havenReady||(entry.home().world().equals(SKYRIM.getValue().toString())&&!skyrimReady)){deferredDeliveries.put(player.getUuid(),new Delivery(entry,completed));return;}
        try {
            player.closeHandledScreen();
            clearCompanionVisual(sessions.get(player.getUuid()));
            cobblemon.recallParty(player);
            InventorySnapshot.restore(player,entry.settlement(),config.performance().maxSnapshotBytes());
            Session session=sessions.get(player.getUuid());if(session!=null&&session.previousGameMode!=null)player.changeGameMode(session.previousGameMode);
            ServerWorld destination=entry.home().world().equals(SKYRIM.getValue().toString())?skyrim:haven;
            if(destination==null||(destination==skyrim&&!skyrimReady))throw new IllegalStateException("Recorded return hub unavailable; inventory remains protected");
            teleport(player,destination,entry.home().x(),entry.home().y(),entry.home().z(),entry.home().yaw(),entry.home().pitch());
            String returnDimension=destination.getRegistryKey().getValue().toString();
            rpg.restoreStats(player,true);
            if(!rpg.present())player.setHealth(player.getMaxHealth());player.getHungerManager().setFoodLevel(20);
            // Player-data save precedes journal close. Until close succeeds all external interactions remain frozen.
            // Crash replay replaces the entire protected inventory rather than adding another reward.
            ((dev.svextract.mixin.PlayerManagerAccess)server.getPlayerManager()).svextract$savePlayerData(player);
            watch(journal.closeVerified(entry.raid(),verifyInventorySave(player,entry.settlement(),returnDimension)),closed->{
                durablePending=Math.max(0,durablePending-1);
                Session s=sessions.remove(entry.player());if(s!=null)dispose(s);
                recoveryReady.add(entry.player());
                ServerPlayerEntity online=server.getPlayerManager().getPlayer(entry.player());
                if(online!=null&&verified.contains(entry.player())){
                    boolean risk=gameModes.all().stream().anyMatch(mode->mode.id().equals(closed.mode())&&mode.equipmentRisk());
                    locked.remove(entry.player());ServerPlayNetworking.send(online,new Protocol.Result(closed.outcome(),completed,closed.mode(),risk,s==null?0:s.teamScore,s==null?0:s.enemyScore));
                    companions.release(entry.player());
                    ServerPlayNetworking.send(online,new Protocol.Hud("",0,0,0,""));
                }
            },e->failSafe(entry.player(),e));
        }catch(Exception e){failSafe(entry.player(),e);}
    }
    private void recoverStash(ServerPlayerEntity player){
        UUID id=player.getUuid();
        watch(journal.liveStashTransfer(id),pending->{
            ServerPlayerEntity online=server.getPlayerManager().getPlayer(id);if(online==null)return;
            if(pending.isPresent()){
                if(stashInFlight.add(id))deliverStash(online,pending.get(),0);
            }else if(!stashInFlight.contains(id)){
                recoveryReady.add(id);if(verified.contains(id))locked.remove(id);
            }
        },e->failSafe(id,e));
    }
    private void stashError(ServerPlayerEntity player,String message){
        String text=message==null?"Storage unavailable; reconnect to recover":message;
        ServerPlayNetworking.send(player,new Protocol.StashState(-1,0,stashConfig.slots()/27,new byte[0],text.substring(0,Math.min(160,text.length()))));
    }
    private void sendStash(ServerPlayerEntity player,int page,String status){
        UUID id=player.getUuid();
        try{
            watch(journal.stash(id,InventorySnapshot.empty(config.performance().maxSnapshotBytes())),snapshot->{
                ServerPlayerEntity online=server.getPlayerManager().getPlayer(id);
                if(online==null||!verified.contains(id)||restricted(id)||!isHub(online.getServerWorld()))return;
                try{
                    byte[] items=dev.svextract.persistence.StashInventory.page(snapshot.items(),page,stashConfig.slots(),config.performance().maxSnapshotBytes());
                    ServerPlayNetworking.send(online,new Protocol.StashState(snapshot.revision(),page,stashConfig.slots()/27,items,status));telemetry.packet(items.length+192);
                }catch(Exception e){stashError(online,e.getMessage());}
            },e->{ServerPlayerEntity online=server.getPlayerManager().getPlayer(id);if(online!=null)stashError(online,"Storage read failed; no items moved");});
        }catch(Exception e){stashError(player,e.getMessage());}
    }
    public void stashAction(ServerPlayerEntity player,Protocol.StashAction action){
        UUID id=player.getUuid();
        if(!verified.contains(id))return;
        if(action.action()<0||action.action()>2||action.page()<0||action.page()>=stashConfig.slots()/27||action.revision()<0)return;
        Long previous=stashRequests.get(id);if(previous!=null&&tick-previous<stashConfig.requestIntervalTicks()){stashError(player,"Please wait before the next storage request");return;}
        stashRequests.put(id,tick);
        if(restricted(id)||!isHub(player.getServerWorld())){stashError(player,"Storage is available in the protected hub after recovery finishes");return;}
        if(action.action()==0){sendStash(player,action.page(),"");return;}
        if(stashInFlight.size()>=stashConfig.maxConcurrentTransfers()){stashError(player,"Storage is busy saving another transfer; try again shortly");return;}
        if(action.slot()<0||action.slot()>=(action.action()==1?36:27)){stashError(player,"Invalid inventory slot");return;}
        if(!player.currentScreenHandler.getCursorStack().isEmpty()){stashError(player,"Put the item on your cursor away first");return;}
        player.closeHandledScreen();locked.add(id);stashInFlight.add(id);
        try{
            byte[] before=InventorySnapshot.capture(player,config.performance().maxSnapshotBytes());
            String dimension=player.getServerWorld().getRegistryKey().getValue().toString();
            watch(journal.stash(id,InventorySnapshot.empty(config.performance().maxSnapshotBytes())),snapshot->{
                try{
                    if(snapshot.revision()!=action.revision())throw new IllegalStateException("Storage changed; refresh before transferring");
                    var move=dev.svextract.persistence.StashInventory.move(before,snapshot.items(),action.action()==1,action.action()==1?action.slot():action.page()*27+action.slot(),stashConfig.slots(),config.performance().maxSnapshotBytes());
                    watch(journal.prepareStashTransfer(UUID.randomUUID(),id,action.revision(),before,move.inventory(),move.stash(),dimension),transfer->{
                        durablePending++;
                        ServerPlayerEntity online=server.getPlayerManager().getPlayer(id);
                        if(online!=null)deliverStash(online,transfer,action.page());else stashInFlight.remove(id);
                    },e->rejectStash(id,"Storage could not be reserved; no items moved"));
                }catch(Exception e){rejectStash(id,e.getMessage());}
            },e->rejectStash(id,"Storage read failed; no items moved"));
        }catch(Exception e){rejectStash(id,e.getMessage());}
    }
    private void rejectStash(UUID id,String message){
        stashInFlight.remove(id);recoveryReady.add(id);if(verified.contains(id))locked.remove(id);
        ServerPlayerEntity online=server.getPlayerManager().getPlayer(id);if(online!=null)stashError(online,message);
    }
    private RaidJournal.SaveVerification verifyInventorySave(ServerPlayerEntity player,byte[] expected,String dimension){
        Path file=server.getSavePath(WorldSavePath.PLAYERDATA).resolve(player.getUuid()+".dat");int limit=config.performance().maxSnapshotBytes();
        return ()->{
            if(Files.size(file)>limit*4L)throw new java.io.IOException("Player save exceeds verification budget");
            InventorySnapshot.verifyPlayerFile(Files.readAllBytes(file),expected,dimension,limit);
            try(var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.WRITE)){channel.force(true);}
            if(!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows"))
                try(var directory=java.nio.channels.FileChannel.open(file.getParent(),StandardOpenOption.READ)){directory.force(true);}
        };
    }
    private void deliverStash(ServerPlayerEntity player,RaidJournal.StashTransfer transfer,int page){
        UUID id=transfer.player();locked.add(id);
        try{
            if(transfer.closed())throw new IllegalStateException("Refusing to replay a completed storage transfer");
            if(!player.getServerWorld().getRegistryKey().getValue().toString().equals(transfer.dimension())){
                if(transfer.dimension().equals(HAVEN.getValue().toString())&&havenReady)teleport(player,haven,0,65,4,180,0);
                else if(transfer.dimension().equals(SKYRIM.getValue().toString())&&skyrimReady)teleport(player,skyrim,skyrimX,skyrimY,skyrimZ,180,0);
                else throw new IllegalStateException("Recorded storage hub is unavailable; recovery retained");
            }
            InventorySnapshot.restore(player,transfer.afterInventory(),config.performance().maxSnapshotBytes());
            ((dev.svextract.mixin.PlayerManagerAccess)server.getPlayerManager()).svextract$savePlayerData(player);
            watch(journal.closeStashTransfer(transfer.id(),verifyInventorySave(player,transfer.afterInventory(),transfer.dimension())),closed->{
                durablePending=Math.max(0,durablePending-1);stashInFlight.remove(id);recoveryReady.add(id);
                ServerPlayerEntity online=server.getPlayerManager().getPlayer(id);
                if(online!=null&&verified.contains(id)){locked.remove(id);sendStash(online,page,"");}
            },e->{stashInFlight.remove(id);failSafe(id,e);ServerPlayerEntity online=server.getPlayerManager().getPlayer(id);if(online!=null)stashError(online,"Save verification failed; inventory locked. Reconnect for recovery");});
        }catch(Exception e){stashInFlight.remove(id);failSafe(id,e);stashError(player,"Storage delivery pending; reconnect for recovery");}
    }
    private void dispose(Session s){
        frozen.remove(s.player);s.seats.clear();
        clearCompanionVisual(s);
        for(MobEntity entity:s.enemies){botOwners.remove(entity.getUuid());if(!entity.isRemoved())entity.discard();}
        for(MobEntity entity:s.allies){botOwners.remove(entity.getUuid());if(!entity.isRemoved())entity.discard();}
        s.enemies.clear();s.allies.clear();s.reloads.clear();s.reloadAmmo.clear();s.nextShots.clear();s.lastPaths.clear();s.botRespawns.clear();s.respawnTeams.clear();
        s.companionGun=ItemStack.EMPTY;companions.release(s.player);pool.cleanup(s.slot);
    }
    public void loot(ServerPlayerEntity player){
        Session s=sessions.get(player.getUuid());if(s==null||!s.active||locked(player.getUuid())||!allowReadCommand(player))return;
        for(var cache:lootConfig.caches())if(player.squaredDistanceTo(Vec3d.ofCenter(s.slot.at(cache.x(),cache.y(),cache.z())))<=25){openLoot(player,s,cache);return;}
        List<NbtCompound> carried=carriedLoot(player,s);player.sendMessage(Text.translatable("svextract.loot.carried",carried.size()),false);
        for(var encoded:carried)player.sendMessage(Text.literal(encoded.getString("id")+" × "+encoded.getInt("count")),false);
    }
    private List<NbtCompound> carriedLoot(ServerPlayerEntity player,Session s){
        List<NbtCompound> carried=new ArrayList<>();Map<UUID,Integer> counts=new HashMap<>();
        for(int slot=0;slot<player.getInventory().size();slot++)collectOwnedLoot(player.getInventory().getStack(slot),s,carried,counts);
        collectOwnedLoot(s.companionGun,s,carried,counts);return carried;
    }
    private void collectOwnedLoot(ItemStack stack,Session s,List<NbtCompound> collected,Map<UUID,Integer> counts){
        if(stack.isEmpty())return;NbtCompound identity=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
        if(!identity.containsUuid("svextract_item")||!identity.containsUuid("svextract_raid")||!s.raid.equals(identity.getUuid("svextract_raid")))return;
        UUID id=identity.getUuid("svextract_item");LootMint mint=s.lootIdentities.get(id);if(mint==null)return;
        int total=counts.merge(id,stack.getCount(),Integer::sum);
        if(total>mint.count()||!net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString().equals(mint.item()))throw new IllegalStateException("Raid loot ownership/count mismatch; delivery remains protected");
        collected.add((NbtCompound)stack.encode(server.getRegistryManager()));
    }
    private boolean lobbyAction(ServerPlayerEntity player){
        if(!allowReadCommand(player))return false;
        if(!lobbyReady||lobbyWritePending){player.sendMessage(Text.literal("A squad update is in progress. Try again shortly."),false);return false;}
        if(restricted(player.getUuid())||!isHub(player.getServerWorld())){player.sendMessage(Text.literal("Manage your squad from Mission Control in a protected hub."),false);return false;}return true;
    }
    public void invite(ServerPlayerEntity leader,ServerPlayerEntity target){
        if(!lobbyAction(leader))return;
        if(restricted(target.getUuid())||!isHub(target.getServerWorld())){leader.sendMessage(Text.literal("That player must return to a protected hub first."),false);return;}
        try{
            lobby.invite(leader.getUuid(),target.getUuid(),System.nanoTime());
            target.sendMessage(Text.literal(leader.getName().getString()+" invited you to a squad. Accept within two minutes: /svextract squad accept").styled(s->s.withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND,"/svextract squad accept"))),false);
            leader.sendMessage(Text.literal("Squad invitation sent to "+target.getName().getString()),false);
        }catch(Exception e){leader.sendMessage(Text.literal(e.getMessage()),false);}
    }
    public void acceptInvite(ServerPlayerEntity player){
        if(!lobbyAction(player))return;
        try{
            UUID inviter=lobby.inviter(player.getUuid(),System.nanoTime());ServerPlayerEntity leader=server.getPlayerManager().getPlayer(inviter);
            if(leader==null||restricted(inviter)||!isHub(leader.getServerWorld())){player.sendMessage(Text.literal("The inviter must be online in a protected hub before acceptance."),false);return;}
            saveLobby(player,lobby.accept(player.getUuid(),System.nanoTime()));
        }catch(Exception e){player.sendMessage(Text.literal(e.getMessage()),false);}
    }
    public void leaveSquad(ServerPlayerEntity player){
        if(!lobbyAction(player))return;try{saveLobby(player,lobby.leave(player.getUuid()));}catch(Exception e){player.sendMessage(Text.literal(e.getMessage()),false);}
    }
    private void saveLobby(ServerPlayerEntity player,RaidJournal.Lobby proposal){
        lobbyWritePending=true;UUID changed=player.getUuid();
        watch(journal.saveLobby(proposal),state->{lobby.committed(state,changed);lobbyWritePending=false;ServerPlayerEntity online=server.getPlayerManager().getPlayer(changed);if(online!=null)online.sendMessage(Text.literal("Squad membership saved. It survives disconnects and server restarts."),false);},e->{lobbyWritePending=false;SvExtract.LOGGER.error("Squad change rejected; durable membership retained",e);ServerPlayerEntity online=server.getPlayerManager().getPlayer(changed);if(online!=null)online.sendMessage(Text.literal("Squad could not be saved. Previous membership retained."),false);});
    }
    public void squadStatus(ServerPlayerEntity player){
        if(!allowReadCommand(player))return;
        if(!lobbyReady){player.sendMessage(Text.literal("Lobby membership is loading."),false);return;}
        var squad=lobby.squad(player.getUuid());if(squad.isEmpty()){player.sendMessage(Text.literal("No squad. Invite someone with /svextract squad invite <player>."),false);return;}
        for(UUID id:squad.get().members()){ServerPlayerEntity online=server.getPlayerManager().getPlayer(id);String name=online==null?id.toString():online.getName().getString();player.sendMessage(Text.literal((id.equals(squad.get().leader())?"Leader: ":"Member: ")+name+(online==null?" (offline, reconnect reserved)":" (online)")),false);}
    }

    public void endTick(){
        tick++;
        if(tick%20==0)lobby.expire(System.nanoTime());
        for(int i=0,done=0;i<work.size()&&done<config.performance().completionsPerTick();){
            Work<?> job=work.get(i);if(job.future.isDone()){work.remove(i);job.finish();done++;}else i++;
        }
        Iterator<Map.Entry<UUID,Long>> hi=handshakes.entrySet().iterator();
        while(hi.hasNext()){
            Map.Entry<UUID,Long> e=hi.next();if(tick-e.getValue()>config.performance().handshakeTimeoutTicks()){
                hi.remove();ServerPlayerEntity p=server.getPlayerManager().getPlayer(e.getKey());
                if(p!=null)p.networkHandler.disconnect(Text.literal("SVEXTRACT client handshake timed out. Install the matching client mod."));
            }
        }
        if(!hubPreparationFailed)try(WorldMutationScope ignored=WorldMutationScope.enter()){
            if(!havenReady)prepareHaven();else if(skyrimInstalled&&!skyrimReady)prepareSkyrim();
        }catch(Exception failure){
            hubPreparationFailed=true;if(havenPreparation!=null)havenPreparation.close();if(skyrimPreparation!=null)skyrimPreparation.close();
            SvExtract.LOGGER.error("Hub preparation failed; affected hub admissions remain unavailable and pending equipment stays protected",failure);
        }
        if(havenReady&&(!skyrimInstalled||skyrimReady)&&!deferredDeliveries.isEmpty()){
            int delivered=0;
            for(UUID id:List.copyOf(deferredDeliveries.keySet())){
                if(delivered++>=config.performance().completionsPerTick())break;
                Delivery d=deferredDeliveries.remove(id);ServerPlayerEntity p=server.getPlayerManager().getPlayer(id);
                if(p!=null)deliver(p,d.entry,d.objectives);
            }
        }
        if(tick%20==0)recentP95=telemetry.snapshot().p95Ms();
        try(WorldMutationScope ignored=WorldMutationScope.enter()){pool.tick(havenReady&&recentP95<=config.performance().admissionP95Ms());}
        int spawnBudget=config.performance().spawnRequestsPerTick();
        for(Session s:List.copyOf(sessions.values())){
            if(!s.active&&s.entry!=null&&s.entry.state()==RaidState.RESERVED){
                if(!s.activationRequested&&(s.slot.state==InstancePool.State.FAILED||tick-s.started>s.config.performance().instancePreparationTimeoutTicks())){
                    s.activationRequested=true;
                    watch(journal.prepareSettlement(s.raid,s.entry.reserved(),"ABORTED"),entry->{s.entry=entry;ServerPlayerEntity online=server.getPlayerManager().getPlayer(s.player);if(online!=null)deliver(online,entry,0);else{sessions.remove(s.player);dispose(s);}},e->failSafe(s.player,e));
                }else activatePrepared(s);continue;
            }
            if(!s.active)continue;
            ServerPlayerEntity player=server.getPlayerManager().getPlayer(s.player);
            if(player==null){if(s.offlineUntil>=0&&tick>=s.offlineUntil)expireDisconnected(s);continue;}
            if(s.offlineUntil>=0)continue;
            if(player.getWorld()!=raid){settle(player,s,"ABORTED");continue;}
            if(!s.slot.contains(player.getX(),player.getY(),player.getZ()))teleport(player,raid,s.slot.originX,65,0,0,0);
            if(s.mode.id().equals("versus")){
                if(s.defusal!=null?tickDefusal(player,s):tickTeamVersus(player,s))continue;
            }else if(tick-s.started>s.mode.timeoutSeconds()*20L){settle(player,s,"TIMEOUT");continue;}
            if(s.extractAt>=0){
                if(s.extractionPosition==null||player.getPos().squaredDistanceTo(Vec3d.ofCenter(s.extractionPosition))>49){s.extractAt=-1;message(player,"svextract.extraction.cancelled");}
                else if(!s.mode.optionalObjectives()&&!hasCore(player,s)){s.extractAt=-1;message(player,"svextract.error.core");}
                else if(tick>=s.extractAt){s.graph.complete("evacuation");settle(player,s,"EXTRACTED");continue;}
            }
            // Landmark awareness is bounded to one small, prevalidated list every 40 ticks.
            // Discoveries encourage exploration without making optional POIs mandatory objectives.
            if(!s.mode.id().equals("versus")&&tick%40==0){
                double localX=player.getX()-s.slot.originX,localZ=player.getZ();
                for(RaidLandmarks.Landmark landmark:RaidLandmarks.forMap(s.slot.map)){
                    if(s.discoveredLandmarks.contains(landmark.id())||!landmark.contains(localX,localZ))continue;
                    s.discoveredLandmarks.add(landmark.id());
                    player.sendMessage(Text.translatable("svextract.poi.discovered",Text.translatable("svextract.poi."+landmark.id())),true);
                    if(landmark.highRisk())s.threat=Math.min(5,s.threat+1);
                    sendHud(player,s,true);
                    break;
                }
            }
            boolean fighting=s.defusal!=null?s.defusal.phase()==DefusalMatch.Phase.LIVE:
                s.versus==null||s.versus.phase()==VersusMatch.Phase.ACTIVE;
            if(fighting&&tick%s.config.performance().aiDecisionIntervalTicks()==0)updateEnemies(player,s);
            if(fighting)updateActorGuns(player,s);
            if(spawnBudget>0&&s.alliesPending>0){
                if(s.mode.id().equals("versus")){if(spawnCombatBot(s,true))s.alliesPending--;}
                else{spawnAlly(s);s.alliesPending--;}spawnBudget--;
            }
            if(spawnBudget>0&&s.hostilesPending>0){
                if(s.mode.id().equals("versus")){if(spawnCombatBot(s,false))s.hostilesPending--;}
                else if(tick>=s.nextEnemySpawnAt){if(spawnEnemy(player,s))s.hostilesPending--;s.nextEnemySpawnAt=tick+10;}
                spawnBudget--;
            }
            if(!s.mode.id().equals("versus")&&s.hostilesPending==0&&spawnBudget>0
                &&tick>=s.nextEnemySpawnAt&&tick%s.config.mission().enemySpawnIntervalTicks()==0){
                spawnEnemy(player,s);s.nextEnemySpawnAt=tick+10;spawnBudget--;
            }
            if(s.mode.equipmentRisk()&&!locked(s.player)&&tick-s.lastCheckpoint>=s.mode.checkpointTicks())try{checkpoint(player,s);}catch(Exception e){failSafe(s.player,e);}
            if(tick%s.config.performance().hudIntervalTicks()==0)sendHud(player,s,false);
        }
    }
    public void recordTick(long duration,long interval){telemetry.tick(duration,interval);}
    public void recordPointBlankRequest(long nanos){telemetry.pointBlankRequest(nanos);}
    private void updateEnemies(ServerPlayerEntity player,Session s){
        long start=System.nanoTime();int paths=0;
        s.enemies.removeIf(e->forgetDeadActor(e,s));s.allies.removeIf(e->forgetDeadActor(e,s));
        if(s.mode.id().equals("versus")){
            int total=s.enemies.size()+s.allies.size();
            for(MobEntity actor:s.enemies){actor.setAiDisabled(false);actor.setTarget(nearestTarget(actor,player,s,true));}
            for(MobEntity actor:s.allies){actor.setAiDisabled(false);actor.setTarget(nearestTarget(actor,player,s,false));}
            int budget=Math.min(s.config.performance().actorShotRequestsPerDecision(),total);
            for(int i=0;i<budget;i++){
                int index=s.navigationCursor++%total;MobEntity actor=index<s.enemies.size()?s.enemies.get(index):s.allies.get(index-s.enemies.size());
                LivingEntity target=actor.getTarget();
                Vec3d objective=defusalObjective(s,s.seats.get(actor.getUuid()));
                if(objective!=null&&(target==null||!actor.canSee(target))&&actor.squaredDistanceTo(objective)>4){
                    if(actor.getNavigation().isIdle()||s.lastPaths.get(actor.getUuid())==null||s.lastPaths.get(actor.getUuid()).squaredDistanceTo(objective)>4){actor.getNavigation().startMovingTo(objective.x,objective.y,objective.z,1.0);s.lastPaths.put(actor.getUuid(),objective);paths++;}continue;
                }
                if(target==null||!target.isAlive())continue;
                Vec3d previous=s.lastPaths.get(actor.getUuid());
                if(actor.squaredDistanceTo(target)>36&&(actor.getNavigation().isIdle()||previous==null||previous.squaredDistanceTo(target.getPos())>16)){
                    actor.getNavigation().startMovingTo(target,1.0);s.lastPaths.put(actor.getUuid(),target.getPos());paths++;
                }
            }
            telemetry.ai(System.nanoTime()-start,paths);return;
        }
        double range=s.config.mission().activationRadius();
        int patrolBudget=tick%Math.max(100,s.config.performance().aiDecisionIntervalTicks()*5)==0?2:0;
        for(MobEntity e:s.enemies){
            boolean near=e.squaredDistanceTo(player)<=range*range;
            e.setAiDisabled(!near);
            if(near && e.canSee(player))e.setTarget(player);
            else if(near&&patrolBudget>0&&e.getTarget()==null&&e.getNavigation().isIdle()){
                // Idle guards move between local POIs instead of waiting motionless
                // for the player. At most two route requests every five seconds.
                RaidPatrols.Point waypoint=RaidPatrols.next(s.slot.map,e.getX()-s.slot.originX,e.getZ());
                if(waypoint!=null){
                    BlockPos at=s.slot.at(waypoint.x(),65,waypoint.z());
                    if(raid.getBlockState(at).isAir()&&raid.getBlockState(at.up()).isAir()
                        &&raid.getBlockState(at.down()).isSolidBlock(raid,at.down())){
                        if(e.getNavigation().startMovingTo(at.getX()+0.5,65,at.getZ()+0.5,0.85)){paths++;patrolBudget--;}
                    }
                }
            }
            if(!s.slot.contains(e.getX(),e.getY(),e.getZ()))e.discard();
        }
        for(MobEntity ally:s.allies){
            ally.setAiDisabled(false);
            if(!s.slot.contains(ally.getX(),ally.getY(),ally.getZ())){ally.discard();continue;}
            MobEntity target=null;double nearest=256;
            for(MobEntity enemy:s.enemies){double d=ally.squaredDistanceTo(enemy);if(d<nearest&&enemy.isAlive()){nearest=d;target=enemy;}}
            ally.setTarget(target);
            if(target==null&&ally.squaredDistanceTo(player)>36){ally.getNavigation().startMovingTo(player,0.9);paths++;}
        }
        telemetry.ai(System.nanoTime()-start,paths);
    }
    private boolean forgetDeadActor(MobEntity actor,Session s){
        if(actor.isAlive()&&!actor.isRemoved())return false;
        botOwners.remove(actor.getUuid());s.reloads.remove(actor.getUuid());s.reloadAmmo.remove(actor.getUuid());s.nextShots.remove(actor.getUuid());s.lastPaths.remove(actor.getUuid());return true;
    }
    private LivingEntity nearestTarget(LivingEntity actor,ServerPlayerEntity player,Session s,boolean hostile){
        LivingEntity target=null;double radius=s.mode.id().equals("versus")?80:s.config.mission().activationRadius();double nearest=radius*radius;
        if(hostile&&s.respawnAt<0&&!player.isSpectator()){double distance=actor.squaredDistanceTo(player);if(distance<nearest){target=player;nearest=distance;}}
        for(MobEntity candidate:hostile?s.allies:s.enemies){double distance=actor.squaredDistanceTo(candidate);if(candidate.isAlive()&&distance<nearest){target=candidate;nearest=distance;}}
        return target;
    }
    private boolean spawnCombatBot(Session s,boolean friendly){
        int index=friendly?s.allies.size():s.enemies.size();int teamX=friendly?-26:27;
        if(s.defusal!=null&&s.defusal.attackingTeam()==1)teamX=-teamX;
        for(int attempt=0;attempt<6;attempt++){
            BlockPos position=s.slot.at(teamX,65,-18+((index+attempt)*7)%36);
            if(!raid.getBlockState(position).isAir()||!raid.getBlockState(position.up()).isAir()||!raid.getBlockState(position.down()).isSolidBlock(raid,position.down()))continue;
            CombatBotEntity bot=Content.COMBAT_BOT.create(raid);if(bot==null)return false;
            bot.refreshPositionAndAngles(position, friendly?270:90,0);bot.setPersistent();bot.setCanPickUpLoot(false);
            bot.addCommandTag(friendly?"svextract_ally":"svextract_enemy");bot.addCommandTag(entityEpoch);bot.addCommandTag("svextract_bot");
            bot.setCustomName(Text.literal("BOT "+(friendly?"AEGIS ":"VEIL ")+(index+1)));bot.setCustomNameVisible(true);
            try{
                ItemStack weapon=actorGuns.missionLoan(s.config.mission().botGun());bot.equipStack(EquipmentSlot.MAINHAND,weapon);bot.setEquipmentDropChance(EquipmentSlot.MAINHAND,0);
                s.reloadAmmo.put(bot.getUuid(),actorGuns.remainingAmmo(weapon)*3);
            }catch(Exception e){SvExtract.LOGGER.error("Bot weapon initialization rejected",e);return false;}
            if(!raid.spawnEntity(bot))return false;
            if(friendly)s.allies.add(bot);else s.enemies.add(bot);botOwners.put(bot.getUuid(),s);
            if(s.defusal!=null||s.versus!=null){
                List<UUID> candidates=friendly?s.alliedSeats:s.hostileSeats;
                UUID free=candidates.stream().filter(id->!s.seats.containsValue(id)&&!s.botRespawns.containsKey(id)).findFirst().orElse(null);
                if(free==null){botOwners.remove(bot.getUuid());(friendly?s.allies:s.enemies).remove(bot);bot.discard();return false;}
                s.seats.put(bot.getUuid(),free);
            }
            return true;
        }
        return false;
    }
    private void spawnAlly(Session s){
            int i=s.allies.size();IronGolemEntity ally=EntityType.IRON_GOLEM.create(raid);if(ally==null)return;
            ally.refreshPositionAndAngles(s.slot.originX+3+i*3,65,2,0,0);ally.setPlayerCreated(true);ally.setPersistent();
            ally.addCommandTag("svextract_ally");ally.addCommandTag(entityEpoch);ally.setCustomName(Text.translatable("svextract.bot.aegis"));ally.setCustomNameVisible(true);
            try{if(actorGuns.ready()){ally.equipStack(EquipmentSlot.MAINHAND,actorGuns.missionLoan(s.config.mission().botGun()));s.reloadAmmo.put(ally.getUuid(),actorGuns.remainingAmmo(ally.getMainHandStack())*3);}}
            catch(Exception e){SvExtract.LOGGER.warn("Escort firearm unavailable; escort keeps its normal melee behavior",e);}
            if(raid.spawnEntity(ally)){s.allies.add(ally);botOwners.put(ally.getUuid(),s);}
    }
    private boolean spawnEnemy(ServerPlayerEntity player,Session s){
        if(s.enemies.size()>=Math.min(s.mode.hostileBots(),s.config.performance().maxEnemiesPerInstance()))return false;
        // Spread enemies across authored patrol circuits rather than two fixed
        // vertical spawn lines. Never consume a failed spawn request.
        List<RaidPatrols.Point> candidates=RaidPatrols.spawnPoints(s.slot.map);
        if(candidates.isEmpty())return false;
        for(int attempt=0;attempt<Math.min(16,candidates.size());attempt++){
            RaidPatrols.Point waypoint=candidates.get((s.enemySpawnCursor++)%candidates.size());
            BlockPos pos=s.slot.at(waypoint.x(),65,waypoint.z());
            if(player.squaredDistanceTo(Vec3d.ofCenter(pos))<144)continue;
            if(!raid.getBlockState(pos).isAir()||!raid.getBlockState(pos.up()).isAir()||!raid.getBlockState(pos.down()).isSolidBlock(raid,pos.down()))continue;
            MobEntity enemy;
            try{enemy=mobs.present()?mobs.spawn(raid,Vec3d.ofBottomCenter(pos)):EntityType.ZOMBIE.create(raid);}
            catch(Exception e){SvExtract.LOGGER.error("Configured enemy spawn failed",e);return false;}
            if(enemy==null)continue;
            enemy.refreshPositionAndAngles(pos,0,0);enemy.setPersistent();enemy.setCanPickUpLoot(false);
            enemy.addCommandTag("svextract_enemy");enemy.addCommandTag(entityEpoch);enemy.setCustomName(Text.translatable("svextract.enemy.fractured"));
            try{if(actorGuns.ready()){enemy.equipStack(EquipmentSlot.MAINHAND,actorGuns.missionLoan(s.config.mission().botGun()));s.reloadAmmo.put(enemy.getUuid(),actorGuns.remainingAmmo(enemy.getMainHandStack())*3);}}
            catch(Exception e){SvExtract.LOGGER.warn("Configured enemy firearm unavailable; enemy retains melee behavior",e);}
            if(mobs.present()||raid.spawnEntity(enemy)){
                s.enemies.add(enemy);botOwners.put(enemy.getUuid(),s);return true;
            }
        }
        return false;
    }
    public void equipCompanion(ServerPlayerEntity player){
        Session s=sessions.get(player.getUuid());
        if(s==null||!s.active||locked(player.getUuid())){message(player,"svextract.error.busy");return;}
        if(companions.selected(player.getUuid())==null){player.sendMessage(Text.literal("Deploy with one owned Pokémon before assigning it a firearm."),false);return;}
        if(!s.companionGun.isEmpty()){player.sendMessage(Text.literal("Your companion already carries a firearm for this mission."),false);return;}
        try{
            ItemStack weapon=player.getMainHandStack();
            if(!actorGuns.supports(weapon)){player.sendMessage(Text.literal("Hold a nonexplosive, single-pellet Point Blank firearm. This initial companion adapter supports hitscan modes."),false);return;}
            s.companionGun=weapon;player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.getInventory().markDirty();
            player.sendMessage(Text.literal("Firearm assigned to your Pokémon. It uses its existing magazine; recall it and extract to recover the original training loadout."),false);
        }catch(Exception e){SvExtract.LOGGER.error("Companion firearm assignment rejected",e);}
    }
    private void updateActorGuns(ServerPlayerEntity player,Session s){
        if(!actorGuns.ready())return;
        try{
            LivingEntity companion=s.visualCompanion;
            if(tick%s.config.performance().aiDecisionIntervalTicks()==0||companion!=null&&companion.isRemoved())companion=cobblemon.companionEntity(player,companions.selected(s.player));
            if(companion!=null&&!s.companionGun.isEmpty()){
                if(s.visualCompanion!=companion){clearCompanionVisual(s);s.visualCompanion=companion;companion.getClass().getMethod("setShownItem",ItemStack.class).invoke(companion,s.companionGun.copy());}
            }else companion=null;
            int total=s.enemies.size()+s.allies.size()+(companion==null?0:1);
            int budget=Math.min(s.config.performance().actorShotRequestsPerDecision(),total);
            for(int processed=0;processed<budget;processed++){
                int enemies=s.enemies.size(),allies=s.allies.size();total=enemies+allies+(companion==null?0:1);if(total==0)break;
                int index=s.gunCursor%total;s.gunCursor=(index+1)%total;
                LivingEntity actor=index<enemies?s.enemies.get(index):index<enemies+allies?s.allies.get(index-enemies):companion;
                if(!actor.isAlive()||s.nextShots.getOrDefault(actor.getUuid(),0L)>tick||s.defusal!=null&&s.defusal.channeling(s.seats.get(actor.getUuid())))continue;
                boolean hostile=index<enemies;LivingEntity target=nearestTarget(actor,player,s,hostile);
                if(target==null)continue;
                ItemStack weapon=actor==companion?s.companionGun:actor.getMainHandStack();
                if(!actorGuns.supports(weapon))continue;
                if(actorGuns.remainingAmmo(weapon)==0){
                    int reserve=s.reloadAmmo.getOrDefault(actor.getUuid(),0);if(reserve<=0)continue;
                    long complete=s.reloads.computeIfAbsent(actor.getUuid(),id->tick+s.mode.botReloadSeconds()*20L);
                    if(tick<complete)continue;
                    int supplied=actorGuns.reload(weapon,reserve);s.reloadAmmo.put(actor.getUuid(),reserve-supplied);s.reloads.remove(actor.getUuid());actor.swingHand(Hand.MAIN_HAND);
                }
                s.nextShots.put(actor.getUuid(),tick+(long)s.config.mission().actorShotIntervalTicks()*(3-s.difficulty)/2);
                float previousHealth=target.getHealth();
                if(actorGuns.fire(actor,target,weapon,s.config.mission().actorGunRange())){s.threat=Math.min(5,s.threat+1);telemetry.actorShot(hostile,actor==companion,target.getHealth()<previousHealth);}
            }
        }catch(Exception e){SvExtract.LOGGER.error("Actor gun processing failed; no substitute damage issued",e);}
    }
    private void clearCompanionVisual(Session s){
        if(s==null||s.visualCompanion==null)return;
        try{s.visualCompanion.getClass().getMethod("setShownItem",ItemStack.class).invoke(s.visualCompanion,ItemStack.EMPTY);}
        catch(Exception e){SvExtract.LOGGER.warn("Companion weapon visual cleanup failed",e);}
        s.visualCompanion=null;
    }
    private void sendHud(ServerPlayerEntity player,Session s,boolean force){
        Protocol.Hud hud;
        if(s.defusal!=null){
            String objective=s.defusal.phase()==DefusalMatch.Phase.BUY?"versus_buy":s.defusal.phase()==DefusalMatch.Phase.ROUND_OVER?"versus_round_over":s.defusal.planted()?"versus_bomb":s.defusal.team(s.player)==s.defusal.attackingTeam()?"versus_attack":"versus_defend";
            hud=new Protocol.Hud(objective,s.defusal.remainingSeconds(tick),s.defusal.channelSeconds(tick),s.defusal.round(),"VERSUS",s.teamScore,s.enemyScore,s.slot.originX);
        }else if(s.versus!=null){
            var result=s.versus.snapshot(tick);
            String objective=s.versus.rules().mode()==VersusMatch.Mode.DOMINATION?"versus_domination":"versus_tdm";
            hud=new Protocol.Hud(objective,(int)Math.min(Integer.MAX_VALUE,(result.remainingTicks()+19)/20),0,0,"VERSUS",s.teamScore,s.enemyScore,s.slot.originX);
        }else hud=new Protocol.Hud(s.mode.optionalObjectives()?"operation_"+s.graph.next():s.graph.next(),Math.max(0,s.mode.timeoutSeconds()-(int)((tick-s.started)/20)),
                s.extractAt<0?0:(int)Math.max(1,(s.extractAt-tick+19)/20),s.threat,locked(s.player)?"SETTLING":s.mode.id().toUpperCase(java.util.Locale.ROOT),s.teamScore,s.enemyScore,s.slot.originX);
        if(force||!hud.equals(s.lastHud)){ServerPlayNetworking.send(player,hud);s.lastHud=hud;telemetry.packet(64);}
    }
    private void prepareHaven(){
        if(havenPreparation==null){List<ChunkPos> positions=new ArrayList<>();for(int c=0;c<16;c++)positions.add(new ChunkPos(c%4-2,c/4-2));havenPreparation=new ChunkPreparation(haven,positions);}
        if(!havenPreparation.tick(config.performance().chunksPerTick()))return;
        int budget=config.performance().blocksPerTick();
        while(budget-->0&&havenCursor<havenPlan.size()){
            StationBlueprint.Change c=havenPlan.get(havenCursor++);haven.setBlockState(c.position(),c.state(),Block.NOTIFY_LISTENERS);telemetry.blocks(1);
        }
        if(havenCursor!=havenPlan.size())return;
        havenReady=true;
        List<VillagerEntity> existing=haven.getEntitiesByType(EntityType.VILLAGER,new Box(-24,60,-24,24,80,24),e->e.getCommandTags().contains("svextract_mission_control"));
        if(existing.isEmpty()){
            VillagerEntity npc=EntityType.VILLAGER.create(haven);
            if(npc!=null){npc.refreshPositionAndAngles(0.5,65,-12.5,0,0);npc.setAiDisabled(true);npc.setInvulnerable(true);npc.setPersistent();npc.addCommandTag("svextract_mission_control");npc.setCustomName(Text.translatable("svextract.npc.control"));npc.setCustomNameVisible(true);haven.spawnEntity(npc);}
        }
        SvExtract.LOGGER.info("Haven-9 mission control prepared. Original Redline regions preparing with bounded block/chunk budgets.");
        havenPreparation.close();havenPreparation=null;
    }
    public boolean isHub(ServerWorld world){return world==haven||(skyrimInstalled&&world==skyrim);}
    private void prepareSkyrim(){
        if(skyrimPreparation==null){List<ChunkPos> positions=new ArrayList<>();for(int c=0;c<16;c++)positions.add(new ChunkPos((skyrimX>>4)+c%4-2,(skyrimZ>>4)+c/4-2));skyrimPreparation=new ChunkPreparation(skyrim,positions);}
        if(!skyrimPreparation.tick(config.performance().chunksPerTick()))return;
        var present=skyrim.getEntitiesByType(EntityType.VILLAGER,new Box(skyrimX-12,skyrimY-8,skyrimZ-16,skyrimX+12,skyrimY+8,skyrimZ+12),e->e.getCommandTags().contains("svextract_mission_control"));
        if(present.isEmpty()){
            VillagerEntity npc=EntityType.VILLAGER.create(skyrim);
            if(npc!=null){npc.refreshPositionAndAngles(skyrimX+0.5,skyrimY,skyrimZ-3.5,0,0);npc.setAiDisabled(true);npc.setInvulnerable(true);npc.setPersistent();npc.addCommandTag("svextract_mission_control");npc.setCustomName(Text.translatable("svextract.npc.control"));npc.setCustomNameVisible(true);skyrim.spawnEntity(npc);}
        }
        skyrimReady=true;SvExtract.LOGGER.info("Imported Skyrim hub prepared at {},{},{}. No terrain replacement performed.",skyrimX,skyrimY,skyrimZ);
        skyrimPreparation.close();skyrimPreparation=null;
    }
    public void skyrim(ServerPlayerEntity player){
        if(restricted(player.getUuid())){message(player,"svextract.error.busy");return;}
        if(!skyrimInstalled){player.sendMessage(Text.literal("Skyrim map is not installed. Stop this test/server save and run the validated world-map importer."),false);return;}
        if(!skyrimReady){message(player,"svextract.error.capacity");return;}
        teleport(player,skyrim,skyrimX,skyrimY,skyrimZ,180,0);rpgRestoreAfterHub(player);
    }
    private void rpgRestoreAfterHub(ServerPlayerEntity player){try{rpg.restoreStats(player,false);}catch(Exception e){failSafe(player.getUuid(),e);}}
    public void haven(ServerPlayerEntity player){
        if(restricted(player.getUuid())){message(player,"svextract.error.busy");return;}
        if(!havenReady){message(player,"svextract.error.capacity");return;}returnToHaven(player);
    }
    private void returnToHaven(ServerPlayerEntity p){teleport(p,haven,0,65,4,180,0);}
    private void teleport(ServerPlayerEntity p,ServerWorld world,double x,double y,double z,float yaw,float pitch){
        internalTeleport=true;
        try{
            p.teleportTo(new net.minecraft.world.TeleportTarget(world,new Vec3d(x,y,z),Vec3d.ZERO,yaw,pitch,net.minecraft.world.TeleportTarget.NO_OP));
            p.setVelocity(Vec3d.ZERO);p.velocityModified=true;p.fallDistance=0;
        }finally{internalTeleport=false;}
    }
    private static void message(ServerPlayerEntity p,String key){p.sendMessage(Text.translatable(key),false);}
    private boolean allowReadCommand(ServerPlayerEntity p){
        Long prior=readCommands.get(p.getUuid());
        if(prior!=null&&tick-prior<config.performance().commandCooldownTicks())return false;
        readCommands.put(p.getUuid(),tick);return true;
    }
    public void progress(ServerPlayerEntity p){if(!allowReadCommand(p))return;watch(journal.progress(p.getUuid()),r->p.sendMessage(Text.translatable("svextract.progress",r.extractions(),r.trainingCredits(),r.aegisReputation()),false),e->failSafe(p.getUuid(),e));}
    public Map<String,Object> report(){
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("schemaVersion",1);report.put("modVersion",SvExtract.VERSION);report.put("capturedAt",java.time.Instant.now().toString());
        report.put("hardware",Map.of("os",System.getProperty("os.name"),"arch",System.getProperty("os.arch"),"availableProcessors",Runtime.getRuntime().availableProcessors(),"maxHeapBytes",Runtime.getRuntime().maxMemory(),"jvm",System.getProperty("java.vm.name"),"javaVersion",System.getProperty("java.version"),"jvmArguments",java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
        report.put("mods",Integrations.installed());report.put("tickProcessing",telemetry.snapshot());
        report.put("database",journal.metrics(durablePending));report.put("runtimeTicks",tick);
        report.put("combatProcessing",telemetry.combatSnapshot());
        report.put("activeMissions",sessions.size());report.put("realPlayers",server.getPlayerManager().getCurrentPlayerCount());
        report.put("havenMap",havenMapProvenance);report.put("havenPrepared",havenReady);report.put("skyrimMap",Map.of("installed",skyrimInstalled,"prepared",skyrimReady,"spawn",List.of(skyrimX,skyrimY,skyrimZ)));
        report.put("instances",pool.slots().stream().map(s->Map.of("slot",s.index,"map",s.map,"originX",s.originX,"state",s.state.name(),"retainedChunks",s.retainedChunks(),"preparationMs",s.preparationNanos/1e6,"cleanupMs",s.cleanupNanos/1e6)).toList());
        report.put("sessions",sessions.values().stream().map(s->Map.of("mode",s.mode.id(),"map",s.slot.map,"humanPlayers",1,"teamScore",s.teamScore,"enemyScore",s.enemyScore,"pendingBotRespawns",s.botRespawns.size(),"landmarksVisited",s.discoveredLandmarks.size())).toList());
        report.put("enemies",sessions.values().stream().mapToInt(s->s.enemies.size()).sum());
        report.put("friendlyBots",sessions.values().stream().mapToInt(s->s.allies.size()).sum());
        report.put("integrations",Map.of("pointBlank",Integrations.pointBlankLoaded(),"pointBlankItemCount",Integrations.pointBlankItems().size(),"cobblemon",cobblemon.ready(),"beconomy",economy.ready(),"svframemmo",rpg.ready()));
        try{report.put("installedClasses",rpg.classIds());}catch(Exception e){report.put("classDiscoveryError",e.toString());}
        report.put("loadedChunks",Map.of("raid",raid.getChunkManager().getLoadedChunkCount(),"haven",haven.getChunkManager().getLoadedChunkCount()));
        report.put("validation",Map.of("fullModpackVerified",false,"multiplayerVerified",false,"pointBlankShootingVerified",false,"pathfindingMeasured",false));
        return report;
    }
    public Path export() throws Exception {
        Path folder=server.getSavePath(WorldSavePath.ROOT).resolve("svextract/reports");Files.createDirectories(folder);
        Path path=folder.resolve("performance-"+System.currentTimeMillis()+".json");
        // Explicit admin command; tiny diagnostic write, never on the automatic tick path.
        Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report()));return path;
    }
    public void reload(ServerConfig next){
        // World geometry, database capacity and telemetry storage are restart-only to preserve consistency.
        if(!next.performance().equals(config.performance())||next.mission().regionSpacing()!=config.mission().regionSpacing())
            throw new IllegalArgumentException("Performance pool/worker settings and regionSpacing require a server restart. Active missions retain their original settings.");
        config=next;
    }
    public void balances(ServerPlayerEntity player){
        if(!allowReadCommand(player))return;
        try{if(!economy.ready()){player.sendMessage(Text.literal("BEconomy is unavailable."),false);return;}
            for(BEconomyAdapter.Currency c:economy.currencies())player.sendMessage(Text.literal(c.name()+": "+economy.balance(player.getUuid(),c.id())+" "+c.symbol()),false);
        }catch(Exception e){SvExtract.LOGGER.error("BEconomy API read failed",e);player.sendMessage(Text.literal("BEconomy balance read failed; see server log."),false);}
    }
    @Override public void close(){
        for(Session s:sessions.values()){
            ServerPlayerEntity player=server.getPlayerManager().getPlayer(s.player);
            if(s.active&&s.mode.equipmentRisk()&&player!=null)try{
                // Shutdown-only durable checkpoint; normal ticks use the bounded asynchronous writer.
                journal.checkpoint(s.raid,currentInventory(player,s)).join();
            }catch(Exception e){SvExtract.LOGGER.error("Shutdown checkpoint failed for {}; previous durable checkpoint retained",s.raid,e);}
            dispose(s);
        }
        sessions.clear();if(skyrimPreparation!=null)skyrimPreparation.close();if(havenPreparation!=null)havenPreparation.close();pool.stop();journal.close();work.clear();locked.clear();verified.clear();recoveryReady.clear();stashInFlight.clear();stashRequests.clear();frozen.clear();handshakes.clear();readCommands.clear();lobbyRequests.clear();botOwners.clear();companions.clear();lobby.clear();
    }
}
