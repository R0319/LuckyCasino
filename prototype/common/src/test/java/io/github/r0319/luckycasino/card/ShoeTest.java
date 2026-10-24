package io.github.r0319.luckycasino.card;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoeTest {

    @Test
    void containsEachCardOncePerDeck() {
        Shoe shoe = new Shoe(6, 0.75, new Random(1));
        Map<Card, Integer> counts = new HashMap<>();
        while (shoe.remaining() > 0) {
            counts.merge(shoe.draw(), 1, Integer::sum);
        }
        assertEquals(52, counts.size());
        counts.values().forEach(n -> assertEquals(6, n));
    }

    @Test
    void reshufflesAfterPenetration() {
        Shoe shoe = new Shoe(1, 0.75, new Random(1));
        for (int i = 0; i < 39; i++) {
            shoe.draw();
        }
        assertFalse(shoe.shuffleIfNeeded());
        shoe.draw();
        assertTrue(shoe.shuffleIfNeeded());
        assertEquals(52, shoe.remaining());
    }

    @Test
    void cardCodeRoundTrip() {
        for (int code = 1; code <= 52; code++) {
            assertEquals(code, Card.fromCode(code).code());
        }
        assertEquals("spade_a", new Card(Suit.SPADE, Rank.ACE).modelName());
        assertEquals("club_k", new Card(Suit.CLUB, Rank.KING).modelName());
    }
}
