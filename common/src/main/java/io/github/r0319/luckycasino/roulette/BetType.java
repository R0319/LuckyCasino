package io.github.r0319.luckycasino.roulette;

/** 賭け方と配当（勝ったとき賭け金の何倍をもらえるか。賭け金は別に戻る）。 */
public enum BetType {
    STRAIGHT(35, "ストレートアップ"),
    SPLIT(17, "スプリット"),
    STREET(11, "ストリート"),
    CORNER(8, "コーナー"),
    LINE(5, "ライン"),
    DOZEN(2, "ダズン"),
    COLUMN(2, "コラム"),
    RED(1, "赤"),
    BLACK(1, "黒"),
    EVEN(1, "偶数"),
    ODD(1, "奇数"),
    LOW(1, "1〜18"),
    HIGH(1, "19〜36");

    private final int payout;
    private final String displayName;

    BetType(int payout, String displayName) {
        this.payout = payout;
        this.displayName = displayName;
    }

    public int payout() {
        return payout;
    }

    public String displayName() {
        return displayName;
    }
}
