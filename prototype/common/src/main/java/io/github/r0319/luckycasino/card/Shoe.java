package io.github.r0319.luckycasino.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 何組かのトランプを混ぜた山（シュー）。残りが一定の割合を切ったら、勝負と勝負の間で切り直す。
 */
public final class Shoe {

    private final int decks;
    private final double penetration;
    private final Random random;
    private final List<Card> cards = new ArrayList<>();
    private final boolean stacked;

    /**
     * @param penetration 全体のうち配ったら切り直す割合（例: 0.75）
     * @param random      結果を決める乱数。本番では SecureRandom を渡す
     */
    public Shoe(int decks, double penetration, Random random) {
        if (decks < 1) {
            throw new IllegalArgumentException("decks must be >= 1");
        }
        if (penetration <= 0 || penetration >= 1) {
            throw new IllegalArgumentException("penetration must be between 0 and 1");
        }
        this.decks = decks;
        this.penetration = penetration;
        this.random = random;
        this.stacked = false;
        shuffle();
    }

    private Shoe(List<Card> topFirst) {
        this.decks = 1;
        this.penetration = 0.5;
        this.random = new Random(0);
        this.stacked = true;
        this.cards.addAll(topFirst.reversed());
    }

    /** 上から順に決まったカードが出る山（テスト用）。切り直しはしない。 */
    public static Shoe stacked(List<Card> topFirst) {
        return new Shoe(topFirst);
    }

    public void shuffle() {
        cards.clear();
        for (int i = 0; i < decks; i++) {
            for (Suit suit : Suit.values()) {
                for (Rank rank : Rank.values()) {
                    cards.add(new Card(suit, rank));
                }
            }
        }
        Collections.shuffle(cards, random);
    }

    /** 勝負の前に呼ぶ。切り直しの目安を過ぎていたら混ぜ直して true を返す。 */
    public boolean shuffleIfNeeded() {
        if (!stacked && remaining() < decks * 52 * (1 - penetration)) {
            shuffle();
            return true;
        }
        return false;
    }

    public Card draw() {
        if (cards.isEmpty()) {
            if (stacked) {
                throw new IllegalStateException("stacked shoe is empty");
            }
            // 1 回の勝負で山を使い切ることは通常ないが、念のため混ぜ直して続ける
            shuffle();
        }
        return cards.removeLast();
    }

    public int remaining() {
        return cards.size();
    }
}
