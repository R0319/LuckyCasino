package io.github.r0319.luckycasino.poker;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Rank;
import io.github.r0319.luckycasino.poker.HandValue.Category;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 5〜7 枚から一番強い 5 枚の役を求める。 */
public final class HandEvaluator {

    private HandEvaluator() {
    }

    /** ポーカーでの数字の強さ（2〜14、A が 14）。 */
    public static int value(Rank rank) {
        return rank == Rank.ACE ? 14 : rank.ordinal() + 1;
    }

    public static HandValue best(List<Card> cards) {
        if (cards.size() < 5 || cards.size() > 7) {
            throw new IllegalArgumentException("need 5 to 7 cards");
        }
        HandValue best = null;
        int n = cards.size();
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                for (int c = b + 1; c < n; c++) {
                    for (int d = c + 1; d < n; d++) {
                        for (int e = d + 1; e < n; e++) {
                            HandValue value = evaluate5(List.of(cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e)));
                            if (best == null || value.score() > best.score()) {
                                best = value;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    static HandValue evaluate5(List<Card> five) {
        List<Card> sorted = new ArrayList<>(five);
        sorted.sort(Comparator.comparingInt((Card c) -> value(c.rank())).reversed());
        int[] counts = new int[15];
        for (Card card : sorted) {
            counts[value(card.rank())]++;
        }
        boolean flush = sorted.stream().map(Card::suit).distinct().count() == 1;
        int straightTop = straightTop(counts);

        // 枚数の多い順、同じなら数字の大きい順に並べた数字
        List<Integer> groups = new ArrayList<>();
        for (int count = 4; count >= 1; count--) {
            for (int v = 14; v >= 2; v--) {
                if (counts[v] == count) {
                    groups.add(v);
                }
            }
        }
        int top = counts[groups.getFirst()];
        int second = groups.size() > 1 ? counts[groups.get(1)] : 0;

        Category category;
        List<Integer> kickers;
        if (straightTop > 0 && flush) {
            category = Category.STRAIGHT_FLUSH;
            kickers = List.of(straightTop);
        } else if (top == 4) {
            category = Category.FOUR_OF_A_KIND;
            kickers = groups;
        } else if (top == 3 && second == 2) {
            category = Category.FULL_HOUSE;
            kickers = groups;
        } else if (flush) {
            category = Category.FLUSH;
            kickers = groups;
        } else if (straightTop > 0) {
            category = Category.STRAIGHT;
            kickers = List.of(straightTop);
        } else if (top == 3) {
            category = Category.THREE_OF_A_KIND;
            kickers = groups;
        } else if (top == 2 && second == 2) {
            category = Category.TWO_PAIR;
            kickers = groups;
        } else if (top == 2) {
            category = Category.ONE_PAIR;
            kickers = groups;
        } else {
            category = Category.HIGH_CARD;
            kickers = groups;
        }
        long score = (long) category.ordinal() << 20;
        for (int i = 0; i < kickers.size(); i++) {
            score |= (long) kickers.get(i) << (16 - 4 * i);
        }
        return new HandValue(category, score, sorted);
    }

    /** 5 つ連続していれば一番上の数字（A・2・3・4・5 は 5）、でなければ 0。 */
    private static int straightTop(int[] counts) {
        for (int top = 14; top >= 6; top--) {
            boolean ok = true;
            for (int v = top; v > top - 5; v--) {
                if (counts[v] != 1) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return top;
            }
        }
        if (counts[14] == 1 && counts[2] == 1 && counts[3] == 1 && counts[4] == 1 && counts[5] == 1) {
            return 5;
        }
        return 0;
    }
}
