package io.github.r0319.luckycasino.slot;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotTest {

    @Test
    void stripsHaveTheIntendedCounts() {
        for (int reel = 0; reel < Reels.REELS; reel++) {
            Map<Symbol, Integer> counts = new EnumMap<>(Symbol.class);
            for (int stop = 0; stop < Reels.STOPS; stop++) {
                counts.merge(Reels.at(reel, stop), 1, Integer::sum);
            }
            assertEquals(1, counts.get(Symbol.SEVEN));
            assertEquals(2, counts.get(Symbol.BAR));
            assertEquals(3, counts.get(Symbol.BELL));
            assertEquals(3, counts.get(Symbol.MELON));
            assertEquals(reel == 2 ? 5 : 4, counts.get(Symbol.LEMON));
            assertEquals(reel == 2 ? 2 : 3, counts.get(Symbol.CHERRY));
            // 同じ絵柄が隣り合わない（16 コマ目と 1 コマ目も）
            for (int stop = 0; stop < Reels.STOPS; stop++) {
                assertTrue(Reels.at(reel, stop) != Reels.at(reel, stop + 1), "reel " + reel + " stop " + stop);
            }
        }
    }

    @Test
    void defaultReturnRateIsAbout96Percent() {
        double rate = Paytable.DEFAULT.returnRate();
        assertEquals(0.9602, rate, 0.0001);
        assertEquals(0.2224, Paytable.DEFAULT.hitRate(), 0.0001);
    }

    @Test
    void evaluateLines() {
        Paytable p = Paytable.DEFAULT;
        assertEquals(300, p.evaluate(new Symbol[]{Symbol.SEVEN, Symbol.SEVEN, Symbol.SEVEN}).times());
        assertTrue(p.evaluate(new Symbol[]{Symbol.SEVEN, Symbol.SEVEN, Symbol.SEVEN}).big());
        assertEquals(50, p.evaluate(new Symbol[]{Symbol.BAR, Symbol.BAR, Symbol.BAR}).times());
        assertEquals(10, p.evaluate(new Symbol[]{Symbol.LEMON, Symbol.LEMON, Symbol.LEMON}).times());
        assertEquals(1, p.evaluate(new Symbol[]{Symbol.CHERRY, Symbol.BELL, Symbol.CHERRY}).times());
        assertEquals(4, p.evaluate(new Symbol[]{Symbol.CHERRY, Symbol.CHERRY, Symbol.BELL}).times());
        assertEquals(20, p.evaluate(new Symbol[]{Symbol.CHERRY, Symbol.CHERRY, Symbol.CHERRY}).times());
        // チェリーは左から数える
        assertEquals(0, p.evaluate(new Symbol[]{Symbol.BELL, Symbol.CHERRY, Symbol.CHERRY}).times());
        assertEquals(0, p.evaluate(new Symbol[]{Symbol.SEVEN, Symbol.SEVEN, Symbol.BAR}).times());
    }

    @Test
    void spinPaysBetTimesMultiplier() {
        Spin spin = Spin.of(new int[]{0, 0, 0}, 10, Paytable.DEFAULT);
        assertEquals(Symbol.SEVEN, spin.line()[2]);
        assertEquals(3000, spin.payout());
        Spin lose = Spin.of(new int[]{0, 1, 2}, 50, Paytable.DEFAULT);
        assertEquals(0, lose.payout());
        assertThrows(IllegalArgumentException.class, () -> Spin.of(new int[]{0, 0}, 10, Paytable.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> Spin.of(new int[]{0, 0, 0}, 0, Paytable.DEFAULT));
    }

    @Test
    void simulatedReturnMatchesTheExactRate() {
        Random random = new Random(7);
        long in = 0;
        long out = 0;
        for (int i = 0; i < 400_000; i++) {
            Spin spin = Spin.random(random, 10, Paytable.DEFAULT);
            in += 10;
            out += spin.payout();
        }
        assertEquals(Paytable.DEFAULT.returnRate(), (double) out / in, 0.02);
    }
}
