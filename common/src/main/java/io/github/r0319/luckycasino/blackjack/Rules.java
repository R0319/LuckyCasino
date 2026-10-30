package io.github.r0319.luckycasino.blackjack;

/**
 * テーブルごとのルール。
 *
 * @param decks           使うトランプの組数
 * @param penetration     この割合まで配ったら次の勝負の前に切り直す
 * @param dealerHitsSoft17 ディーラーがソフト 17 で引くか
 * @param doubleAfterSplit スプリットした手でダブルダウンできるか（A を分けた手は除く）
 * @param minBet          1 手の最低額
 * @param maxBet          1 手の最高額
 */
public record Rules(int decks, double penetration, boolean dealerHitsSoft17, boolean doubleAfterSplit,
                    long minBet, long maxBet) {

    public static final Rules DEFAULT = new Rules(6, 0.75, false, true, 10, 1000);

    public Rules {
        if (minBet < 1 || maxBet < minBet) {
            throw new IllegalArgumentException("invalid bet limits: " + minBet + ".." + maxBet);
        }
    }
}
