package dev.svextract.persistence;

import dev.svextract.mission.RaidState;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Single bounded SQLite writer. Inventory snapshots are bytes captured on the server thread.
 * FULL WAL durability; one live transaction per player; CAS transitions; idempotent settlement.
 * This class never touches Minecraft worlds, players or registries from its worker.
 */
public final class RaidJournal implements AutoCloseable {
    public record ReturnPoint(String world,double x,double y,double z,float yaw,float pitch) {}
    public record Entry(UUID raid, UUID player, RaidState state, byte[] reserved, byte[] settlement,
                        ReturnPoint home, String outcome,String mode,byte[] checkpoint) {}
    public record Progress(int extractions,int trainingCredits,int aegisReputation) {}
    public record Stash(long revision,byte[] items) {}
    public record StashTransfer(UUID id,UUID player,long revision,byte[] beforeInventory,byte[] afterInventory,byte[] afterStash,String dimension,boolean closed) {}
    public record Metrics(int queueDepth,int pending,long operations,long failures,double averageMs,double maxMs) {}
    public record Squad(UUID id,UUID leader,List<UUID> members) { public Squad { members=List.copyOf(members); } }
    public record Lobby(long revision,List<Squad> squads) { public Lobby { squads=List.copyOf(squads); } }
    private final Connection connection;
    private final ThreadPoolExecutor executor;
    private final int shutdownSeconds, maxBytes;
    private final AtomicLong ops=new AtomicLong(), failures=new AtomicLong(), nanos=new AtomicLong(), maxNanos=new AtomicLong();

