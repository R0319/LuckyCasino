package io.github.r0319.luckycasino.baccarat;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Rank;
import io.github.r0319.luckycasino.card.Shoe;
import io.github.r0319.luckycasino.card.Suit;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaccaratTest {

    private static Card c(Rank rank) {
        return new Card(Suit.SPADE, rank);
    }

    /** 配る順（P, B, P, B, 3 枚目…）にカードを積んだシュー。 */
    private static Baccarat deal(Rank... ranks) {
        return Baccarat.deal(Shoe.stacked(Arrays.stream(ranks).map(BaccaratTest::c).toList()));
    }

    @Test
    void cardValues() {
        assertEquals(1, Baccarat.value(c(Rank.ACE)));
        assertEquals(9, Baccarat.value(c(Rank.NINE)));
        assertEquals(0, Baccarat.value(c(Rank.TEN)));
        assertEquals(0, Baccarat.value(c(Rank.KING)));
        assertEquals(5, Baccarat.total(List.of(c(Rank.SEVEN), c(Rank.EIGHT))));
    }

    @Test
    void naturalStandsForBoth() {
        Baccarat b = deal(Rank.FOUR, Rank.TWO, Rank.FIVE, Rank.THREE);
        assertEquals(2, b.player().size());
        assertEquals(2, b.banker().size());
        assertTrue(b.natural());
        assertEquals(Baccarat.Winner.PLAYER, b.winner());
    }

    @Test
    void playerStandsOnSixBankerDrawsOnFive() {
        Baccarat b = deal(Rank.SIX, Rank.TWO, Rank.KING, Rank.THREE, Rank.ACE);
        assertEquals(2, b.player().size());
        assertEquals(3, b.banker().size());
        assertEquals(6, b.playerTotal());
        assertEquals(6, b.bankerTotal());
        assertEquals(Baccarat.Winner.TIE, b.winner());
    }

    @Test
    void thirdCardRules() {
        // P: 2+3=5 → 引く（8）。B: 4 は P の 3 枚目が 8 なら引かない
        Baccarat b = deal(Rank.TWO, Rank.ACE, Rank.THREE, Rank.THREE, Rank.EIGHT, Rank.NINE);
        assertEquals(3, b.player().size());
        assertEquals(2, b.banker().size());
        assertEquals(3, b.playerTotal());
        assertEquals(Baccarat.Winner.BANKER, b.winner());
        assertEquals(List.of(Baccarat.Side.PLAYER, Baccarat.Side.BANKER, Baccarat.Side.PLAYER, Baccarat.Side.BANKER,
                Baccarat.Side.PLAYER), b.deals().stream().map(Baccarat.Deal::side).toList());
    }

    @Test
    void bankerDrawingTable() {
        for (int third = 0; third <= 9; third++) {
            assertTrue(Baccarat.bankerDraws(2, third));
            assertEquals(third != 8, Baccarat.bankerDraws(3, third));
            assertEquals(third >= 2 && third <= 7, Baccarat.bankerDraws(4, third));
            assertEquals(third >= 4 && third <= 7, Baccarat.bankerDraws(5, third));
            assertEquals(third == 6 || third == 7, Baccarat.bankerDraws(6, third));
            assertFalse(Baccarat.bankerDraws(7, third));
        }
        assertTrue(Baccarat.bankerDraws(5, -1));
        assertFalse(Baccarat.bankerDraws(6, -1));
    }

    @Test
    void payouts() {
        Baccarat banker = deal(Rank.TWO, Rank.ACE, Rank.THREE, Rank.THREE, Rank.EIGHT, Rank.NINE);
        assertEquals(0, banker.payout(Baccarat.Bet.PLAYER, 100));
        assertEquals(195, banker.payout(Baccarat.Bet.BANKER, 100));
        assertEquals(19, banker.payout(Baccarat.Bet.BANKER, 10));
        assertEquals(0, banker.payout(Baccarat.Bet.TIE, 100));

        Baccarat tie = deal(Rank.SIX, Rank.TWO, Rank.KING, Rank.THREE, Rank.ACE);
        assertEquals(100, tie.payout(Baccarat.Bet.PLAYER, 100));
        assertEquals(100, tie.payout(Baccarat.Bet.BANKER, 100));
        assertEquals(900, tie.payout(Baccarat.Bet.TIE, 100));

        Baccarat pair = deal(Rank.FOUR, Rank.QUEEN, Rank.FOUR, Rank.QUEEN);
        assertTrue(pair.playerPair());
        assertTrue(pair.bankerPair());
        assertEquals(1200, pair.payout(Baccarat.Bet.PLAYER_PAIR, 100));
        assertEquals(1200, pair.payout(Baccarat.Bet.BANKER_PAIR, 100));
        assertEquals(200, pair.payout(Baccarat.Bet.PLAYER, 100));
    }

    @Test
    void houseKeepsAnEdgeOnMainBets() {
        Shoe shoe = new Shoe(8, 0.8, new Random(42));
        long player = 0;
        long banker = 0;
        int rounds = 200_000;
        for (int i = 0; i < rounds; i++) {
            shoe.shuffleIfNeeded();
            Baccarat b = Baccarat.deal(shoe);
            player += b.payout(Baccarat.Bet.PLAYER, 100);
            banker += b.payout(Baccarat.Bet.BANKER, 100);
        }
        double p = player / (rounds * 100.0);
        double k = banker / (rounds * 100.0);
        assertTrue(p > 0.975 && p < 0.995, "player " + p);
        assertTrue(k > 0.977 && k < 0.997, "banker " + k);
    }
}
