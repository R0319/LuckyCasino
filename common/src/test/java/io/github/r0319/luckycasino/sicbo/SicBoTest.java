package io.github.r0319.luckycasino.sicbo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SicBoTest {

    @Test
    void smallAndBigLoseOnTriple() {
        SicBo r = new SicBo(2, 2, 2);
        assertEquals(0, r.payout("small", 10));
        assertEquals(0, r.payout("even", 10));
        assertEquals(310, r.payout("any_triple", 10));
        assertEquals(1810, r.payout("triple_2", 10));
        assertEquals(0, r.payout("triple_3", 10));
        assertEquals(110, r.payout("double_2", 10));
        assertEquals(40, r.payout("single_2", 10));
    }

    @Test
    void ordinaryRoll() {
        SicBo r = new SicBo(1, 4, 4);
        assertEquals(9, r.total());
        assertEquals(20, r.payout("small", 10));
        assertEquals(0, r.payout("big", 10));
        assertEquals(20, r.payout("odd", 10));
        assertEquals(0, r.payout("even", 10));
        assertEquals(70, r.payout("total_9", 10));
        assertEquals(0, r.payout("total_10", 10));
        assertEquals(110, r.payout("double_4", 10));
        assertEquals(30, r.payout("single_4", 10));
        assertEquals(20, r.payout("single_1", 10));
        assertEquals(0, r.payout("single_6", 10));
        assertEquals(0, r.payout("any_triple", 10));
    }

    @Test
    void rejectsUnknownBets() {
        assertFalse(SicBo.valid("total_3"));
        assertFalse(SicBo.valid("single_7"));
        assertFalse(SicBo.valid("foo"));
        assertTrue(SicBo.valid("total_17"));
        assertThrows(IllegalArgumentException.class, () -> new SicBo(0, 1, 2));
    }

    /** どの賭けも、全 216 通りで均すと店が少し勝つ（還元率 100% 未満、80% 以上）。 */
    @Test
    void everyBetFavoursTheHouse() {
        List<String> bets = new ArrayList<>(List.of("small", "big", "odd", "even", "any_triple"));
        for (int n = 1; n <= 6; n++) {
            bets.add("triple_" + n);
            bets.add("double_" + n);
            bets.add("single_" + n);
        }
        for (int t = 4; t <= 17; t++) {
            bets.add("total_" + t);
        }
        for (String bet : bets) {
            long back = 0;
            for (int a = 1; a <= 6; a++) {
                for (int b = 1; b <= 6; b++) {
                    for (int c = 1; c <= 6; c++) {
                        back += new SicBo(a, b, c).payout(bet, 1);
                    }
                }
            }
            double rtp = back / 216.0;
            assertTrue(rtp < 1 && rtp > 0.8, bet + " rtp " + rtp);
        }
    }
}
