package io.github.r0319.luckycasino.card;

/** 並び順は CardCode とリソースパック（tools/gen_cards.py）と同じにすること。 */
public enum Rank {
    ACE(1), TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9), TEN(10),
    JACK(10), QUEEN(10), KING(10);

    /** ブラックジャックの点数（A は 1 として持ち、11 にするかは Hand が決める）。 */
    private final int points;

    Rank(int points) {
        this.points = points;
    }

    public int points() {
        return points;
    }
}
