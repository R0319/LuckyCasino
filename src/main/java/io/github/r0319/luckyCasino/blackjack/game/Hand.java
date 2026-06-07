package io.github.r0319.luckyCasino.blackjack.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Hand {

    private final List<Card> cards = new ArrayList<>();

    public void addCard(Card card) { cards.add(card); }

    public List<Card> getCards() { return Collections.unmodifiableList(cards); }

    public void clear() { cards.clear(); }

    public int size() { return cards.size(); }

    /**
     * Calculates the optimal total.
     * Aces start as 11; each is reduced to 1 whenever the total would bust (>= 22).
     */
    public int getTotal() {
        int total = 0;
        int aces = 0;
        for (Card card : cards) {
            if (card.getRank() == Rank.ACE) {
                aces++;
                total += 11;
            } else {
                total += card.getValue();
            }
        }
        while (total >= 22 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    /** Exactly 2 cards totalling 21. */
    public boolean isBlackjack() {
        return cards.size() == 2 && getTotal() == 21;
    }

    /** Total >= 22. */
    public boolean isBust() {
        return getTotal() >= 22;
    }

    /**
     * Returns true when the hand totals exactly 17 and at least one Ace
     * is still counting as 11 (i.e. the "hard" total is less than 17).
     * Used to implement the "hit on soft 17" dealer rule.
     */
    public boolean isSoftSeventeen() {
        if (getTotal() != 17) return false;
        // Hard total: count every Ace as 1
        int hard = 0;
        for (Card card : cards) {
            hard += (card.getRank() == Rank.ACE) ? 1 : card.getValue();
        }
        return hard < 17; // at least one Ace is contributing 11
    }

    /** Legacy-coloured chat representation with running total. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) {
            sb.append(c.getDisplayName()).append(" ");
        }
        sb.append("§7(合計: §f").append(getTotal()).append("§7)");
        return sb.toString().trim();
    }
}
