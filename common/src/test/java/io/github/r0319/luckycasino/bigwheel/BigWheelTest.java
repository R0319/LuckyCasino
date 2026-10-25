package io.github.r0319.luckycasino.bigwheel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BigWheelTest {

    @Test
    void segmentCounts() {
        assertEquals(24, BigWheel.count(BigWheel.Segment.ONE));
        assertEquals(15, BigWheel.count(BigWheel.Segment.TWO));
        assertEquals(7, BigWheel.count(BigWheel.Segment.FIVE));
        assertEquals(4, BigWheel.count(BigWheel.Segment.TEN));
        assertEquals(2, BigWheel.count(BigWheel.Segment.TWENTY));
        assertEquals(1, BigWheel.count(BigWheel.Segment.JOKER));
        assertEquals(1, BigWheel.count(BigWheel.Segment.LOGO));
    }

    @Test
    void sameSymbolsNeverTouchExceptOnes() {
        for (int i = 0; i < BigWheel.SEGMENTS; i++) {
            BigWheel.Segment a = BigWheel.at(i);
            if (a != BigWheel.Segment.ONE) {
                assertTrue(a != BigWheel.at(i + 1), "マス " + i + " と次が同じ " + a);
            }
        }
    }

    @Test
    void payouts() {
        int joker = 0;
        assertEquals(BigWheel.Segment.JOKER, BigWheel.at(joker));
        assertEquals(410, BigWheel.payout(BigWheel.Segment.JOKER, 10, joker));
        assertEquals(0, BigWheel.payout(BigWheel.Segment.ONE, 10, joker));
        assertEquals(20, BigWheel.payout(BigWheel.Segment.ONE, 10, 1));
        assertEquals(30, BigWheel.payout(BigWheel.Segment.TWO, 10, 2));
    }

    @Test
    void everyBetFavoursTheHouse() {
        for (BigWheel.Segment s : BigWheel.Segment.values()) {
            double back = (double) BigWheel.count(s) * (s.pays() + 1) / BigWheel.SEGMENTS;
            assertTrue(back < 1 && back > 0.7, s + " の還元率 " + back);
        }
    }
}
