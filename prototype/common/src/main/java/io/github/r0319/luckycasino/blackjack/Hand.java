package io.github.r0319.luckycasino.blackjack;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Rank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 1 つの手札と、その手に賭けたチップ。 */
public final class Hand {

    private final List<Card> cards = new ArrayList<>();
    private long bet;
    private final boolean fromSplit;
    private boolean doubled;
    private boolean finished;

    Hand(long bet, boolean fromSplit) {
        this.bet = bet;
        this.fromSplit = fromSplit;
    }

    void add(Card card) {
        cards.add(card);
    }

    Card removeSecond() {
        return cards.remove(1);
    }

    void doubleBet() {
        bet *= 2;
        doubled = true;
    }

    void finish() {
        finished = true;
    }

    public List<Card> cards() {
        return Collections.unmodifiableList(cards);
    }

    public long bet() {
        return bet;
    }

    public boolean fromSplit() {
        return fromSplit;
    }

    public boolean doubled() {
        return doubled;
    }

    public boolean finished() {
        return finished;
    }

    /** 21 を超えない範囲で A を 1 枚だけ 11 として数えた合計。 */
    public int total() {
        return total(cards);
    }

    /** A を 11 として数えている（ソフト）か。 */
    public boolean soft() {
        return soft(cards);
    }

    /** カードの並びの合計（表示用にも使う）。 */
    public static int total(List<Card> cards) {
        int hard = hardTotal(cards);
        return hasAce(cards) && hard + 10 <= 21 ? hard + 10 : hard;
    }

    public static boolean soft(List<Card> cards) {
        return hasAce(cards) && hardTotal(cards) + 10 <= 21;
    }

    private static int hardTotal(List<Card> cards) {
        int sum = 0;
        for (Card card : cards) {
            sum += card.rank().points();
        }
        return sum;
    }

    private static boolean hasAce(List<Card> cards) {
        for (Card card : cards) {
            if (card.rank() == Rank.ACE) {
                return true;
            }
        }
        return false;
    }

    public boolean bust() {
        return total() > 21;
    }

    /** 最初の 2 枚で 21。スプリットで作った 21 は含まない。 */
    public boolean blackjack() {
        return !fromSplit && cards.size() == 2 && total() == 21;
    }

    boolean splitAces() {
        return fromSplit && !cards.isEmpty() && cards.getFirst().rank() == Rank.ACE;
    }
}
