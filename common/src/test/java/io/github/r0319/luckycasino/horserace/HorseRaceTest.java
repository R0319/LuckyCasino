package io.github.r0319.luckycasino.horserace;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorseRaceTest {

    @Test
    void oddsComeFromChance() {
        HorseRace race = HorseRace.of(0.4, 0.2, 0.1, 0.1, 0.1, 0.1);
        assertEquals(225, race.percent(0));
        assertEquals(450, race.percent(1));
        assertEquals(900, race.percent(2));
        assertEquals(225, race.payout(0, 100, 0));
        assertEquals(0, race.payout(1, 100, 0));
        assertEquals("2.25 倍", HorseRace.percentText(225));
    }

    @Test
    void orderIsAPermutation() {
        Random random = new Random(1);
        for (int n = 0; n < 200; n++) {
            int[] order = HorseRace.card(random).run(random);
            int[] sorted = order.clone();
            Arrays.sort(sorted);
            assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, sorted);
        }
    }

    /** 勝つ頻度が確率どおりで、還元率は 90% 前後（それを超えない）。 */
    @Test
    void winnersFollowChanceAndHouseKeepsTenPercent() {
        Random random = new Random(7);
        HorseRace race = HorseRace.of(0.4, 0.2, 0.1, 0.1, 0.1, 0.1);
        int[] wins = new int[6];
        int rounds = 200_000;
        for (int n = 0; n < rounds; n++) {
            wins[race.run(random)[0]]++;
        }
        for (int h = 0; h < 6; h++) {
            assertEquals(race.chance(h), wins[h] / (double) rounds, 0.01, "horse " + h);
        }
        for (int n = 0; n < 50; n++) {
            HorseRace card = HorseRace.card(random);
            double sum = 0;
            for (int h = 0; h < 6; h++) {
                sum += card.chance(h);
                double rtp = card.chance(h) * card.percent(h) / 100;
                assertTrue(rtp <= 0.9 && rtp > 0.85, "rtp " + rtp);
                assertTrue(card.percent(h) >= 110);
            }
            assertEquals(1, sum, 1e-9);
        }
    }
}
