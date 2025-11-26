package nl.jvdkolk.klaversjassentrainer.train;

import java.util.*;

/**
 * Minimal card utilities for 32-card Klaverjassen deck.
 * Card notation: rank+suit with ranks [A,K,Q,J,10,9,8,7] and suits [C,D,H,S].
 */
public final class CardUtil {
    public static final String[] SUITS = {"C", "D", "H", "S"};
    public static final String[] RANKS = {"A", "K", "Q", "J", "10", "9", "8", "7"};

    // Fixed ordering of 32 cards for vector encoding and network outputs
    public static final List<String> ALL_CARDS;
    private static final Map<String, Integer> CARD_TO_INDEX;

    static {
        List<String> cards = new ArrayList<>(32);
        for (String suit : SUITS) {
            for (String rank : RANKS) {
                cards.add(rank + suit);
            }
        }
        ALL_CARDS = Collections.unmodifiableList(cards);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < ALL_CARDS.size(); i++) {
            map.put(ALL_CARDS.get(i), i);
        }
        CARD_TO_INDEX = Collections.unmodifiableMap(map);
    }

    public static int cardIndex(String card) {
        Integer idx = CARD_TO_INDEX.get(card);
        if (idx == null) throw new IllegalArgumentException("Unknown card: " + card);
        return idx;
    }

    public static double[] encodeHand(Collection<String> hand) {
        double[] v = new double[ALL_CARDS.size()];
        for (String c : hand) {
            Integer idx = CARD_TO_INDEX.get(c);
            if (idx != null) v[idx] = 1.0;
        }
        return v;
    }

    public static double[] encodeTrick(Collection<String> trick) {
        // Aggregate one-hot of any cards visible in the current trick
        double[] v = new double[ALL_CARDS.size()];
        for (String c : trick) {
            if (c == null || c.isBlank() || c.equals("-")) continue;
            Integer idx = CARD_TO_INDEX.get(c);
            if (idx != null) v[idx] = 1.0;
        }
        return v;
    }

    /**
     * Encode current trick with order information: 4 slots x 32 one-hots.
     * Slots beyond provided cards remain zero. Cards may be null/"-" and are skipped.
     */
    public static double[] encodeTrickOrdered(List<String> trickInOrder) {
        int slots = 4;
        double[] v = new double[slots * ALL_CARDS.size()];
        if (trickInOrder == null) return v;
        int pos = 0;
        for (String c : trickInOrder) {
            if (pos >= slots) break;
            if (c != null && !c.isBlank() && !"-".equals(c)) {
                Integer idx = CARD_TO_INDEX.get(c);
                if (idx != null) {
                    v[pos * ALL_CARDS.size() + idx] = 1.0;
                }
            }
            pos++;
        }
        return v;
    }

    public static double[] encodeTrump(String trumpSuit) {
        double[] v = new double[SUITS.length];
        for (int i = 0; i < SUITS.length; i++) {
            v[i] = SUITS[i].equalsIgnoreCase(trumpSuit) ? 1.0 : 0.0;
        }
        return v;
    }

    public static String randomSuit(Random rnd) {
        return SUITS[rnd.nextInt(SUITS.length)];
    }

    public static List<String> randomHand(Random rnd, int n) {
        List<String> deck = new ArrayList<>(ALL_CARDS);
        Collections.shuffle(deck, rnd);
        return new ArrayList<>(deck.subList(0, n));
    }
}
