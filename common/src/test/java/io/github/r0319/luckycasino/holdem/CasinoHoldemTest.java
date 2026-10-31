package io.github.r0319.luckycasino.holdem;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Rank;
import io.github.r0319.luckycasino.card.Suit;
import io.github.r0319.luckycasino.holdem.CasinoHoldem.Outcome;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoHoldemTest {

    /** "As" "Td" "9h" のような表記（T = 10、s/h/d/c）。 */
    static Card c(String s) {
        Rank rank = switch (s.charAt(0)) {
            case 'A' -> Rank.ACE;
            case 'K' -> Rank.KING;
            case 'Q' -> Rank.QUEEN;
            case 'J' -> Rank.JACK;
            case 'T' -> Rank.TEN;
            default -> Rank.values()[s.charAt(0) - '1'];
        };
        Suit suit = switch (s.charAt(1)) {
            case 's' -> Suit.SPADE;
            case 'h' -> Suit.HEART;
            case 'd' -> Suit.DIAMOND;
            default -> Suit.CLUB;
        };
        return new Card(suit, rank);
    }

    static List<Card> cards(String text) {
        return Arrays.stream(text.split(" ")).map(CasinoHoldemTest::c).toList();
    }

    private static CasinoHoldem game(String hole, String board, String dealer) {
        return CasinoHoldem.of(List.of(cards(hole)), cards(board), cards(dealer));
    }

    @Test
    void dealerNeedsPairOfFours() {
        assertFalse(game("2c 3c", "Ks Qd 8h 7c 3d", "As 9d").dealerQualifies());
        assertFalse(game("2c 3c", "Ks Qd 8h 7c 3d", "3s 9d").dealerQualifies());
        assertTrue(game("2c 3c", "Ks Qd 8h 7c 4d", "4s 9d").dealerQualifies());
        assertTrue(game("2c 3c", "Ks Qd 8h 7c 4d", "Kd 2d").dealerQualifies());
    }

    @Test
    void notQualifiedPaysAnteAndReturnsCall() {
        CasinoHoldem g = game("2c 5h", "Ks Qd 8h 7c 3d", "As 9d");
        assertEquals(Outcome.NOT_QUALIFIED, g.outcome(0, true));
        assertEquals(200, g.antePayout(0, true, 100));
        assertEquals(200, g.callPayout(0, 200));
    }

    @Test
    void winPaysAnteTableAndCallEvenMoney() {
        // 手札でフラッシュ（アンティ 2 倍）、ディーラーはキングのワンペア
        CasinoHoldem g = game("2s 5s", "Ks Qs 8h 7s 4d", "Kd 9c");
        assertEquals(Outcome.WIN, g.outcome(0, true));
        assertEquals(300, g.antePayout(0, true, 100));
        assertEquals(400, g.callPayout(0, 200));
    }

    @Test
    void loseAndTieAndFold() {
        CasinoHoldem lose = game("2c 3h", "Ks Qd 8h 7c 4d", "Kd 9c");
        assertEquals(Outcome.LOSE, lose.outcome(0, true));
        assertEquals(0, lose.antePayout(0, true, 100));
        assertEquals(0, lose.callPayout(0, 200));
        CasinoHoldem tie = game("2c 3h", "As Ad Kh Kc Qd", "4d 5c");
        assertEquals(Outcome.TIE, tie.outcome(0, true));
        assertEquals(100, tie.antePayout(0, true, 100));
        assertEquals(200, tie.callPayout(0, 200));
        assertEquals(Outcome.FOLD, tie.outcome(0, false));
        assertEquals(0, tie.antePayout(0, false, 100));
    }

    @Test
    void royalPaysHundred() {
        CasinoHoldem g = game("As Ks", "Qs Js Ts 2d 3c", "4d 4c");
        assertEquals(101 * 100, g.antePayout(0, true, 100));
    }

    @Test
    void suggestCall() {
        assertTrue(CasinoHoldem.suggestCall(cards("8c 8d"), cards("2s 5h Jd")));
        assertTrue(CasinoHoldem.suggestCall(cards("As 3d"), cards("7s 9h Jd")));
        assertTrue(CasinoHoldem.suggestCall(cards("6s 7d"), cards("8s 9h 2d")));
        assertTrue(CasinoHoldem.suggestCall(cards("2s 7s"), cards("9s Js 4d")));
        // 場だけのペアで、手札は弱い
        assertFalse(CasinoHoldem.suggestCall(cards("2c 7d"), cards("Js Jh 4s")));
        assertFalse(CasinoHoldem.suggestCall(cards("2c 7d"), cards("9s Th 4s")));
    }
}
