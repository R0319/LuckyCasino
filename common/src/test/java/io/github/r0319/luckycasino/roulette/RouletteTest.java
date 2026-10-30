package io.github.r0319.luckycasino.roulette;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouletteTest {

    @Test
    void wheelHasEveryNumberOnce() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < Wheel.POCKETS; i++) {
            seen.add(Wheel.numberAt(i));
            assertEquals(i, Wheel.indexOf(Wheel.numberAt(i)));
        }
        assertEquals(37, seen.size());
        long red = seen.stream().filter(Wheel::red).count();
        long black = seen.stream().filter(Wheel::black).count();
        assertEquals(18, red);
        assertEquals(18, black);
        assertFalse(Wheel.red(0) || Wheel.black(0));
    }

    @Test
    void redAndBlackAlternateOnTheWheel() {
        for (int i = 1; i < Wheel.POCKETS - 1; i++) {
            assertTrue(Wheel.red(Wheel.numberAt(i)) != Wheel.red(Wheel.numberAt(i + 1)));
        }
    }

    @Test
    void betNumbersAndPayouts() {
        assertEquals(Set.of(17), RouletteBet.straight(17, 10).numbers());
        assertEquals(360, RouletteBet.straight(17, 10).payout(17));
        assertEquals(0, RouletteBet.straight(17, 10).payout(18));
        assertEquals(Set.of(14, 17), RouletteBet.split(14, 17, 10).numbers());
        assertEquals(Set.of(17, 18), RouletteBet.split(18, 17, 10).numbers());
        assertEquals(180, RouletteBet.split(14, 17, 10).payout(17));
        assertEquals(Set.of(0, 2), RouletteBet.split(0, 2, 10).numbers());
        assertEquals(Set.of(16, 17, 18), RouletteBet.street(6, 10).numbers());
        assertEquals(120, RouletteBet.street(6, 10).payout(18));
        assertEquals(Set.of(17, 18, 20, 21), RouletteBet.corner(17, 10).numbers());
        assertEquals(90, RouletteBet.corner(17, 10).payout(21));
        assertEquals(Set.of(0, 1, 2, 3), RouletteBet.corner(0, 10).numbers());
        assertEquals(Set.of(31, 32, 33, 34, 35, 36), RouletteBet.line(11, 10).numbers());
        assertEquals(60, RouletteBet.line(11, 10).payout(36));
        assertEquals(12, RouletteBet.dozen(2, 10).numbers().size());
        assertEquals(30, RouletteBet.dozen(2, 10).payout(24));
        assertEquals(30, RouletteBet.column(3, 10).payout(36));
        assertEquals(0, RouletteBet.column(3, 10).payout(35));
        assertEquals(20, RouletteBet.outside(BetType.RED, 10).payout(32));
        assertEquals(0, RouletteBet.outside(BetType.RED, 10).payout(0));
        assertEquals(0, RouletteBet.outside(BetType.EVEN, 10).payout(0));
        assertEquals(20, RouletteBet.outside(BetType.HIGH, 10).payout(19));
    }

    @Test
    void outsideBetsLoseOnZero() {
        for (BetType type : List.of(BetType.RED, BetType.BLACK, BetType.EVEN, BetType.ODD, BetType.LOW, BetType.HIGH)) {
            assertEquals(18, RouletteBet.outside(type, 10).numbers().size());
            assertFalse(RouletteBet.outside(type, 10).wins(0));
        }
        assertFalse(RouletteBet.dozen(1, 10).wins(0));
        assertFalse(RouletteBet.column(1, 10).wins(0));
    }

    @Test
    void invalidPlacesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RouletteBet.split(3, 4, 10));
        assertThrows(IllegalArgumentException.class, () -> RouletteBet.split(1, 5, 10));
        assertThrows(IllegalArgumentException.class, () -> RouletteBet.split(0, 4, 10));
        assertThrows(IllegalArgumentException.class, () -> RouletteBet.corner(3, 10));
        assertThrows(IllegalArgumentException.class, () -> RouletteBet.corner(34, 10));
        assertThrows(IllegalArgumentException.class, () -> RouletteBet.line(12, 10));
        assertThrows(IllegalArgumentException.class, () -> RouletteBet.straight(37, 10));
    }

    @Test
    void expectedValueIsTheSameForEveryBet() {
        // ヨーロピアンでは、どの賭け方でも 37 回の結果の払い戻し合計は 36 × 賭け金
        List<RouletteBet> bets = List.of(RouletteBet.straight(5, 1), RouletteBet.split(5, 8, 1), RouletteBet.street(2, 1),
                RouletteBet.corner(5, 1), RouletteBet.line(2, 1), RouletteBet.dozen(1, 1), RouletteBet.column(2, 1),
                RouletteBet.outside(BetType.RED, 1), RouletteBet.outside(BetType.ODD, 1), RouletteBet.outside(BetType.LOW, 1));
        for (RouletteBet bet : bets) {
            long sum = 0;
            for (int n = 0; n <= 36; n++) {
                sum += bet.payout(n);
            }
            assertEquals(36, sum, bet.type().name());
        }
    }

    @Test
    void roundMergesSamePlaceAndEnforcesLimits() {
        RouletteRound round = new RouletteRound(10, 100, 150);
        round.place(0, RouletteBet.straight(7, 50));
        RouletteBet merged = round.place(0, RouletteBet.straight(7, 30));
        assertEquals(80, merged.amount());
        assertEquals(1, round.bets(0).size());
        assertThrows(IllegalArgumentException.class, () -> round.place(0, RouletteBet.straight(7, 30)));
        assertThrows(IllegalArgumentException.class, () -> round.place(0, RouletteBet.straight(8, 5)));
        round.place(0, RouletteBet.outside(BetType.RED, 70));
        assertThrows(IllegalArgumentException.class, () -> round.place(0, RouletteBet.straight(9, 10)));
        assertEquals(150, round.total(0));
        round.spinTo(7);
        List<RouletteRound.Payout> payouts = round.payouts();
        assertEquals(2, payouts.size());
        assertEquals(80 * 36, payouts.get(0).payout());
        assertEquals(140, payouts.get(1).payout());
        assertThrows(IllegalStateException.class, () -> round.place(1, RouletteBet.straight(1, 10)));
    }

    @Test
    void spinCoversAllNumbers() {
        Set<Integer> seen = new HashSet<>();
        Random random = new Random(3);
        for (int i = 0; i < 2000; i++) {
            RouletteRound round = new RouletteRound(1, 10, 10);
            seen.add(round.spin(random));
        }
        assertEquals(37, seen.size());
    }
}
