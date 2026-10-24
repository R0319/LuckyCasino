package io.github.r0319.luckycasino.card;

import io.github.r0319.luckycasino.bridge.CardCode;

public record Card(Suit suit, Rank rank) {

    /** CardCode の番号（1〜52）。 */
    public int code() {
        return suit.ordinal() * 13 + rank.ordinal() + 1;
    }

    public static Card fromCode(int code) {
        if (code < 1 || code > CardCode.MAX) {
            throw new IllegalArgumentException("invalid card code: " + code);
        }
        return new Card(Suit.values()[(code - 1) / 13], Rank.values()[(code - 1) % 13]);
    }

    public String modelName() {
        return CardCode.modelName(code());
    }
}
