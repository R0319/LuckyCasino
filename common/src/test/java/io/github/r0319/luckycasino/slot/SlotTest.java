package io.github.r0319.luckycasino.slot;

import org.junit.jupiter.api.Test;

import static io.github.r0319.luckycasino.slot.Symbol.BELL;
import static io.github.r0319.luckycasino.slot.Symbol.CHERRY;
import static io.github.r0319.luckycasino.slot.Symbol.LEMON;
import static io.github.r0319.luckycasino.slot.Symbol.SEVEN;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(0.654, Paytable.DEFAULT.hitRate(), 0.001);
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
    void linesReadTheRowsAboveAndBelow() {
        int[] stops = {0, 0, 0};
        // 中央の段は止まったコマ、上の段は次のコマ、下の段は前のコマ
        assertArrayEquals(new Symbol[]{SEVEN, SEVEN, SEVEN}, Line.MIDDLE.symbols(stops));
        assertArrayEquals(new Symbol[]{CHERRY, LEMON, BELL}, Line.TOP.symbols(stops));
        assertArrayEquals(new Symbol[]{LEMON, BELL, LEMON}, Line.BOTTOM.symbols(stops));
        assertArrayEquals(new Symbol[]{CHERRY, SEVEN, LEMON}, Line.DOWN.symbols(stops));
        assertArrayEquals(new Symbol[]{LEMON, SEVEN, BELL}, Line.UP.symbols(stops));
    }

    @Test
    void spinPaysEachLineOneFifthOfTheBet() {
        // 中央 7 7 7（300 倍）、上の段の左にチェリー（1 倍）、右下がりの斜めの左にもチェリー（1 倍）
        Spin spin = Spin.of(new int[]{0, 0, 0}, 10, Paytable.DEFAULT);
        assertEquals(List.of(Line.TOP, Line.MIDDLE, Line.DOWN), spin.wins().stream().map(Spin.LineWin::line).toList());
        assertEquals(2 * 300 + 2 + 2, spin.payout());
        assertTrue(spin.big());
        assertEquals(Line.MIDDLE, spin.best().line());
        // 上の段が 7 7 7（コマ 15・15・15 の次）
        Spin top = Spin.of(new int[]{15, 15, 15}, 50, Paytable.DEFAULT);
        assertEquals(Line.TOP, top.best().line());
        assertEquals(10 * 300, top.best().win().times() * 10);
        // 斜め: 右上がり（左が下の段・右が上の段）で 7 7 7
        Spin up = Spin.of(new int[]{1, 0, 15}, 100, Paytable.DEFAULT);
        assertEquals(Line.UP, up.best().line());
        // 中央の段の左はチェリー（1 倍）
        assertEquals(20 * 300 + 20, up.payout());
        // 外れ（どのラインも左がチェリーでなく、3 つそろわない）
        Spin lose = Spin.of(new int[]{3, 0, 0}, 50, Paytable.DEFAULT);
        assertEquals(List.of(), lose.wins());
        assertEquals(0, lose.payout());
        assertNull(lose.best());
        assertThrows(IllegalArgumentException.class, () -> Spin.of(new int[]{0, 0}, 10, Paytable.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> Spin.of(new int[]{0, 0, 0}, 0, Paytable.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> Spin.of(new int[]{0, 0, 0}, 12, Paytable.DEFAULT));
    }

    @Test
    void reachIsSevenOrBarOnTheLeftTwoOfAnyLine() {
        assertEquals(List.of(Line.MIDDLE), Spin.reachLines(new int[]{0, 0, 5}));
        assertEquals(List.of(Line.TOP), Spin.reachLines(new int[]{15, 15, 3}));
        assertEquals(List.of(Line.UP), Spin.reachLines(new int[]{1, 0, 3}));
        assertEquals(List.of(), Spin.reachLines(new int[]{2, 1, 2}));
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