    public RaidJournal(Path file,int capacity,int shutdownSeconds,int maxBytes) throws SQLException {
        this.shutdownSeconds=shutdownSeconds; this.maxBytes=maxBytes;
        connection=DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement s=connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=FULL");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)");
            s.execute("INSERT INTO schema_version SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM schema_version)");
            try(ResultSet r=s.executeQuery("SELECT version FROM schema_version")) {
                if(!r.next() || r.getInt(1)!=1) throw new SQLException("Unsupported SVEXTRACT database schema");
            }
            s.execute("CREATE TABLE IF NOT EXISTS raids(id TEXT PRIMARY KEY,player TEXT NOT NULL,state TEXT NOT NULL," +
                    "reserved BLOB NOT NULL,settlement BLOB,world TEXT NOT NULL,x REAL,y REAL,z REAL,yaw REAL,pitch REAL," +
                    "outcome TEXT,created TEXT NOT NULL,updated TEXT NOT NULL)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS one_live_raid ON raids(player) WHERE state <> 'CLOSED'");
            s.execute("CREATE INDEX IF NOT EXISTS raid_recovery ON raids(state)");
            s.execute("CREATE TABLE IF NOT EXISTS progress(player TEXT PRIMARY KEY,extractions INTEGER NOT NULL DEFAULT 0,"+
                    "training_credits INTEGER NOT NULL DEFAULT 0,aegis_reputation INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS audit(seq INTEGER PRIMARY KEY,id TEXT NOT NULL,event TEXT NOT NULL,at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS lobby_revision(id INTEGER PRIMARY KEY CHECK(id=1),revision INTEGER NOT NULL)");
            s.execute("INSERT OR IGNORE INTO lobby_revision VALUES(1,0)");
            s.execute("CREATE TABLE IF NOT EXISTS squads(id TEXT PRIMARY KEY,leader TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS squad_members(player TEXT PRIMARY KEY,squad TEXT NOT NULL,position INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS squad_lookup ON squad_members(squad,position)");
            s.execute("CREATE TABLE IF NOT EXISTS stashes(player TEXT PRIMARY KEY,revision INTEGER NOT NULL,items BLOB NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS stash_transfers(id TEXT PRIMARY KEY,player TEXT NOT NULL,revision INTEGER NOT NULL,before_inventory BLOB NOT NULL,after_inventory BLOB NOT NULL,after_stash BLOB NOT NULL,dimension TEXT NOT NULL,closed INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS one_live_stash_transfer ON stash_transfers(player) WHERE closed=0");
            Set<String> raidColumns=new HashSet<>();
            try(ResultSet columns=s.executeQuery("PRAGMA table_info(raids)")){while(columns.next())raidColumns.add(columns.getString("name"));}
            if(!raidColumns.contains("mode"))s.execute("ALTER TABLE raids ADD COLUMN mode TEXT NOT NULL DEFAULT 'training'");
            if(!raidColumns.contains("checkpoint"))s.execute("ALTER TABLE raids ADD COLUMN checkpoint BLOB");
        }
        executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),r->{
            Thread t=new Thread(r,"svextract-journal"); t.setDaemon(false); return t;
        },new ThreadPoolExecutor.AbortPolicy());
    }

    @FunctionalInterface private interface SqlTask<T> { T run() throws Exception; }
    private <T> CompletableFuture<T> submit(SqlTask<T> task) {
        CompletableFuture<T> future=new CompletableFuture<>();
        try { executor.execute(()->{
            long start=System.nanoTime();
            try { future.complete(task.run()); }
            catch(Exception e) { failures.incrementAndGet(); future.completeExceptionally(e); }
            finally { long duration=System.nanoTime()-start; ops.incrementAndGet(); nanos.addAndGet(duration); maxNanos.accumulateAndGet(duration,Math::max); }
        }); } catch(RejectedExecutionException e) { failures.incrementAndGet(); future.completeExceptionally(new IllegalStateException("Persistence queue full or shutting down; no transition performed",e)); }
        return future;
    }
    private <T> T transaction(SqlTask<T> task) throws Exception {
        connection.setAutoCommit(false);
        try { T result=task.run(); connection.commit(); return result; }
        catch(Exception e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(true); }
    }
    private void validate(byte[] data) {
        if(data==null || data.length==0 || data.length>maxBytes) throw new IllegalArgumentException("Snapshot must contain 1–"+maxBytes+" bytes");
    }
    public CompletableFuture<Entry> reserve(UUID raid,UUID player,byte[] snapshot,ReturnPoint home) {
        return reserve(raid,player,snapshot,home,"training");
    }
    public CompletableFuture<Entry> reserve(UUID raid,UUID player,byte[] snapshot,ReturnPoint home,String mode) {
        if(!Set.of("training","operations","versus").contains(mode))throw new IllegalArgumentException("Unsupported transaction mode");
        validate(snapshot); byte[] immutable=snapshot.clone();
        return submit(()->transaction(()->{
            Entry existing=find(raid);
            if(existing!=null) {
                if(!existing.player.equals(player) || !Arrays.equals(existing.reserved,immutable) || !existing.home.equals(home)||!existing.mode.equals(mode))
                    throw new IllegalStateException("Transaction id reused with different ownership or snapshot");
                return existing;
            }
            if(findStashTransfer(player).isPresent())throw new IllegalStateException("Storage transfer is pending; recover before deployment");
            try(PreparedStatement s=connection.prepareStatement("INSERT INTO raids(id,player,state,reserved,world,x,y,z,yaw,pitch,created,updated) VALUES(?,?,'RESERVED',?,?,?,?,?,?,?,?,?)")) {
                s.setString(1,raid.toString());s.setString(2,player.toString());s.setBytes(3,immutable);
                s.setString(4,home.world);s.setDouble(5,home.x);s.setDouble(6,home.y);s.setDouble(7,home.z);
                s.setFloat(8,home.yaw);s.setFloat(9,home.pitch);s.setString(10,Instant.now().toString());s.setString(11,Instant.now().toString());s.executeUpdate();
            }
            try(PreparedStatement update=connection.prepareStatement("UPDATE raids SET mode=?,checkpoint=? WHERE id=?")){
                update.setString(1,mode);update.setBytes(2,immutable);update.setString(3,raid.toString());update.executeUpdate();
            }
            audit(raid,"RESERVED"); return find(raid);
        }));
    }
    /** Immutable server-thread snapshot; bounded writer preserves ammo/components across recovery. */
    public CompletableFuture<Entry> checkpoint(UUID id,byte[] snapshot){
        validate(snapshot);byte[] immutable=snapshot.clone();
        return submit(()->transaction(()->{
            Entry entry=require(id);
            if(entry.state!=RaidState.ACTIVE)throw new IllegalStateException("Only an active raid can checkpoint");
            try(PreparedStatement update=connection.prepareStatement("UPDATE raids SET checkpoint=?,updated=? WHERE id=? AND state='ACTIVE'")){
                update.setBytes(1,immutable);update.setString(2,Instant.now().toString());update.setString(3,id.toString());
                if(update.executeUpdate()!=1)throw new IllegalStateException("Checkpoint state changed");
            }
            return require(id);
        }));
    }
    public CompletableFuture<Entry> activate(UUID id) {
        return submit(()->transaction(()->{
            Entry current=require(id);
            if(current.state==RaidState.ACTIVE) return current;
            transition(id,RaidState.RESERVED,RaidState.ACTIVE); return require(id);
        }));
    }
    public CompletableFuture<Entry> prepareSettlement(UUID id,byte[] finalInventory,String outcome) {
        validate(finalInventory); byte[] immutable=finalInventory.clone();
        if(!Set.of("EXTRACTED","ABORTED","DEATH","TIMEOUT","RECOVERED","WON","LOST","DRAW").contains(outcome)) throw new IllegalArgumentException("Invalid outcome");
        return submit(()->transaction(()->{
            Entry current=require(id);
            if(current.state==RaidState.SETTLING || current.state==RaidState.CLOSED) {
                if(!Arrays.equals(current.settlement,immutable) || !outcome.equals(current.outcome))
                    throw new IllegalStateException("Settlement replay differs from durable result");
                return current;
            }
            if(!current.state.canTransitionTo(RaidState.SETTLING)) throw new IllegalStateException("Illegal settlement state");
            try(PreparedStatement s=connection.prepareStatement("UPDATE raids SET state='SETTLING',settlement=?,outcome=?,updated=? WHERE id=? AND state=?")) {
                s.setBytes(1,immutable);s.setString(2,outcome);s.setString(3,Instant.now().toString());s.setString(4,id.toString());s.setString(5,current.state.name());
                if(s.executeUpdate()!=1) throw new IllegalStateException("Settlement CAS failed");
            }
            audit(id,"SETTLING:"+outcome);return require(id);
        }));
    }
    public CompletableFuture<Entry> closeAfterPlayerSave(UUID id) {
        return closeVerified(id,()->{});
    }
    @FunctionalInterface public interface SaveVerification { void verify() throws Exception; }
    /** File I/O and raw NBT verification run on the bounded writer, never on the tick thread. */
    public CompletableFuture<Entry> closeVerified(UUID id,SaveVerification verification) {
        return submit(()->transaction(()->{
            Entry current=require(id);
            if(current.state==RaidState.CLOSED) return current;
            verification.verify();
            transition(id,RaidState.SETTLING,RaidState.CLOSED);
            if("EXTRACTED".equals(current.outcome)&&!"versus".equals(current.mode)) {
                try(PreparedStatement s=connection.prepareStatement("INSERT INTO progress(player,extractions,training_credits,aegis_reputation) VALUES(?,1,?,1) ON CONFLICT(player) DO UPDATE SET extractions=extractions+1,training_credits=training_credits+excluded.training_credits,aegis_reputation=aegis_reputation+1")) {
                    s.setString(1,current.player.toString());s.setInt(2,"training".equals(current.mode)?1:0);s.executeUpdate();
                }
            }
            return require(id);
        }));
    }
    public CompletableFuture<Optional<Entry>> live(UUID player) {
        return submit(()->{
            try(PreparedStatement s=connection.prepareStatement("SELECT * FROM raids WHERE player=? AND state<>'CLOSED'")) {
                s.setString(1,player.toString());try(ResultSet r=s.executeQuery()) { return r.next()?Optional.of(read(r)):Optional.empty(); }
            }
        });
    }
    public CompletableFuture<Progress> progress(UUID player) {
        return submit(()->{
            try(PreparedStatement s=connection.prepareStatement("SELECT * FROM progress WHERE player=?")) {
                s.setString(1,player.toString());try(ResultSet r=s.executeQuery()) { return r.next()?new Progress(r.getInt("extractions"),r.getInt("training_credits"),r.getInt("aegis_reputation")):new Progress(0,0,0); }
            }
        });
    }
    public CompletableFuture<Integer> pending() {
        return submit(()->{try(Statement s=connection.createStatement();ResultSet r=s.executeQuery("SELECT (SELECT COUNT(*) FROM raids WHERE state<>'CLOSED')+(SELECT COUNT(*) FROM stash_transfers WHERE closed=0)")) {return r.next()?r.getInt(1):0;}});
    }
    private Stash readStash(UUID player,byte[] empty)throws SQLException{
        try(PreparedStatement s=connection.prepareStatement("SELECT revision,items FROM stashes WHERE player=?")){
            s.setString(1,player.toString());try(ResultSet r=s.executeQuery()){return r.next()?new Stash(r.getLong(1),r.getBytes(2)):new Stash(0,empty.clone());}
        }
    }
    public CompletableFuture<Stash> stash(UUID player,byte[] empty){validate(empty);byte[] copy=empty.clone();return submit(()->readStash(player,copy));}
    private static StashTransfer readStashTransfer(ResultSet r)throws SQLException{
        return new StashTransfer(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("player")),r.getLong("revision"),r.getBytes("before_inventory"),r.getBytes("after_inventory"),r.getBytes("after_stash"),r.getString("dimension"),r.getBoolean("closed"));
    }
    private Optional<StashTransfer> findStashTransfer(UUID player)throws SQLException{
        try(PreparedStatement s=connection.prepareStatement("SELECT * FROM stash_transfers WHERE player=? AND closed=0")){
            s.setString(1,player.toString());try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(readStashTransfer(r)):Optional.empty();}
        }
    }
    public CompletableFuture<Optional<StashTransfer>> liveStashTransfer(UUID player){return submit(()->findStashTransfer(player));}
    /** The caller holds the player inventory lock. No inventory is changed before this durable intent. */
    public CompletableFuture<StashTransfer> prepareStashTransfer(UUID id,UUID player,long revision,byte[] before,byte[] after,byte[] stash,String dimension){
        validate(before);validate(after);validate(stash);
        byte[] original=before.clone(),result=after.clone(),stored=stash.clone();
        if(revision<0||dimension==null||dimension.length()>128)throw new IllegalArgumentException("Invalid storage transfer identity");
        return submit(()->transaction(()->{
            try(PreparedStatement s=connection.prepareStatement("SELECT * FROM stash_transfers WHERE id=?")){
                s.setString(1,id.toString());try(ResultSet r=s.executeQuery()){if(r.next()){
                    StashTransfer existing=readStashTransfer(r);
                    if(!existing.player.equals(player)||existing.revision!=revision||!Arrays.equals(existing.beforeInventory,original)||!Arrays.equals(existing.afterInventory,result)||!Arrays.equals(existing.afterStash,stored)||!existing.dimension.equals(dimension))throw new IllegalStateException("Storage transaction replay differs from durable ownership");
                    return existing;
                }}
            }
            try(PreparedStatement s=connection.prepareStatement("SELECT 1 FROM raids WHERE player=? AND state<>'CLOSED'")){
                s.setString(1,player.toString());try(ResultSet r=s.executeQuery()){if(r.next())throw new IllegalStateException("Cannot transfer storage during a raid transaction");}
            }
            if(readStash(player,new byte[]{0}).revision!=revision)throw new IllegalStateException("Storage changed; refresh before transferring");
            try(PreparedStatement s=connection.prepareStatement("INSERT INTO stash_transfers VALUES(?,?,?,?,?,?,?,0)")){
                s.setString(1,id.toString());s.setString(2,player.toString());s.setLong(3,revision);s.setBytes(4,original);s.setBytes(5,result);s.setBytes(6,stored);s.setString(7,dimension);s.executeUpdate();
            }
            audit(id,"STASH_PREPARED");return findStashTransfer(player).orElseThrow();
        }));
    }
    /** Commit ownership only after the replacement player inventory was verified and fsynced. */
    public CompletableFuture<StashTransfer> closeStashTransfer(UUID id,SaveVerification verification){
        return submit(()->transaction(()->{
            StashTransfer transfer;
            try(PreparedStatement s=connection.prepareStatement("SELECT * FROM stash_transfers WHERE id=?")){
                s.setString(1,id.toString());try(ResultSet r=s.executeQuery()){if(!r.next())throw new IllegalStateException("Unknown storage transaction");transfer=readStashTransfer(r);}
            }
            if(transfer.closed)return transfer;
            verification.verify();
            if(readStash(transfer.player,new byte[]{0}).revision!=transfer.revision)throw new IllegalStateException("Storage revision changed during protected delivery");
            try(PreparedStatement s=connection.prepareStatement("INSERT INTO stashes VALUES(?,?,?) ON CONFLICT(player) DO UPDATE SET revision=excluded.revision,items=excluded.items")){
                s.setString(1,transfer.player.toString());s.setLong(2,transfer.revision+1);s.setBytes(3,transfer.afterStash);s.executeUpdate();
            }
            try(PreparedStatement s=connection.prepareStatement("UPDATE stash_transfers SET closed=1 WHERE id=? AND closed=0")){
                s.setString(1,id.toString());if(s.executeUpdate()!=1)throw new IllegalStateException("Storage ownership CAS failed");
            }
            audit(id,"STASH_CLOSED");return new StashTransfer(transfer.id,transfer.player,transfer.revision,transfer.beforeInventory,transfer.afterInventory,transfer.afterStash,transfer.dimension,true);
        }));
    }
    public CompletableFuture<Lobby> lobby(){return submit(this::readLobby);}
    private Lobby readLobby() throws SQLException {
        long revision;try(Statement s=connection.createStatement();ResultSet r=s.executeQuery("SELECT revision FROM lobby_revision WHERE id=1")){if(!r.next())throw new SQLException("Missing lobby revision");revision=r.getLong(1);}
        Map<UUID,List<UUID>> membership=new HashMap<>();
        try(Statement s=connection.createStatement();ResultSet r=s.executeQuery("SELECT player,squad FROM squad_members ORDER BY squad,position")){
            while(r.next())membership.computeIfAbsent(UUID.fromString(r.getString(2)),id->new ArrayList<>()).add(UUID.fromString(r.getString(1)));
        }
        List<Squad> squads=new ArrayList<>();
        try(Statement s=connection.createStatement();ResultSet r=s.executeQuery("SELECT id,leader FROM squads ORDER BY id")){
            while(r.next()){UUID id=UUID.fromString(r.getString(1));squads.add(new Squad(id,UUID.fromString(r.getString(2)),membership.getOrDefault(id,List.of())));}
        }
        Lobby lobby=new Lobby(revision,squads);validateLobby(lobby);return lobby;
    }
    private static void validateLobby(Lobby lobby){
        if(lobby.revision()<0||lobby.squads().size()>128)throw new IllegalArgumentException("Lobby exceeds bounded persistence limits");
        Set<UUID> ids=new HashSet<>(),players=new HashSet<>();
        for(Squad squad:lobby.squads()){
            if(!ids.add(squad.id())||squad.members().isEmpty()||squad.members().size()>5||!squad.members().contains(squad.leader()))throw new IllegalArgumentException("Invalid squad membership or leader");
            for(UUID player:squad.members())if(!players.add(player))throw new IllegalArgumentException("A player belongs to more than one squad");
        }
        if(players.size()>256)throw new IllegalArgumentException("Lobby exceeds 256 stored players");
    }
    /** Rare lobby changes share the bounded writer. Persist before publishing new membership. */
    public CompletableFuture<Lobby> saveLobby(Lobby proposal){
        validateLobby(proposal);
        return submit(()->transaction(()->{
            try(PreparedStatement s=connection.prepareStatement("UPDATE lobby_revision SET revision=revision+1 WHERE id=1 AND revision=?")){
                s.setLong(1,proposal.revision());if(s.executeUpdate()!=1)throw new IllegalStateException("Lobby revision conflict; membership not changed");
            }
            try(Statement s=connection.createStatement()){s.executeUpdate("DELETE FROM squad_members");s.executeUpdate("DELETE FROM squads");}
            try(PreparedStatement group=connection.prepareStatement("INSERT INTO squads VALUES(?,?)");PreparedStatement member=connection.prepareStatement("INSERT INTO squad_members VALUES(?,?,?)")){
                for(Squad squad:proposal.squads()){
                    group.setString(1,squad.id().toString());group.setString(2,squad.leader().toString());group.addBatch();
                    for(int position=0;position<squad.members().size();position++){member.setString(1,squad.members().get(position).toString());member.setString(2,squad.id().toString());member.setInt(3,position);member.addBatch();}
                }
                group.executeBatch();member.executeBatch();
            }
            return new Lobby(proposal.revision()+1,proposal.squads());
        }));
    }
    private Entry find(UUID id) throws SQLException {
        try(PreparedStatement s=connection.prepareStatement("SELECT * FROM raids WHERE id=?")) {
            s.setString(1,id.toString());try(ResultSet r=s.executeQuery()) {return r.next()?read(r):null;}
        }
    }
    private Entry require(UUID id) throws SQLException { Entry e=find(id);if(e==null)throw new IllegalStateException("Unknown transaction "+id);return e; }
    private static Entry read(ResultSet r) throws SQLException {
        return new Entry(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("player")),RaidState.valueOf(r.getString("state")),r.getBytes("reserved"),r.getBytes("settlement"),
                new ReturnPoint(r.getString("world"),r.getDouble("x"),r.getDouble("y"),r.getDouble("z"),r.getFloat("yaw"),r.getFloat("pitch")),r.getString("outcome"),r.getString("mode"),r.getBytes("checkpoint"));
    }
    private void transition(UUID id,RaidState expected,RaidState next) throws SQLException {
        if(!expected.canTransitionTo(next))throw new IllegalArgumentException("Illegal transition");
        try(PreparedStatement s=connection.prepareStatement("UPDATE raids SET state=?,updated=? WHERE id=? AND state=?")) {
            s.setString(1,next.name());s.setString(2,Instant.now().toString());s.setString(3,id.toString());s.setString(4,expected.name());
            if(s.executeUpdate()!=1)throw new IllegalStateException("Expected "+expected+" for "+id);
        }
        audit(id,next.name());
    }
    private void audit(UUID id,String event) throws SQLException {
        try(PreparedStatement s=connection.prepareStatement("INSERT INTO audit(id,event,at) VALUES(?,?,?)")) {
            s.setString(1,id.toString());s.setString(2,event);s.setString(3,Instant.now().toString());s.executeUpdate();
        }
    }
    public Metrics metrics(int pending) { long n=ops.get();return new Metrics(executor.getQueue().size(),pending,n,failures.get(),n==0?0:nanos.get()/1e6/n,maxNanos.get()/1e6); }
    @Override public void close() {
        executor.shutdown();
        try {
            if(!executor.awaitTermination(shutdownSeconds,TimeUnit.SECONDS)) throw new IllegalStateException("Database still flushing. Journal retained for restart recovery.");
            try(Statement s=connection.createStatement()) {s.execute("PRAGMA wal_checkpoint(TRUNCATE)");}
            connection.close();
        } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException("Shutdown interrupted; journal remains recoverable",e);}
        catch(SQLException e) {throw new IllegalStateException("Database shutdown failed",e);}
    }
}
