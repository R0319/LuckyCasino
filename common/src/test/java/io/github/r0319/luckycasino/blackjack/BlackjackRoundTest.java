package io.github.r0319.luckycasino.blackjack;

import io.github.r0319.luckycasino.blackjack.BlackjackEvent.DealerCard;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.RoundOver;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.Settled;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.Turn;
import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Rank;
import io.github.r0319.luckycasino.card.Shoe;
import io.github.r0319.luckycasino.card.Suit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackRoundTest {

    /** "A" "10" "K" などのランクだけでカードを作る（スートはスペード固定）。 */
    private static Card c(String rank) {
        Rank r = switch (rank) {
            case "A" -> Rank.ACE;
            case "J" -> Rank.JACK;
            case "Q" -> Rank.QUEEN;
            case "K" -> Rank.KING;
            default -> Rank.values()[Integer.parseInt(rank) - 1];
        };
        return new Card(Suit.SPADE, r);
    }

    private static BlackjackRound round(String... topFirst) {
        return round(Rules.DEFAULT, topFirst);
    }

    private static BlackjackRound round(Rules rules, String... topFirst) {
        List<Card> cards = Arrays.stream(topFirst).map(BlackjackRoundTest::c).toList();
        return new BlackjackRound(rules, Shoe.stacked(cards), 5);
    }

    private static List<Settled> settled(List<BlackjackEvent> events) {
        List<Settled> out = new ArrayList<>();
        for (BlackjackEvent event : events) {
            if (event instanceof Settled s) {
                out.add(s);
            }
        }
        return out;
    }

    @Test
    void dealOrderAndHiddenHoleCard() {
        // 配る順: 席 0、ディーラー（表）、席 0、ディーラー（裏）
        BlackjackRound round = round("10", "9", "7", "8");
        round.bet(0, 100);
        List<BlackjackEvent> events = round.deal();
        DealerCard up = assertInstanceOf(DealerCard.class, events.get(1));
        DealerCard hole = assertInstanceOf(DealerCard.class, events.get(3));
        assertEquals(c("9"), up.card());
        assertTrue(hole.hidden());
        assertNull(hole.card());
        Turn turn = assertInstanceOf(Turn.class, events.getLast());
        assertEquals(0, turn.seat());
        assertEquals(List.of(Action.HIT, Action.STAND, Action.DOUBLE), turn.actions());
    }

    @Test
    void standWinsAgainstLowerDealer() {
        // 席 0: 10+8=18、ディーラー: 9+8=17 で止まる
        BlackjackRound round = round("10", "9", "8", "8");
        round.bet(0, 100);
        round.deal();
        List<BlackjackEvent> events = round.act(0, Action.STAND);
        assertEquals(List.of(new Settled(0, 0, Outcome.WIN, 100, 200)), settled(events));
        assertInstanceOf(RoundOver.class, events.getLast());
        assertEquals(BlackjackRound.Phase.FINISHED, round.phase());
    }

    @Test
    void hitToBustLosesAndDealerDoesNotDraw() {
        // 席 0: 10+6、ヒットで K → 26 バースト。ディーラーは引かない
        BlackjackRound round = round("10", "9", "6", "5", "K");
        round.bet(0, 100);
        round.deal();
        List<BlackjackEvent> events = round.act(0, Action.HIT);
        assertEquals(List.of(new Settled(0, 0, Outcome.BUST, 100, 0)), settled(events));
        assertEquals(14, round.dealerHand().total());
    }

    @Test
    void blackjackPaysThreeToTwo() {
        BlackjackRound round = round("A", "9", "K", "8");
        round.bet(0, 100);
        List<BlackjackEvent> events = round.deal();
        assertEquals(List.of(new Settled(0, 0, Outcome.BLACKJACK, 100, 250)), settled(events));
    }

    @Test
    void blackjackPayoutRoundsDown() {
        assertEquals(25 + 37, Outcome.BLACKJACK.payout(25));
    }

    @Test
    void dealerBlackjackEndsRoundImmediately() {
        // ディーラー: A（表）+ K（裏）。席 0 は 10+9、席 1 はブラックジャックで引き分け
        BlackjackRound round = round("10", "A", "A", "9", "K", "K");
        round.bet(0, 100);
        round.bet(1, 50);
        List<BlackjackEvent> events = round.deal();
        assertEquals(List.of(
                new Settled(0, 0, Outcome.LOSE, 100, 0),
                new Settled(1, 0, Outcome.PUSH, 50, 50)), settled(events));
    }

    @Test
    void dealerStandsOnSoft17ByDefault() {
        BlackjackRound round = round("10", "A", "8", "6");
        round.bet(0, 100);
        round.deal();
        List<BlackjackEvent> events = round.act(0, Action.STAND);
        assertEquals(17, round.dealerHand().total());
        assertEquals(Outcome.WIN, settled(events).getFirst().outcome());
    }

    @Test
    void dealerHitsSoft17WhenConfigured() {
        Rules rules = new Rules(6, 0.75, true, true, 10, 1000);
        BlackjackRound round = round(rules, "10", "A", "8", "6", "3");
        round.bet(0, 100);
        round.deal();
        List<BlackjackEvent> events = round.act(0, Action.STAND);
        assertEquals(20, round.dealerHand().total());
        assertEquals(Outcome.LOSE, settled(events).getFirst().outcome());
    }

    @Test
    void doubleDownDrawsExactlyOneCard() {
        BlackjackRound round = round("6", "9", "5", "8", "2", "10");
        round.bet(0, 100);
        round.deal();
        assertEquals(100, round.extraStake(Action.DOUBLE));
        List<BlackjackEvent> events = round.act(0, Action.DOUBLE);
        // 6+5+2=13、ディーラー 9+8=17
        assertEquals(List.of(new Settled(0, 0, Outcome.LOSE, 200, 0)), settled(events));
        assertEquals(3, round.hands(0).getFirst().cards().size());
    }

    @Test
    void splitPlaysEachHand() {
        // 席 0: 8,8 をスプリット。1 つ目に 3 → 11、ヒットで 10 → 21。2 つ目に 9 → 17 でスタンド
        // ディーラー: 10+7=17
        BlackjackRound round = round("8", "10", "8", "7", "3", "10", "9");
        round.bet(0, 100);
        round.deal();
        assertTrue(round.allowedActions().contains(Action.SPLIT));
        round.act(0, Action.SPLIT);
        assertEquals(0, round.currentHand());
        assertFalse(round.allowedActions().contains(Action.SPLIT));
        List<BlackjackEvent> afterHit = round.act(0, Action.HIT);
        // 21 で自動的に終わり、2 つ目の手に 9 が配られて番が来る
        assertEquals(1, round.currentHand());
        assertInstanceOf(Turn.class, afterHit.getLast());
        List<BlackjackEvent> events = round.act(0, Action.STAND);
        assertEquals(List.of(
                new Settled(0, 0, Outcome.WIN, 100, 200),
                new Settled(0, 1, Outcome.PUSH, 100, 100)), settled(events));
    }

    @Test
    void splitAcesGetOneCardEachAndTwentyOneIsNotBlackjack() {
        // A,A をスプリット → 1 つ目に K（21、ブラックジャックではない）、2 つ目に 5
        // ディーラー: 10+7=17
        BlackjackRound round = round("A", "10", "A", "7", "K", "5");
        round.bet(0, 100);
        round.deal();
        List<BlackjackEvent> events = round.act(0, Action.SPLIT);
        assertEquals(List.of(
                new Settled(0, 0, Outcome.WIN, 100, 200),
                new Settled(0, 1, Outcome.LOSE, 100, 0)), settled(events));
    }

    @Test
    void timeoutStandsAndMovesToNextSeat() {
        BlackjackRound round = round("10", "10", "9", "7", "8", "8");
        round.bet(0, 100);
        round.bet(2, 100);
        round.deal();
        assertEquals(0, round.currentSeat());
        round.timeout();
        assertEquals(2, round.currentSeat());
        assertThrows(IllegalStateException.class, () -> round.act(0, Action.HIT));
    }

    @Test
    void betLimitsAreEnforced() {
        BlackjackRound round = round();
        assertThrows(IllegalArgumentException.class, () -> round.bet(0, 5));
        assertThrows(IllegalArgumentException.class, () -> round.bet(0, 5000));
        assertThrows(IllegalArgumentException.class, () -> round.bet(5, 100));
        assertThrows(IllegalStateException.class, round::deal);
    }
}
