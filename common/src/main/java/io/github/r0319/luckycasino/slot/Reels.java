package io.github.r0319.luckycasino.slot;

import static io.github.r0319.luckycasino.slot.Symbol.BAR;
import static io.github.r0319.luckycasino.slot.Symbol.BELL;
import static io.github.r0319.luckycasino.slot.Symbol.CHERRY;
import static io.github.r0319.luckycasino.slot.Symbol.LEMON;
import static io.github.r0319.luckycasino.slot.Symbol.MELON;
import static io.github.r0319.luckycasino.slot.Symbol.SEVEN;

/**
 * 3 本のリールの絵柄の並び。1 本 16 コマで、リールのモデル（16 角柱、tools/gen_slot.py の STRIPS）と同じ。
 * コマ k は、リールの正面から上へ k × 22.5 度の位置にある。止まる位置はどのコマも同じ確率。
 */
public final class Reels {

    public static final int REELS = 3;
    public static final int STOPS = 16;

    private static final Symbol[][] STRIPS = {
            {SEVEN, CHERRY, LEMON, BELL, MELON, BAR, LEMON, CHERRY, BELL, MELON, LEMON, BAR, CHERRY, MELON, BELL, LEMON},
            {SEVEN, LEMON, MELON, CHERRY, BELL, BAR, LEMON, MELON, CHERRY, BELL, LEMON, BAR, MELON, CHERRY, LEMON, BELL},
            {SEVEN, BELL, LEMON, MELON, CHERRY, BAR, LEMON, BELL, MELON, LEMON, BAR, CHERRY, BELL, LEMON, MELON, LEMON},
    };

    private Reels() {
    }

    /** reel 本目のコマ stop の絵柄。 */
    public static Symbol at(int reel, int stop) {
        return STRIPS[reel][Math.floorMod(stop, STOPS)];
    }
}
