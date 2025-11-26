package nl.jvdkolk.klaversjassentrainer.service;

import nl.jvdkolk.klaversjassentrainer.api.BestCardRequest;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LegalityService {
    private static final List<String> TRUMP_ORDER = List.of("J","9","A","10","K","Q","8","7");
    private static final List<String> NON_TRUMP_ORDER = List.of("A","K","Q","J","10","9","8","7");

    public List<String> computeLegalCards(List<String> hand,
                                          List<BestCardRequest.Play> table,
                                          String trump,
                                          int partnerPosition,
                                          int leaderPosition) {
        List<String> legal = new ArrayList<>();
        List<BestCardRequest.Play> actualTable = new ArrayList<>();
        if (table != null) {
            for (BestCardRequest.Play p : table) {
                if (p != null && p.getCard() != null && !p.getCard().isBlank() && !"-".equals(p.getCard())) {
                    actualTable.add(p);
                }
            }
        }
        if (actualTable.isEmpty()) {
            legal.addAll(hand);
            return legal;
        }
        String ledSuit = suit(actualTable.get(0).getCard());
        List<String> followSuit = filterBySuit(hand, ledSuit);
        if (!followSuit.isEmpty()) {
            return followSuit;
        }

        // Player is void in led suit
        // Determine current winner and if partner is winning
        BestCardRequest.Play winner = currentWinner(actualTable, ledSuit, trump);
        boolean partnerWinning = (winner.getPlayer() != null && winner.getPlayer() == partnerPosition);
        if (partnerWinning) {
            // may discard any non-trump, else any card
            List<String> nonTrump = new ArrayList<>();
            for (String c : hand) if (!suit(c).equals(trump)) nonTrump.add(c);
            if (!nonTrump.isEmpty()) return nonTrump;
            return new ArrayList<>(hand);
        } else {
            // must play trump if you have it; if opponents trumped already, try to overtrump
            List<String> trumpsInHand = filterBySuit(hand, trump);
            if (trumpsInHand.isEmpty()) return new ArrayList<>(hand);

            boolean trumpOnTable = actualTable.stream().anyMatch(p -> suit(p.getCard()).equals(trump));
            if (!trumpOnTable) {
                return trumpsInHand;
            }
            // Trump on table: must overtrump if possible
            String winningCard = winner.getCard();
            if (suit(winningCard).equals(trump)) {
                int winRank = trumpRankIndex(winningCard);
                List<String> over = new ArrayList<>();
                for (String c : trumpsInHand) if (trumpRankIndex(c) < winRank) over.add(c); // lower index = higher
                if (!over.isEmpty()) return over;
            }
            return trumpsInHand;
        }
    }

    private static List<String> filterBySuit(List<String> cards, String suit) {
        List<String> out = new ArrayList<>();
        for (String c : cards) if (suit(c).equals(suit)) out.add(c);
        return out;
    }

    private static BestCardRequest.Play currentWinner(List<BestCardRequest.Play> table, String ledSuit, String trump) {
        BestCardRequest.Play best = table.get(0);
        for (int i = 1; i < table.size(); i++) {
            BestCardRequest.Play p = table.get(i);
            if (beats(p.getCard(), best.getCard(), ledSuit, trump)) best = p;
        }
        return best;
    }

    private static boolean beats(String a, String b, String ledSuit, String trump) {
        String sa = suit(a), sb = suit(b);
        boolean aTrump = sa.equals(trump), bTrump = sb.equals(trump);
        if (aTrump && !bTrump) return true;
        if (!aTrump && bTrump) return false;
        if (aTrump) {
            return trumpRankIndex(a) < trumpRankIndex(b);
        }
        // neither trump
        boolean aLed = sa.equals(ledSuit), bLed = sb.equals(ledSuit);
        if (aLed && !bLed) return true;
        if (!aLed && bLed) return false;
        if (aLed && bLed) return nonTrumpRankIndex(a) < nonTrumpRankIndex(b);
        return false; // off-suit, neither led nor trump beats
    }

    private static String suit(String card) {
        return card.substring(card.length() - 1);
    }

    private static String rank(String card) {
        return card.length() == 3 ? "10" : card.substring(0, 1);
    }

    private static int trumpRankIndex(String card) {
        return TRUMP_ORDER.indexOf(rank(card));
    }

    private static int nonTrumpRankIndex(String card) {
        return NON_TRUMP_ORDER.indexOf(rank(card));
    }
}
