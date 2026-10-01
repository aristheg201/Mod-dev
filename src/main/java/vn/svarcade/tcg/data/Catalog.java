package vn.svarcade.tcg.data;

import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Content snapshot: a duel retains the same catalog throughout its lifetime. */
public record Catalog(Rules rules, Map<String, Card> cards, Map<String, Banner> banners,
                      Map<String, Reward> rewards, Map<String, Dealer> dealers, Map<String, List<String>> starters) {
    public record Rules(int minDeck, int maxDeck, int extraDeck, int copies, int hand, int life,
                        int pokemonZones, int supportZones, int normalSummons, int typeBonus,
                        Map<String,Integer> rankedLimits, Map<String,List<String>> effective) {}
    public record Effect(String operation, int amount, int speed, int lifeCost, String target,
                         List<String> phases, boolean oncePerTurn, EffectSpec spec) {
        public Effect(String operation,int amount,int speed,int lifeCost,String target,List<String> phases,boolean oncePerTurn) {
            this(operation,amount,speed,lifeCost,target,phases,oncePerTurn,null);
        }
    }
    public record Trigger(String cause,String zone,String relation,Effect effect) {}
    public record Modifier(String zone,String affectedType,int power) {}
    public record Card(String id, String name, String category, String species, List<String> aspects,
                       String type, String family, String evolvesFrom, boolean extra, int level, int power,
                       String text, String set, String rarity, List<String> sources, Effect effect,
                       List<Trigger> triggers,List<Modifier> modifiers) {
        public int tributeCount() {
            if(!category.equals("pokemon") || extra || level <= 4) return 0;
            return level <= 6 ? 1 : 2;
        }
        public String summonRequirement() {
            if(!category.equals("pokemon")) return "";
            if(extra) return "Extra Evolution";
            int tributes=tributeCount();
            return tributes==0 ? "No Tribute" : tributes==1 ? "1 Tribute" : "2 Tributes";
        }
    }
    public record Weighted(String card, int weight, boolean featured, boolean high) {}
    public record Banner(String name, String family, String source, int price, int slots,
                         int hardPity, int softPity, int softBonus, long starts, long ends,
                         List<Weighted> pool) {}
    public record Reward(String name, String source, String dimension, String biome, int count,
                         String card, int supplyCap, int coins, int dailyLimit) {}
    public record Dealer(String name, String dimension, int startHour, int endHour, int reputation,
                         String requestedCard, int count, String offeredCard) {}

    public static Catalog load(Path override) throws IOException {
        return CatalogMigration.load(override);
    }
    public Card card(String id) { Card c = cards.get(id); if(c == null) throw new IllegalArgumentException("Unknown card: " + id); return c; }
    public void validate() {
        Objects.requireNonNull(rules); Objects.requireNonNull(cards);
        if(rules.minDeck < rules.hand || rules.maxDeck < rules.minDeck || rules.life <= 0 || rules.pokemonZones < 1 || rules.supportZones < 1 || rules.copies < 1) throw new IllegalArgumentException("Invalid format");
        Set<String> ops = Set.of("composite", "damage", "draw", "heal", "destroy", "banish", "return", "revive", "negate_effect", "negate_activation", "shield", "boost");
        for(var e: cards.entrySet()) {
            Card c=e.getValue();
            if(!e.getKey().equals(c.id) || c.power < 0 || c.sources.isEmpty()) throw new IllegalArgumentException("Invalid card " + e.getKey());
            if(c.category.equals("pokemon") && (c.level < 1 || c.level > 12)) throw new IllegalArgumentException("Pokemon level must be 1–12: " + c.id);
            if(!c.category.equals("pokemon") && c.level != 0) throw new IllegalArgumentException("Only Pokemon cards have levels: " + c.id);
            if(c.evolvesFrom != null && !c.evolvesFrom.isBlank()) card(c.evolvesFrom);
            if(c.effect != null && (!ops.contains(c.effect.operation) || c.effect.speed < 1 || c.effect.speed > 3 || c.effect.lifeCost < 0 || c.effect.amount < 0)) throw new IllegalArgumentException("Invalid effect " + c.id);
            if(c.effect!=null&&c.effect.spec()!=null){try{c.effect.spec().validate();}catch(RuntimeException validationError){throw new IllegalArgumentException(c.id+": effect: "+validationError.getMessage(),validationError);}}
            if(c.triggers!=null)for(var t:c.triggers){if(!Set.of("PLAY","SET","TRIBUTE_SUMMON","SPECIAL_SUMMON","SUMMON_STAGE","EVOLVE","EXTRA_SUMMON","DRAW","DESTROY","BATTLE","BANISH","RETURN","REVIVE","EFFECT","DISCARD","SYSTEM").contains(t.cause)||!Set.of("FIELD","STADIUM","DISCARD","SUPPORT").contains(t.zone)||!Set.of("self","ally","enemy","any").contains(t.relation)||t.effect==null||!Set.of("draw","damage","heal").contains(t.effect.operation)||!t.effect.target.equals("none")||t.effect.lifeCost!=0||t.effect.amount<0)throw new IllegalArgumentException("Invalid mandatory trigger "+c.id);}
            if(c.modifiers!=null)for(var m:c.modifiers)if(!Set.of("FIELD","STADIUM","SUPPORT").contains(m.zone)||m.power<0||m.power>10000)throw new IllegalArgumentException("Invalid continuous modifier "+c.id);
        }
        for(var b:banners.values()) {
            if(b.price < 0 || b.slots < 1 || b.slots > 10 || b.hardPity < 1 || b.softPity < 0 || b.softBonus < 0 || b.pool.isEmpty()) throw new IllegalArgumentException("Invalid banner");
            boolean high=false,featured=false;
            for(var w:b.pool) { if(w.weight < 1 || !card(w.card).sources.contains(b.source)) throw new IllegalArgumentException("Illegal acquisition pool: " + w.card); high |= w.high; featured |= w.high && w.featured; }
            if(!high || !featured) throw new IllegalArgumentException("Banner cannot fulfill guarantees");
        }
        for(var reward:rewards.values()) if(reward.count < 1 || !card(reward.card).sources.contains(reward.source)) throw new IllegalArgumentException("Invalid reward");
        for(var dealer:dealers.values()) { card(dealer.requestedCard); if(dealer.count < 1 || !card(dealer.offeredCard).sources.contains("BLACK_MARKET")) throw new IllegalArgumentException("Invalid dealer"); }
        for(var starter:starters.values()) { if(starter.size() < rules.minDeck || starter.size() > rules.maxDeck) throw new IllegalArgumentException("Invalid starter size"); starter.forEach(this::card); }
    }
    public List<String> deckErrors(List<String> main, List<String> extra, boolean ranked) {
        List<String> errors=new ArrayList<>();
        if(main.size()<rules.minDeck || main.size()>rules.maxDeck) errors.add("Main Deck requires " + rules.minDeck + "–" + rules.maxDeck + " cards.");
        if(extra.size()>rules.extraDeck) errors.add("Extra Deck is too large.");
        Map<String,Integer> counts=new HashMap<>();
        for(String id:main) { Card c=card(id); if(c.extra) errors.add(c.name+" belongs in the Extra Deck."); counts.merge(id,1,Integer::sum); }
        for(String id:extra) { Card c=card(id); if(!c.extra) errors.add(c.name+" belongs in the Main Deck."); counts.merge(id,1,Integer::sum); }
        counts.forEach((id,n)->{ if(n>(ranked?rules.rankedLimits.getOrDefault(id,rules.copies):rules.copies)) errors.add(card(id).name+": copy limit exceeded."); });
        return List.copyOf(errors);
    }
}
