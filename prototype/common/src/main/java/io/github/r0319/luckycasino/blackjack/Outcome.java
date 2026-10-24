package io.github.r0319.luckycasino.blackjack;

public enum Outcome {
    /** 最初の 2 枚で 21。配当 3:2 */
    BLACKJACK,
    /** ディーラーより 21 に近い、またはディーラーがバースト。配当 1:1 */
    WIN,
    /** 同点。賭け金が戻る */
    PUSH,
    /** ディーラーの方が 21 に近い */
    LOSE,
    /** 22 以上 */
    BUST;

    /**
     * 戻ってくるチップ（賭け金を含む）。3:2 で端数が出るときは切り捨てる。
     */
    public long payout(long bet) {
        return switch (this) {
            case BLACKJACK -> bet + bet * 3 / 2;
            case WIN -> bet * 2;
            case PUSH -> bet;
            case LOSE, BUST -> 0;
        };
    }
}
