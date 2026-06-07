package io.github.r0319.luckyCasino.blackjack.game;

public enum Suit {
    SPADES("♠", false),
    HEARTS("♥", true),
    DIAMONDS("♦", true),
    CLUBS("♣", false);

    private final String symbol;
    /** true = red suit, false = black suit */
    private final boolean red;

    Suit(String symbol, boolean red) {
        this.symbol = symbol;
        this.red = red;
    }

    public String getSymbol() { return symbol; }
    public boolean isRed() { return red; }
}
