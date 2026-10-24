package io.github.r0319.luckycasino.bridge;

import java.util.List;

/**
 * カードの番号。0 は「裏向きで中身を知らせない」、1〜52 はスート * 13 + ランク + 1。
 * 並び順はリソースパック生成スクリプト（tools/gen_cards.py）と一致させること。
 */
public final class CardCode {

    public static final int HIDDEN = 0;
    public static final int MAX = 52;

    private static final List<String> SUITS = List.of("spade", "heart", "diamond", "club");
    private static final List<String> RANKS = List.of("a", "2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k");

    private CardCode() {
    }

    public static boolean isValid(int code) {
        return code >= HIDDEN && code <= MAX;
    }

    /** リソースパック内のモデル名（例: spade_a）。HIDDEN は back。 */
    public static String modelName(int code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("invalid card code: " + code);
        }
        if (code == HIDDEN) {
            return "back";
        }
        int index = code - 1;
        return SUITS.get(index / 13) + "_" + RANKS.get(index % 13);
    }

    /** モデル名から番号へ。見つからなければ -1。 */
    public static int fromModelName(String name) {
        for (int code = 1; code <= MAX; code++) {
            if (modelName(code).equals(name)) {
                return code;
            }
        }
        return -1;
    }
}
