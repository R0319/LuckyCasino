package io.github.r0319.luckyCasino.blackjack.game;

public class Card {

    private final Suit suit;
    private final Rank rank;

    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() { return suit; }
    public Rank getRank() { return rank; }

    /** Base point value as defined by Rank (Ace = 11 before hand adjustment). */
    public int getValue() { return rank.getValue(); }

    /** Coloured legacy-chat display string: e.g. "§cA♥" */
    public String getDisplayName() {
        String color = suit.isRed() ? "§c" : "§8";
        return color + rank.getSymbol() + suit.getSymbol();
    }

    /** Plain string for data purposes: e.g. "A♠" */
    @Override
    public String toString() {
        return rank.getSymbol() + suit.getSymbol();
    }
}
