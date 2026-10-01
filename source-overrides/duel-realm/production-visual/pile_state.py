"""Small final overlay on the restored duel: public pile counts, never hidden identities."""
from pathlib import Path

path = Path('src/main/java/vn/svarcade/tcg/duel/Duel.java')
source = path.read_text()
if 'List<Integer> extraCounts' not in source:
    source = source.replace('List<Integer> handCounts, List<VisibleCard> cards,',
                            'List<Integer> handCounts, List<Integer> extraCounts, List<VisibleCard> cards,')
    old = 'List.of(count(0,Zone.HAND),count(1,Zone.HAND)),visible,'
    assert source.count(old) == 2, 'Expected duelist and spectator view constructors'
    source = source.replace(old,
        'List.of(count(0,Zone.HAND),count(1,Zone.HAND)),List.of(count(0,Zone.EXTRA),count(1,Zone.EXTRA)),orderPublicPiles(visible),')
    source = source.replace('    public synchronized View view(int viewer) {', '''    /** Public piles are ordered by arrival; the last visible card is the actual top card. */
    private List<VisibleCard> orderPublicPiles(List<VisibleCard> cards) {
        Map<String,Long> arrival = new HashMap<>();
        for (Event event : history) if (event.to() == Zone.DISCARD || event.to() == Zone.BANISHED)
            arrival.put(event.card(), event.sequence());
        return cards.stream().sorted(Comparator.comparingLong(c ->
            c.zone() == Zone.DISCARD || c.zone() == Zone.BANISHED ? arrival.getOrDefault(c.token(), 0L) : -1L)).toList();
    }
    public synchronized View view(int viewer) {''')

if 'QA_PILE_COUNTS_READY' not in source:
    anchor = '        note("QA position scenario ready: Attack Pokemon, face-up Defense card, and real Normal Set face-down Defense card.");'
    assert anchor in source, 'Position scenario anchor changed'
    source = source.replace(anchor, '''        // Real catalog cards populate all public/hidden pile zones for visual proof.
        for (int seat = 0; seat < 2; seat++) {
            for (int i = 0; i < 12; i++) qaAdd(seat, "charmander", Zone.DECK);
            qaAdd(seat, "arceus_defense", Zone.EXTRA);
            qaAdd(seat, "arceus_judgement", Zone.EXTRA);
            qaAdd(seat, "ultimate_arceus", Zone.EXTRA);
            qaAdd(seat, "protect", Zone.DISCARD);
            qaAdd(seat, "flamethrower", Zone.DISCARD);
            qaAdd(seat, "pikachu", Zone.BANISHED);
        }
        note("QA_PILE_COUNTS_READY: both sides have Deck, Extra Deck, Graveyard and Banished cards.");
        note("QA position scenario ready: Attack Pokemon, face-up Defense Pokemon, and real Normal Set face-down Defense card.");''')
path.write_text(source)
