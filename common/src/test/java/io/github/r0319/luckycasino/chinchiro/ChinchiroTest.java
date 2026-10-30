package io.github.r0319.luckycasino.chinchiro;

import io.github.r0319.luckycasino.chinchiro.Chinchiro.Hand;
import io.github.r0319.luckycasino.chinchiro.Chinchiro.Kind;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChinchiroTest {

    @Test
    void hands() {
        assertEquals(new Hand(Kind.PINZORO, 0), Chinchiro.hand(1, 1, 1));
        assertEquals(new Hand(Kind.ZORO, 4), Chinchiro.hand(4, 4, 4));
        assertEquals(new Hand(Kind.SHIGORO, 0), Chinchiro.hand(6, 4, 5));
        assertEquals(new Hand(Kind.HIFUMI, 0), Chinchiro.hand(3, 1, 2));
        assertEquals(new Hand(Kind.ME, 5), Chinchiro.hand(2, 5, 2));
        assertEquals(new Hand(Kind.ME, 1), Chinchiro.hand(6, 1, 6));
        assertEquals(new Hand(Kind.MENASHI, 0), Chinchiro.hand(1, 3, 6));
        assertThrows(IllegalArgumentException.class, () -> Chinchiro.hand(0, 1, 2));
    }

    @Test
    void ordering() {
        assertTrue(Chinchiro.hand(1, 1, 1).compareTo(Chinchiro.hand(6, 6, 6)) > 0);
        assertTrue(Chinchiro.hand(6, 6, 6).compareTo(Chinchiro.hand(2, 2, 2)) > 0);
        assertTrue(Chinchiro.hand(2, 2, 2).compareTo(Chinchiro.hand(4, 5, 6)) > 0);
        assertTrue(Chinchiro.hand(4, 5, 6).compareTo(Chinchiro.hand(3, 3, 6)) > 0);
        assertTrue(Chinchiro.hand(3, 3, 6).compareTo(Chinchiro.hand(3, 3, 5)) > 0);
        assertTrue(Chinchiro.hand(3, 3, 1).compareTo(Chinchiro.hand(1, 3, 6)) > 0);
        assertTrue(Chinchiro.hand(1, 3, 6).compareTo(Chinchiro.hand(1, 2, 3)) > 0);
    }

    @Test
    void dealerDecides() {
        assertEquals(-5, Chinchiro.settledByDealer(Chinchiro.hand(1, 1, 1)));
        assertEquals(-3, Chinchiro.settledByDealer(Chinchiro.hand(5, 5, 5)));
        assertEquals(-2, Chinchiro.settledByDealer(Chinchiro.hand(4, 5, 6)));
        assertEquals(-1, Chinchiro.settledByDealer(Chinchiro.hand(2, 2, 6)));
        assertEquals(1, Chinchiro.settledByDealer(Chinchiro.hand(2, 2, 1)));
        assertEquals(1, Chinchiro.settledByDealer(Chinchiro.hand(1, 3, 6)));
        assertEquals(2, Chinchiro.settledByDealer(Chinchiro.hand(1, 2, 3)));
        assertNull(Chinchiro.settledByDealer(Chinchiro.hand(3, 3, 4)));
    }

    @Test
    void playerAgainstDealer() {
        Hand four = Chinchiro.hand(3, 3, 4);
        assertEquals(1, Chinchiro.versus(four, Chinchiro.hand(2, 2, 5)));
        assertEquals(0, Chinchiro.versus(four, Chinchiro.hand(6, 4, 6)));
        assertEquals(-1, Chinchiro.versus(four, Chinchiro.hand(1, 1, 3)));
        assertEquals(-1, Chinchiro.versus(four, Chinchiro.hand(1, 4, 6)));
        assertEquals(-2, Chinchiro.versus(four, Chinchiro.hand(1, 2, 3)));
        assertEquals(2, Chinchiro.versus(four, Chinchiro.hand(4, 5, 6)));
        assertEquals(3, Chinchiro.versus(four, Chinchiro.hand(2, 2, 2)));
        assertEquals(5, Chinchiro.versus(four, Chinchiro.hand(1, 1, 1)));
        assertEquals(60, Chinchiro.back(10, 5));
        assertEquals(-40, Chinchiro.back(10, -5));
        assertEquals(10, Chinchiro.back(10, 0));
    }

    @Test
    void turnStopsAtFirstHand() {
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            List<Chinchiro.Roll> rolls = Chinchiro.turn(random);
            assertTrue(rolls.size() >= 1 && rolls.size() <= Chinchiro.TRIES);
            for (int k = 0; k < rolls.size() - 1; k++) {
                assertEquals(Kind.MENASHI, rolls.get(k).hand().kind());
            }
        }
    }

    /** 1 回の番の役の確率（3 回まで振る）を数え上げ、子の期待値が負（胴元の取り分がある）ことと、最大の負けを確かめる。 */
    @Test
    void houseEdge() {
        Map<Hand, Double> once = new HashMap<>();
        for (int a = 1; a <= 6; a++) {
            for (int b = 1; b <= 6; b++) {
                for (int c = 1; c <= 6; c++) {
                    once.merge(Chinchiro.hand(a, b, c), 1 / 216.0, Double::sum);
                }
            }
        }
        double none = once.get(new Hand(Kind.MENASHI, 0));
        Map<Hand, Double> turn = new HashMap<>();
        once.forEach((h, p) -> turn.put(h, h.kind() == Kind.MENASHI ? Math.pow(none, 3) : p * (1 + none + none * none)));
        double ev = 0;
        int worst = 0;
        for (var dealer : turn.entrySet()) {
            Integer settled = Chinchiro.settledByDealer(dealer.getKey());
            if (settled != null) {
                ev += dealer.getValue() * settled;
                worst = Math.min(worst, settled);
                continue;
            }
            for (var player : turn.entrySet()) {
                int m = Chinchiro.versus(dealer.getKey(), player.getKey());
                ev += dealer.getValue() * player.getValue() * m;
                worst = Math.min(worst, m);
            }
        }
        assertEquals(-0.01903, ev, 1e-5);
        assertEquals(-(Chinchiro.RESERVE + 1), worst);
    }
}
