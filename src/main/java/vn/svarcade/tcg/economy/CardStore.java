package vn.svarcade.tcg.economy;

import vn.svarcade.tcg.data.Catalog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Single writer per world. Every exchange is an atomic SQLite transaction, including provenance. */
public final class CardStore implements AutoCloseable {
    public record Owned(String serial,String card,String finish,String origin,String finder,String owner,String lock,long acquired) {}
    public record Listing(String id,String seller,String serial,String name,long price) {}
    public record Deck(String name,List<String> main,List<String> extra) {}
    public enum Format { CASUAL, RANKED, UNLIMITED, EVENT, UNDERGROUND }
    public record Pity(int pulls,boolean guaranteed) {}
    public record Profile(long coins,int reputation,int rating,int owned) {}
    public record Pull(String card,String name,String rarity,String finish,String serial) {}
    public record Trade(String id,String sender,String recipient,String offered,String requested,long coins) {}
    private record Pool(List<Catalog.Weighted> entries,List<Long> weights,long total){}
    private final Connection db; private final Catalog catalog; private final Random rng;
    private final Gson gson=new Gson();
    @FunctionalInterface private interface Work<T>{T run()throws Exception;}
    public CardStore(Path file,Catalog catalog,Random rng)throws Exception {
        this.catalog=catalog;this.rng=rng;Files.createDirectories(file.toAbsolutePath().getParent());
        Class.forName("org.sqlite.JDBC");db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
        try(Statement s=db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA synchronous=FULL");s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS duel_results(owner TEXT NOT NULL,match TEXT NOT NULL,won INTEGER NOT NULL,PRIMARY KEY(owner,match))");
            s.execute("CREATE TABLE IF NOT EXISTS last_rewards(owner TEXT PRIMARY KEY,value TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS profiles(owner TEXT PRIMARY KEY, coins INTEGER NOT NULL CHECK(coins>=0), reputation INTEGER NOT NULL DEFAULT 0, rating INTEGER NOT NULL DEFAULT 1000)");
            s.execute("CREATE TABLE IF NOT EXISTS cards(serial TEXT PRIMARY KEY, card TEXT NOT NULL, finish TEXT NOT NULL, origin TEXT NOT NULL, finder TEXT NOT NULL, owner TEXT NOT NULL REFERENCES profiles(owner), lock TEXT NOT NULL DEFAULT '', acquired INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS cards_owner ON cards(owner,lock,card)");
            s.execute("CREATE TABLE IF NOT EXISTS physical_redemptions(token TEXT PRIMARY KEY,serial TEXT NOT NULL UNIQUE,owner TEXT NOT NULL,card TEXT NOT NULL,at INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS provenance(id INTEGER PRIMARY KEY AUTOINCREMENT, serial TEXT NOT NULL, previous_owner TEXT, next_owner TEXT NOT NULL, reason TEXT NOT NULL, at INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS provenance_serial ON provenance(serial,id)");
            s.execute("CREATE TABLE IF NOT EXISTS decks(owner TEXT NOT NULL, name TEXT NOT NULL, main TEXT NOT NULL, extra TEXT NOT NULL, PRIMARY KEY(owner,name))");
            s.execute("CREATE TABLE IF NOT EXISTS deck_formats(owner TEXT NOT NULL,name TEXT NOT NULL,format TEXT NOT NULL,PRIMARY KEY(owner,name))");
            s.execute("CREATE TABLE IF NOT EXISTS listings(id TEXT PRIMARY KEY, seller TEXT NOT NULL, serial TEXT NOT NULL UNIQUE, price INTEGER NOT NULL CHECK(price>0))");
            s.execute("CREATE INDEX IF NOT EXISTS listings_price ON listings(price,id)");
            s.execute("CREATE TABLE IF NOT EXISTS sales(id TEXT PRIMARY KEY, card TEXT NOT NULL, seller TEXT NOT NULL, buyer TEXT NOT NULL, price INTEGER NOT NULL, tax INTEGER NOT NULL, at INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS sales_card_time ON sales(card,at)");
            s.execute("CREATE TABLE IF NOT EXISTS pity(owner TEXT NOT NULL, family TEXT NOT NULL, pulls INTEGER NOT NULL, guaranteed INTEGER NOT NULL, PRIMARY KEY(owner,family))");
            s.execute("CREATE TABLE IF NOT EXISTS receipts(owner TEXT NOT NULL, request TEXT NOT NULL, result TEXT NOT NULL, PRIMARY KEY(owner,request))");
            s.execute("CREATE TABLE IF NOT EXISTS progress(owner TEXT NOT NULL, reward TEXT NOT NULL, count INTEGER NOT NULL, claimed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(owner,reward))");
            s.execute("CREATE TABLE IF NOT EXISTS supply(edition TEXT PRIMARY KEY, minted INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS offers(id TEXT PRIMARY KEY, sender TEXT NOT NULL, recipient TEXT NOT NULL, give_card TEXT NOT NULL, want_card TEXT NOT NULL, coins INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS npc_rewards(owner TEXT NOT NULL,reward TEXT NOT NULL,duel TEXT PRIMARY KEY,at INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS npc_rewards_daily ON npc_rewards(owner,reward,at)");
        }
        // Duels are ephemeral. Release only duel locks left by a stopped server; market escrow survives.
        update("UPDATE cards SET lock='' WHERE lock LIKE 'duel:%'");
    }
    private synchronized <T>T tx(Work<T> work) {
        try {db.setAutoCommit(false);try{T result=work.run();db.commit();return result;}catch(Exception ex){db.rollback();throw ex;}finally{db.setAutoCommit(true);}}
        catch(Exception ex){if(ex instanceof IllegalArgumentException a)throw a;throw new IllegalStateException("Card transaction failed",ex);}
    }
    private PreparedStatement statement(String sql,Object...args)throws SQLException {PreparedStatement s=db.prepareStatement(sql);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;}
    private int update(String sql,Object...args)throws SQLException {try(var s=statement(sql,args)){return s.executeUpdate();}}
    private long scalar(String sql,Object...args)throws SQLException {try(var s=statement(sql,args);var r=s.executeQuery()){return r.next()?r.getLong(1):0;}}
    private String mint(String owner,String card,String finish,String origin)throws SQLException {
        return mint(owner,card,finish,origin,owner);
    }
    private String mint(String owner,String card,String finish,String origin,String finder)throws SQLException {
        catalog.card(card);String id=UUID.randomUUID().toString();long now=Instant.now().getEpochSecond();
        update("INSERT INTO cards VALUES(?,?,?,?,?,?,?,?)",id,card,finish,origin,finder,owner,"",now);
        update("INSERT INTO provenance(serial,next_owner,reason,at) VALUES(?,?,?,?)",id,owner,origin,now);return id;
    }
    public enum RedeemStatus { MINTED, RECOVERED, ALREADY_REDEEMED }
    public record RedeemResult(RedeemStatus status,String serial) {
        public boolean accepted(){return status!=RedeemStatus.ALREADY_REDEEMED;}
    }
    /** The token and mint commit together. Retried physical copies cannot create a second serial. */
    public RedeemResult redeemPhysical(String owner,UUID token,String cardId,String finish,String origin,String finder) {
        return tx(()->{
            check(token!=null&&!token.equals(new UUID(0,0)),"PHYSICAL_TOKEN_MISSING");
            catalog.card(cardId);
            check(owner!=null&&!owner.isBlank(),"INVALID_OWNER");
            check(vn.svarcade.tcg.physical.PhysicalCardData.FINISHES.contains(finish),"INVALID_FINISH");
            check(Set.of("BLANK_CAPTURE","CHEST_LOOT","ADMIN_GRANT").contains(origin),"INVALID_ORIGIN");
            check(finder!=null&&finder.length()<=128,"INVALID_FINDER");
            try(var q=statement("SELECT serial,owner,card FROM physical_redemptions WHERE token=?",token.toString());var r=q.executeQuery()) {
                if(r.next()) {
                    String serial=r.getString("serial");
                    boolean same=owner.equals(r.getString("owner"))&&cardId.equals(r.getString("card"));
                    org.slf4j.LoggerFactory.getLogger("cardworlds-physical").warn("CARDWORLDS_PHYSICAL_DUPLICATE_TOKEN token={} player={} serial={} recovery={}",token,owner,serial,same);
                    return new RedeemResult(same?RedeemStatus.RECOVERED:RedeemStatus.ALREADY_REDEEMED,serial);
                }
            }
            // Acquisition can initialize a collection without minting a starter deck.
            update("INSERT OR IGNORE INTO profiles(owner,coins) VALUES(?,2000)",owner);
            String serial=mint(owner,cardId,finish,origin,finder);
            update("INSERT INTO physical_redemptions VALUES(?,?,?,?,?)",token.toString(),serial,owner,cardId,Instant.now().getEpochSecond());
            return new RedeemResult(RedeemStatus.MINTED,serial);
        });
    }
    public void createProfile(String owner,String starter) {tx(()->{
        List<String> ids=catalog.starters().get(starter);check(ids!=null,"Choose an available starter.");
        check(update("INSERT OR IGNORE INTO profiles(owner,coins) VALUES(?,2000)",owner)==1,"You already have a card collection.");
        List<String> serials=new ArrayList<>();for(String id:ids)serials.add(mint(owner,id,"Normal","STARTER"));
        update("INSERT INTO decks VALUES(?,?,?,?)",owner,"Starter",gson.toJson(serials),"[]");return null;
    });}
    public synchronized Profile profile(String owner) {try{return new Profile(scalar("SELECT coins FROM profiles WHERE owner=?",owner),(int)scalar("SELECT reputation FROM profiles WHERE owner=?",owner),(int)scalar("SELECT rating FROM profiles WHERE owner=?",owner),(int)scalar("SELECT COUNT(*) FROM cards WHERE owner=?",owner));}catch(SQLException ex){throw new IllegalStateException(ex);}}
    public synchronized List<Owned> binder(String owner,int page) {check(page>=0&&page<100000,"Invalid page.");try(var s=statement("SELECT * FROM cards WHERE owner=? ORDER BY acquired,serial LIMIT 40 OFFSET ?",owner,page*40);var r=s.executeQuery()){List<Owned> out=new ArrayList<>();while(r.next())out.add(owned(r));return List.copyOf(out);}catch(SQLException ex){throw new IllegalStateException(ex);}}
    /** Unique-first, with enough physical copies per identity for a legal deck. Counts are separate. */
    public synchronized List<Owned> collection(String owner){try(var s=statement("SELECT * FROM (SELECT *,ROW_NUMBER() OVER(PARTITION BY card ORDER BY CASE WHEN lock='' THEN 0 ELSE 1 END,acquired,serial) AS rn FROM cards WHERE owner=?) WHERE rn<=? ORDER BY rn,card LIMIT 1200",owner,catalog.rules().copies());var r=s.executeQuery()){List<Owned> out=new ArrayList<>();while(r.next())out.add(owned(r));return List.copyOf(out);}catch(SQLException ex){throw new IllegalStateException(ex);}}
    public synchronized Map<String,String> formats(String owner){try(var s=statement("SELECT name,format FROM deck_formats WHERE owner=?",owner);var r=s.executeQuery()){Map<String,String> out=new HashMap<>();while(r.next())out.put(r.getString(1),r.getString(2));return out;}catch(SQLException ex){throw new IllegalStateException(ex);}}
    
    private Owned owned(ResultSet r)throws SQLException{return new Owned(r.getString("serial"),r.getString("card"),r.getString("finish"),r.getString("origin"),r.getString("finder"),r.getString("owner"),r.getString("lock"),r.getLong("acquired"));}
    private Owned get(String serial)throws SQLException {try(var s=statement("SELECT * FROM cards WHERE serial=?",serial);var r=s.executeQuery()){check(r.next(),"Card not found.");return owned(r);}}
    public synchronized Map<String,Integer> completion(String owner) {try(var s=statement("SELECT card,COUNT(*) FROM cards WHERE owner=? GROUP BY card",owner);var r=s.executeQuery()){Map<String,Integer> out=new TreeMap<>();while(r.next())out.put(r.getString(1),r.getInt(2));return out;}catch(SQLException ex){throw new IllegalStateException(ex);}}
    public synchronized List<Trade> trades(String owner){try(var s=statement("SELECT o.*,a.card AS offered,b.card AS requested FROM offers o JOIN cards a ON a.serial=o.give_card JOIN cards b ON b.serial=o.want_card WHERE sender=? OR recipient=? ORDER BY o.id LIMIT 20",owner,owner);var r=s.executeQuery()){List<Trade> out=new ArrayList<>();while(r.next())out.add(new Trade(r.getString("id"),r.getString("sender"),r.getString("recipient"),catalog.card(r.getString("offered")).name(),catalog.card(r.getString("requested")).name(),r.getLong("coins")));return out;}catch(SQLException ex){throw new IllegalStateException(ex);}}
    public void saveDeck(String owner,String name,List<String> main,List<String> extra,boolean ranked) {saveDeck(owner,name,main,extra,ranked?Format.RANKED:Format.CASUAL);}
    public void saveDeck(String owner,String name,List<String> main,List<String> extra,Format format) {tx(()->{
        check(name.matches("[A-Za-z0-9 _-]{1,32}"),"Deck names must be 1–32 letters or numbers.");validateOwnedDeck(owner,main,extra,format==Format.RANKED);
        update("INSERT INTO decks VALUES(?,?,?,?) ON CONFLICT(owner,name) DO UPDATE SET main=excluded.main,extra=excluded.extra",owner,name,gson.toJson(main),gson.toJson(extra));update("INSERT INTO deck_formats VALUES(?,?,?) ON CONFLICT(owner,name) DO UPDATE SET format=excluded.format",owner,name,format.name());return null;
    });}
    private List<String> strings(String json){return gson.fromJson(json,new TypeToken<List<String>>(){}.getType());}
    public synchronized Deck deck(String owner,String name) {try(var s=statement("SELECT main,extra FROM decks WHERE owner=? AND name=?",owner,name);var r=s.executeQuery()){check(r.next(),"Deck not found.");return new Deck(name,strings(r.getString(1)),strings(r.getString(2)));}catch(SQLException ex){throw new IllegalStateException(ex);}}
    public synchronized List<String> deckNames(String owner){try(var s=statement("SELECT name FROM decks WHERE owner=? ORDER BY name",owner);var r=s.executeQuery()){List<String> result=new ArrayList<>();while(r.next())result.add(r.getString(1));return result;}catch(SQLException ex){throw new IllegalStateException(ex);}}
    private List<List<String>> validateOwnedDeck(String owner,List<String> main,List<String> extra,boolean ranked)throws SQLException {
        Set<String> seen=new HashSet<>();List<String> m=new ArrayList<>(),e=new ArrayList<>();
        for(int group=0;group<2;group++)for(String serial:group==0?main:extra){check(seen.add(serial),"One physical card cannot occupy two deck slots.");Owned c=get(serial);check(c.owner.equals(owner)&&c.lock.isEmpty(),"A deck card is missing or held in escrow.");(group==0?m:e).add(c.card);}
        List<String> errors=catalog.deckErrors(m,e,ranked);check(errors.isEmpty(),String.join(" ",errors));return List.of(m,e);
    }
    public List<List<String>> lockDeck(String owner,String name,boolean ranked,String duel) {return tx(()->{Deck d=deck(owner,name);var result=validateOwnedDeck(owner,d.main,d.extra,ranked);for(String id:concat(d.main,d.extra))update("UPDATE cards SET lock=? WHERE serial=?","duel:"+duel,id);return result;});}
    public void unlockDuel(String duel){tx(()->{update("UPDATE cards SET lock='' WHERE lock=?","duel:"+duel);return null;});}
    private void lock(String owner,String serial,String lock)throws SQLException {check(update("UPDATE cards SET lock=? WHERE serial=? AND owner=? AND lock=''",lock,serial,owner)==1,"This card is unavailable or belongs to another collector.");}
    private void debit(String owner,long coins)throws SQLException {check(coins>=0,"Invalid price.");check(update("UPDATE profiles SET coins=coins-? WHERE owner=? AND coins>=?",coins,owner,coins)==1,"Not enough HunterCoin.");}
    private void credit(String owner,long coins)throws SQLException {check(coins>=0,"Invalid credit.");check(update("UPDATE profiles SET coins=coins+? WHERE owner=? AND coins<=?",coins,owner,Long.MAX_VALUE-coins)==1,"Balance limit exceeded.");}
    private void transfer(String serial,String from,String to,String reason)throws SQLException {check(update("UPDATE cards SET owner=?,lock='' WHERE serial=? AND owner=?",to,serial,from)==1,"Ownership changed.");update("INSERT INTO provenance(serial,previous_owner,next_owner,reason,at) VALUES(?,?,?,?,?)",serial,from,to,reason,Instant.now().getEpochSecond());}
    public String list(String owner,String serial,long price){return tx(()->{check(price>0&&price<=1_000_000_000L,"Price must be between 1 and 1,000,000,000.");String id=UUID.randomUUID().toString();lock(owner,serial,"market:"+id);update("INSERT INTO listings VALUES(?,?,?,?)",id,owner,serial,price);return id;});}
    public synchronized List<Listing> market(int page){check(page>=0&&page<100000,"Invalid page.");try(var s=statement("SELECT l.*,c.card FROM listings l JOIN cards c USING(serial) ORDER BY price,id LIMIT 20 OFFSET ?",page*20);var r=s.executeQuery()){List<Listing> out=new ArrayList<>();while(r.next())out.add(new Listing(r.getString("id"),r.getString("seller"),r.getString("serial"),catalog.card(r.getString("card")).name(),r.getLong("price")));return out;}catch(SQLException ex){throw new IllegalStateException(ex);}}
    public void buy(String buyer,String id){tx(()->{try(var s=statement("SELECT * FROM listings WHERE id=?",id);var r=s.executeQuery()){
        check(r.next(),"That listing has already closed.");String seller=r.getString("seller"),serial=r.getString("serial");long price=r.getLong("price");check(!buyer.equals(seller),"You own this listing.");Owned c=get(serial);check(c.owner.equals(seller)&&c.lock.equals("market:"+id),"Listing escrow mismatch.");long tax=Math.max(1,price/20);debit(buyer,price);credit(seller,price-tax);transfer(serial,seller,buyer,"MARKET");update("DELETE FROM listings WHERE id=?",id);update("INSERT INTO sales VALUES(?,?,?,?,?,?,?)",id,c.card,seller,buyer,price,tax,Instant.now().getEpochSecond());return null;
    }});}
    public void cancelListing(String owner,String id){tx(()->{check(update("DELETE FROM listings WHERE id=? AND seller=?",id,owner)==1,"Listing not found.");update("UPDATE cards SET lock='' WHERE owner=? AND lock=?",owner,"market:"+id);return null;});}
    public String offer(String from,String to,String give,String want,long coins){return tx(()->{
        check(!from.equals(to)&&coins>=0&&coins<=1_000_000_000L,"Invalid trade.");Owned desired=get(want);check(desired.owner.equals(to)&&desired.lock.isEmpty(),"Requested card is unavailable.");
        String id=UUID.randomUUID().toString();lock(from,give,"trade:"+id);debit(from,coins);update("INSERT INTO offers VALUES(?,?,?,?,?,?)",id,from,to,give,want,coins);return id;
    });}
    public void accept(String recipient,String id){tx(()->{try(var s=statement("SELECT * FROM offers WHERE id=? AND recipient=?",id,recipient);var r=s.executeQuery()){
        check(r.next(),"Trade offer not found.");String from=r.getString("sender"),give=r.getString("give_card"),want=r.getString("want_card");Owned offered=get(give);check(offered.owner.equals(from)&&offered.lock.equals("trade:"+id),"Trade escrow mismatch.");lock(recipient,want,"trade:"+id);transfer(give,from,recipient,"TRADE");transfer(want,recipient,from,"TRADE");credit(recipient,r.getLong("coins"));update("DELETE FROM offers WHERE id=?",id);return null;
    }});}
    public void cancelOffer(String actor,String id){tx(()->{try(var s=statement("SELECT * FROM offers WHERE id=? AND (sender=? OR recipient=?)",id,actor,actor);var r=s.executeQuery()){
        check(r.next(),"Trade offer not found.");String from=r.getString("sender");update("UPDATE cards SET lock='' WHERE serial=? AND lock=?",r.getString("give_card"),"trade:"+id);credit(from,r.getLong("coins"));update("DELETE FROM offers WHERE id=?",id);return null;
    }});}
    public synchronized Pity pity(String owner,String family){try(var s=statement("SELECT * FROM pity WHERE owner=? AND family=?",owner,family);var r=s.executeQuery()){return r.next()?new Pity(r.getInt("pulls"),r.getInt("guaranteed")!=0):new Pity(0,false);}catch(SQLException ex){throw new IllegalStateException(ex);}}
    private Pool pool(Catalog.Banner b,int pulls,boolean guarantee){
        boolean hard=pulls+1>=b.hardPity();List<Catalog.Weighted> entries=new ArrayList<>();List<Long> weights=new ArrayList<>();long total=0;
        for(var w:b.pool()){if(hard&&!w.high()||guarantee&&w.high()&&!w.featured())continue;long weight=w.weight()+(w.high()?Math.max(0,pulls-b.softPity()+1)*(long)b.softBonus():0);total=Math.addExact(total,weight);entries.add(w);weights.add(weight);}
        check(total>0,"This banner has no eligible cards.");return new Pool(entries,weights,total);
    }
    public synchronized Map<String,Double> probabilities(String owner,String bannerId){var b=catalog.banners().get(bannerId);check(b!=null,"Banner not found.");var pity=pity(owner,b.family());var pool=pool(b,pity.pulls,pity.guaranteed);Map<String,Double> odds=new LinkedHashMap<>();for(int i=0;i<pool.entries.size();i++)odds.merge(catalog.card(pool.entries.get(i).card()).name(),100.0*pool.weights.get(i)/pool.total,Double::sum);return odds;}
    public List<Pull> pull(String owner,String bannerId,String request,long now){return tx(()->{
        check(request!=null&&request.length()<=80,"Invalid request.");
        try(var s=statement("SELECT result FROM receipts WHERE owner=? AND request=?",owner,request);var r=s.executeQuery()){if(r.next())return gson.fromJson(r.getString(1),new TypeToken<List<Pull>>(){}.getType());}
        Catalog.Banner b=catalog.banners().get(bannerId);check(b!=null,"Banner not found.");check(now>=b.starts()&&(b.ends()==0||now<b.ends()),"This banner is closed.");debit(owner,b.price());Pity p=pity(owner,b.family());int pulls=p.pulls;boolean guarantee=p.guaranteed;List<Pull> results=new ArrayList<>();
        for(int slot=0;slot<b.slots();slot++) {
            var weighted=pool(b,pulls,guarantee);long roll=rng.nextLong(weighted.total);int selected=0;for(;selected<weighted.weights.size()-1;selected++){if(roll<weighted.weights.get(selected))break;roll-=weighted.weights.get(selected);}
            var result=weighted.entries.get(selected);pulls=result.high()?0:pulls+1;if(result.high())guarantee=!result.featured();String finish=result.high()?"Holo":"Normal";String serial=mint(owner,result.card(),finish,b.source());var c=catalog.card(result.card());results.add(new Pull(c.id(),c.name(),c.rarity(),finish,serial));
        }
        update("INSERT INTO pity VALUES(?,?,?,?) ON CONFLICT(owner,family) DO UPDATE SET pulls=excluded.pulls,guaranteed=excluded.guaranteed",owner,b.family(),pulls,guarantee?1:0);
        update("INSERT INTO receipts VALUES(?,?,?)",owner,request,gson.toJson(results));return List.copyOf(results);
    });}
    /** Only server world/NPC event adapters may call this; no client reward-claim packet exists. */
    public boolean progress(String owner,String rewardId,String dimension,String biome){return tx(()->{
        Catalog.Reward reward=catalog.rewards().get(rewardId);check(reward!=null,"Unknown discovery.");
        if(!reward.dimension().isEmpty()&&!reward.dimension().equals(dimension)||!reward.biome().isEmpty()&&!reward.biome().equals(biome))return false;
        if(scalar("SELECT COUNT(*) FROM profiles WHERE owner=?",owner)==0)return false;
        update("INSERT INTO progress(owner,reward,count) VALUES(?,?,1) ON CONFLICT(owner,reward) DO UPDATE SET count=MIN(count+1,?)",owner,rewardId,reward.count());
        if(scalar("SELECT count FROM progress WHERE owner=? AND reward=?",owner,rewardId)<reward.count()||scalar("SELECT claimed FROM progress WHERE owner=? AND reward=?",owner,rewardId)!=0)return false;
        update("INSERT OR IGNORE INTO supply VALUES(?,0)",rewardId);
        if(reward.supplyCap()>0&&scalar("SELECT minted FROM supply WHERE edition=?",rewardId)>=reward.supplyCap())return false;
        update("UPDATE supply SET minted=minted+1 WHERE edition=?",rewardId);mint(owner,reward.card(),"World Found",reward.source());update("UPDATE progress SET claimed=1 WHERE owner=? AND reward=?",owner,rewardId);update("UPDATE profiles SET reputation=reputation+1 WHERE owner=?",owner);return true;
    });}
    public void dealer(String owner,String dealerId,String dimension,int hour){tx(()->{
        Catalog.Dealer d=catalog.dealers().get(dealerId);check(d!=null,"Dealer not found.");boolean hours=d.startHour()<=d.endHour()?hour>=d.startHour()&&hour<d.endHour():hour>=d.startHour()||hour<d.endHour();
        check(hours&&dimension.equals(d.dimension())&&profile(owner).reputation>=d.reputation(),"This dealer is unavailable here or you need more reputation.");
        List<String> selected=new ArrayList<>();try(var s=statement("SELECT serial FROM cards WHERE owner=? AND card=? AND lock='' ORDER BY acquired LIMIT ?",owner,d.requestedCard(),d.count());var r=s.executeQuery()){while(r.next())selected.add(r.getString(1));}
        check(selected.size()==d.count(),"You do not have the requested cards.");
        // Burned cards retain provenance and cannot re-enter the economy.
        for(String serial:selected)update("UPDATE cards SET lock='retired' WHERE serial=?",serial);
        mint(owner,d.offeredCard(),"Underground","BLACK_MARKET");return null;
    });}
    public void rankedResult(String winner,String loser,String duel){tx(()->{
        String receipt="ranked:"+duel;if(scalar("SELECT COUNT(*) FROM receipts WHERE owner=? AND request=?",winner,receipt)>0)return null;
        update("UPDATE profiles SET rating=rating+16 WHERE owner=?",winner);update("UPDATE profiles SET rating=MAX(0,rating-16) WHERE owner=?",loser);update("INSERT INTO receipts VALUES(?,?,?)",winner,receipt,"[]");return null;
    });}
    public void npcVictory(String owner,String rewardId,String duel,long now){tx(()->{
        var reward=catalog.rewards().get(rewardId);check(reward!=null&&reward.source().equals("NPC_DUEL"),"Invalid NPC reward.");
        if(scalar("SELECT COUNT(*) FROM npc_rewards WHERE duel=?",duel)>0)return null;
        if(reward.dailyLimit()>0&&scalar("SELECT COUNT(*) FROM npc_rewards WHERE owner=? AND reward=? AND at>=?",owner,rewardId,now-now%86400)>=reward.dailyLimit())return null;
        update("INSERT INTO npc_rewards VALUES(?,?,?,?)",owner,rewardId,duel,now);mint(owner,reward.card(),"Normal","NPC_DUEL");credit(owner,reward.coins());return null;
    });}
    public synchronized boolean hasProfile(String owner){
        try{return scalar("SELECT COUNT(*) FROM profiles WHERE owner=?",owner)>0;}
        catch(SQLException ex){throw new IllegalStateException(ex);}
    }
    public void adminGrantCard(String owner,String cardId,int amount,String finish){tx(()->{
        check(hasProfile(owner),"PROFILE_MISSING");check(amount>=1&&amount<=1000,"INVALID_AMOUNT");
        catalog.card(cardId);check(finish!=null&&!finish.isBlank(),"INVALID_FINISH");
        for(int i=0;i<amount;i++)mint(owner,cardId,finish,"ADMIN_GRANT");return null;
    });}
    public void adminGrantCoins(String owner,long amount){tx(()->{
        check(hasProfile(owner),"PROFILE_MISSING");check(amount>0&&amount<=1_000_000_000L,"INVALID_AMOUNT");
        credit(owner,amount);return null;
    });}
    public void adminGrantDeck(String owner,String template,String deckName){tx(()->{
        check(hasProfile(owner),"PROFILE_MISSING");
        List<String> ids=catalog.starters().get(template);check(ids!=null,"INVALID_TEMPLATE");
        check(deckName!=null&&!deckName.isBlank()&&deckName.length()<=64,"INVALID_DECK_NAME");
        List<String> serials=new ArrayList<>();
        for(String id:ids)serials.add(mint(owner,id,"Normal","ADMIN_DECK:"+template));
        update("INSERT INTO decks(owner,name,main,extra) VALUES(?,?,?,?) ON CONFLICT(owner,name) DO UPDATE SET main=excluded.main,extra=excluded.extra",
            owner,deckName,gson.toJson(serials),"[]");
        update("INSERT INTO deck_formats(owner,name,format) VALUES(?,?,?) ON CONFLICT(owner,name) DO UPDATE SET format=excluded.format",
            owner,deckName,Format.CASUAL.name());
        return null;
    });}
    private static List<String> concat(List<String>a,List<String>b){List<String> out=new ArrayList<>(a);out.addAll(b);return out;}
    private static void check(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}
    @Override public synchronized void close()throws SQLException{db.close();}

    public record DuelStatistics(long wins,long losses,String lastReward){}
    public synchronized void recordDuelResult(String owner,String match,boolean won){tx(()->{update("INSERT OR IGNORE INTO duel_results VALUES(?,?,?)",owner,match,won?1:0);return null;});}
    public synchronized void lastReward(String owner,String reward){tx(()->{update("INSERT INTO last_rewards VALUES(?,?) ON CONFLICT(owner) DO UPDATE SET value=excluded.value",owner,reward);return null;});}
    public synchronized DuelStatistics duelStatistics(String owner){try(var s=statement("SELECT COALESCE(SUM(won),0),COUNT(*)-COALESCE(SUM(won),0) FROM duel_results WHERE owner=?",owner);var r=s.executeQuery()){
        r.next();String reward="";try(var q=statement("SELECT value FROM last_rewards WHERE owner=?",owner);var v=q.executeQuery()){if(v.next())reward=v.getString(1);}return new DuelStatistics(r.getLong(1),r.getLong(2),reward);
    }catch(SQLException e){throw new IllegalStateException(e);}}
    public synchronized int playersAboveRating(int rating){try(var s=statement("SELECT COUNT(*) FROM profiles WHERE rating>?",rating);var r=s.executeQuery()){r.next();return r.getInt(1);}catch(SQLException e){throw new IllegalStateException(e);}}
}
