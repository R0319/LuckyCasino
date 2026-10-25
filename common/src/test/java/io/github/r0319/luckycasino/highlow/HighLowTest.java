package io.github.r0319.luckycasino.highlow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighLowTest {

    @Test
    void payoutsFollowTheOdds() {
        // 基準 7: 上は 8〜K の 6 種類 → 13/6 x 0.95 = 2.058 → 2.05 倍
        assertEquals(205, HighLow.percent(HighLow.Bet.HIGH, 7));
        assertEquals(205, HighLow.percent(HighLow.Bet.LOW, 7));
        assertEquals(1235, HighLow.percent(HighLow.Bet.SAME, 7));
        assertEquals(0, HighLow.percent(HighLow.Bet.LOW, 1));
        assertEquals(0, HighLow.percent(HighLow.Bet.HIGH, 13));
        assertEquals(205, HighLow.payout(HighLow.Bet.HIGH, 7, 8, 100));
        assertEquals(0, HighLow.payout(HighLow.Bet.HIGH, 7, 7, 100));
        assertEquals(1235, HighLow.payout(HighLow.Bet.SAME, 7, 7, 100));
        assertEquals("2.05 倍", HighLow.percentText(205));
        assertEquals("1.02 倍", HighLow.percentText(102));
    }

    /** どの基準・どの賭けでも、還元率は 95% 以下で 90% 以上。 */
    @Test
    void returnIsAtMostNinetyFivePercent() {
        for (int base = 1; base <= 13; base++) {
            for (HighLow.Bet bet : HighLow.Bet.values()) {
                if (HighLow.wins(bet, base) == 0) {
                    continue;
                }
                long back = 0;
                for (int next = 1; next <= 13; next++) {
                    back += HighLow.payout(bet, base, next, 10000);
                }
                double rtp = back / 13.0 / 10000;
                assertTrue(rtp <= 0.95 && rtp > 0.9, bet + " " + base + " " + rtp);
            }
        }
    }
}
