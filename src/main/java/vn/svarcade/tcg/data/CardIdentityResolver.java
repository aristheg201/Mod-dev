package vn.svarcade.tcg.data;

import vn.svarcade.tcg.integration.ProviderAspects;
import java.util.*;

/** Capture identities come from the final hydrated catalog, never from a client or a species-name guess. */
public final class CardIdentityResolver {
    private final Map<String,List<Catalog.Card>> index;
    public CardIdentityResolver(Catalog catalog) {
        Map<String,List<Catalog.Card>> grouped=new TreeMap<>();
        // The provider declarations are authoritative for authored special cards.
        Map<String,SpecialAspectCards.Definition> special=new HashMap<>();
        SpecialAspectCards.definitions().forEach(d->special.put(d.id(),d));
        for(var card:catalog.cards().values())if(card.category().equals("pokemon")) {
            var definition=special.get(card.id());
            if(definition!=null&&(!canonicalSpecies(card.species()).equals(canonicalSpecies(definition.species()))
                    ||!new TreeSet<>(card.aspects()).equals(new TreeSet<>(definition.requiredAspects()))))
                throw new IllegalArgumentException("Special card identity differs from provider definition: "+card.id());
            grouped.computeIfAbsent(canonicalSpecies(card.species()),ignored->new ArrayList<>()).add(card);
        }
        grouped.replaceAll((species,cards)->{
            // Authored theme/deck-role prints with no form identity are not a second wild base species.
            // A registered registry base is the canonical base; declared provider cards are canonical
            // over their generated registry alias. All other equal-specificity choices still fail.
            String baseId=registryCardId(species,List.of());
            boolean registeredBase=cards.stream().anyMatch(c->c.id().equals(baseId)&&c.aspects().isEmpty());
            Set<List<String>> declared= new HashSet<>();
            cards.stream().filter(c->special.containsKey(c.id())).forEach(c->declared.add(validateAspects(c.aspects())));
            return cards.stream().filter(c->!registeredBase||!c.aspects().isEmpty()||c.id().equals(baseId))
                .filter(c->special.containsKey(c.id())||!declared.contains(validateAspects(c.aspects()))
                    ||!c.id().equals(registryCardId(species,c.aspects())))
                .sorted(Comparator.comparing(Catalog.Card::id)).toList();
        });
        index=Map.copyOf(grouped);
    }
    public Optional<Catalog.Card> resolve(String species,Collection<String> liveAspects) {
        Set<String> aspects=new TreeSet<>(validateAspects(liveAspects));
        List<Catalog.Card> matches=index.getOrDefault(canonicalSpecies(species),List.of()).stream()
            .filter(c->aspects.containsAll(c.aspects())).toList();
        int specificity=matches.stream().mapToInt(c->new HashSet<>(c.aspects()).size()).max().orElse(-1);
        List<Catalog.Card> best=matches.stream().filter(c->new HashSet<>(c.aspects()).size()==specificity).toList();
        if(best.size()>1)throw new IllegalArgumentException("Ambiguous Card Worlds identity: "+canonicalSpecies(species)+" "+aspects+" -> "+best.stream().map(Catalog.Card::id).toList());
        return best.stream().findFirst();
    }
    /** Provenance only records registered form/aspect identity, excluding cosmetic gender and unrelated flags. */
    public List<String> relevantAspects(String species,Collection<String> liveAspects) {
        Set<String> relevant=new TreeSet<>();
        index.getOrDefault(canonicalSpecies(species),List.of()).forEach(c->relevant.addAll(c.aspects()));
        return validateAspects(liveAspects).stream().filter(relevant::contains).toList();
    }
    public static String canonicalSpecies(String species) {
        Objects.requireNonNull(species,"species");
        String id=ProviderAspects.species(species);
        if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid species identifier");
        return id;
    }
    public static List<String> validateAspects(Collection<String> aspects) {
        Objects.requireNonNull(aspects,"aspects");
        if(aspects.size()>64)throw new IllegalArgumentException("Too many aspects");
        // Official Unown punctuation and Oricorio pa'u-style are valid Cobblemon identities.
        for(String a:aspects)if(a==null||!a.matches("[a-z0-9_.:/=!?'-]{1,96}"))throw new IllegalArgumentException("Invalid aspect");
        return List.copyOf(new TreeSet<>(aspects));
    }
    /** Single registry naming implementation, shared by catalog hydration and runtime identity infrastructure. */
    public static String registryCardId(String species,Collection<String> aspects) {
        String[] parts=canonicalSpecies(species).split(":",2);
        String base=parts[0].equals("cobblemon")?safe(parts[1]):"fakemon__"+safe(parts[0])+"__"+safe(parts[1]);
        return aspects.isEmpty()?base:base+"__form__"+safe(String.join("_",new TreeSet<>(aspects)));
    }
    private static String safe(String raw){return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+","_").replaceAll("_+","_").replaceAll("^_|_$","");}
}
