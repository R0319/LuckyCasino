package io.github.r0319.luckycasino.poker;

import io.github.r0319.luckycasino.card.Card;

import java.util.List;

/**
 * 5 枚の役の強さ。score が大きいほど強く、同じなら引き分け。
 *
 * @param best 役を作った 5 枚
 */
public record HandValue(Category category, long score, List<Card> best) implements Comparable<HandValue> {

    public enum Category {
        HIGH_CARD("ハイカード"),
        ONE_PAIR("ワンペア"),
        TWO_PAIR("ツーペア"),
        THREE_OF_A_KIND("スリーカード"),
        STRAIGHT("ストレート"),
        FLUSH("フラッシュ"),
        FULL_HOUSE("フルハウス"),
        FOUR_OF_A_KIND("フォーカード"),
        STRAIGHT_FLUSH("ストレートフラッシュ");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public HandValue {
        best = List.copyOf(best);
    }

    /** 表示名。A が一番上のストレートフラッシュはロイヤルフラッシュ。 */
    public String displayName() {
        if (category == Category.STRAIGHT_FLUSH && ((score >> 16) & 0xF) == 14) {
            return "ロイヤルフラッシュ";
        }
        return category.displayName();
    }

    @Override
    public int compareTo(HandValue other) {
        return Long.compare(score, other.score);
    }
}
