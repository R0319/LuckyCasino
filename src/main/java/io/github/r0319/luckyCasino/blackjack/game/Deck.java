package io.github.r0319.luckyCasino.blackjack.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {

    private final List<Card> cards = new ArrayList<>(52);

    public Deck() {
        reset();
    }

    /** Repopulates the 52-card deck and shuffles it. */
    public void reset() {
        cards.clear();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
        Collections.shuffle(cards);
    }

    /**
     * Draws the top card.
     * Automatically resets and reshuffles when the deck is exhausted.
     */
    public Card draw() {
        if (cards.isEmpty()) reset();
        return cards.remove(cards.size() - 1);
    }

    public int remaining() { return cards.size(); }
}
