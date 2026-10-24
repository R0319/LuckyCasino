package io.github.r0319.luckycasino.poker;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Shoe;
import io.github.r0319.luckycasino.poker.PokerEvent.Refund;
import io.github.r0319.luckycasino.poker.PokerEvent.Turn;
import io.github.r0319.luckycasino.poker.PokerEvent.Won;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokerRoundTest {

    private static Map<Integer, Long> stacks(long... values) {
        Map<Integer, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            map.put(i, values[i]);
        }
        return map;
    }

    /**
     * 配る順（ボタンの左から 1 枚ずつ 2 周）と、そのあとの共有カード 5 枚を指定した山。
     * 足りない分は使わないカードで埋める。
     */
    private static Shoe deck(String text) {
        List<Card> cards = new ArrayList<>(HandEvaluatorTest.cards(text));
        return Shoe.stacked(cards);
    }

    private static List<Won> wins(List<PokerEvent> events) {
        return events.stream().filter(Won.class::isInstance).map(Won.class::cast).toList();
    }

    private static long total(PokerRound round) {
        return round.stacks().values().stream().mapToLong(Long::longValue).sum() + round.pot();
    }

    @Test
    void blindsAndFirstToActWithThreePlayers() {
        // ボタン 0、SB 1、BB 2、最初に行動するのは 0
        PokerRound round = new PokerRound(stacks(1000, 1000, 1000), 6, 0, 5, 10, deck("2s 3s 4s 5s 6s 7s"));
        List<PokerEvent> events = round.start();
        Turn turn = assertInstanceOf(Turn.class, events.getLast());
        assertEquals(0, turn.seat());
        assertEquals(10, turn.legal().toCall());
        assertEquals(20, turn.legal().minRaiseTo());
        assertEquals(1000, turn.legal().maxRaiseTo());
        assertEquals(15, round.pot());
    }

    @Test
    void headsUpButtonIsSmallBlindAndActsFirstPreflopLastPostflop() {
        PokerRound round = new PokerRound(stacks(1000, 1000), 6, 0, 5, 10, deck("2s 3s 4s 5s 6h 7h 8h 9d Jc"));
        round.start();
        assertEquals(0, round.currentSeat());
        assertEquals(5, round.legal().toCall());
        round.call(0);
        // BB にオプション
        assertEquals(1, round.currentSeat());
        assertTrue(round.legal().canCheck());
        round.check(1);
        assertEquals(Street.FLOP, round.street());
        // フロップ以降は BB（ボタンでない方）から
        assertEquals(1, round.currentSeat());
    }

    @Test
    void everyoneFoldsToRaiserWhoGetsBlindsBack() {
        PokerRound round = new PokerRound(stacks(1000, 1000, 1000), 6, 0, 5, 10, deck("2s 3s 4s 5s 6s 7s"));
        round.start();
        round.raiseTo(0, 30);
        round.fold(1);
        List<PokerEvent> events = round.fold(2);
        assertTrue(round.finished());
        // 合わせられなかった 20 を返し、ポット 45（30 + 5 + 10 から 20 返金 → 25）を獲得
        Refund refund = events.stream().filter(Refund.class::isInstance).map(Refund.class::cast).findFirst().orElseThrow();
        assertEquals(20, refund.amount());
        Won won = wins(events).getFirst();
        assertEquals(0, won.seat());
        assertEquals(25, won.amount());
        assertNull(won.value());
        assertEquals(Map.of(0, 1015L, 1, 995L, 2, 990L), round.stacks());
    }

    @Test
    void minRaiseFollowsLastRaiseSize() {
        PokerRound round = new PokerRound(stacks(1000, 1000, 1000), 6, 0, 5, 10, deck("2s 3s 4s 5s 6s 7s"));
        round.start();
        round.raiseTo(0, 40);
        // 30 上げたので次の最低は 70
        assertEquals(70, round.legal().minRaiseTo());
        assertThrows(IllegalArgumentException.class, () -> round.raiseTo(1, 60));
        round.raiseTo(1, 70);
        assertEquals(100, round.legal().minRaiseTo());
    }

    @Test
    void shortAllInDoesNotReopenBetting() {
        // 0: 1000、1: 1000、2: 45（BB）
        PokerRound round = new PokerRound(stacks(1000, 1000, 45), 6, 0, 5, 10, deck("2s 3s 4s 5s 6s 7s 8d 9d Td Jc Qc"));
        round.start();
        round.raiseTo(0, 30);
        round.call(1);
        // BB が 45 でオールイン（15 しか上げていないので完全なレイズではない）
        round.allIn(2);
        // 0 は既に行動しているのでコールかフォールドだけ
        assertEquals(0, round.currentSeat());
        assertFalse(round.legal().canRaise());
        assertEquals(15, round.legal().toCall());
        round.call(0);
        assertEquals(1, round.currentSeat());
        assertFalse(round.legal().canRaise());
        round.call(1);
        assertEquals(Street.FLOP, round.street());
    }

    @Test
    void sidePotsGoToTheRightPlayers() {
        // 配る順: 1, 2, 0, 1, 2, 0（ボタン 0 の左から）、そのあと共有カード 5 枚
        // 1: AA（短い 100）、2: KK（300）、0: QQ（1000）
        PokerRound round = new PokerRound(stacks(1000, 100, 300), 6, 0, 5, 10,
                deck("As Ks Qs Ah Kh Qh 2c 7d 9s 3h 4c"));
        round.start();
        round.allIn(0);
        round.allIn(1);
        List<PokerEvent> events = round.allIn(2);
        assertTrue(round.finished());
        // メインポット 300（100 × 3）は AA、サイドポット 400（200 × 2）は KK、0 には 700 返金
        List<Won> wins = wins(events);
        assertEquals(2, wins.size());
        assertEquals(1, wins.get(0).seat());
        assertEquals(300, wins.get(0).amount());
        assertEquals(0, wins.get(0).pot());
        assertEquals(2, wins.get(1).seat());
        assertEquals(400, wins.get(1).amount());
        assertEquals(1, wins.get(1).pot());
        assertEquals(Map.of(0, 700L, 1, 300L, 2, 400L), round.stacks());
    }

    @Test
    void splitPotGivesOddChipLeftOfButton() {
        // 共有カードがロイヤルフラッシュで全員引き分け。ポット 25 を 2 人（SB 1 と BB 2）で分け、端数は 1 へ
        PokerRound round = new PokerRound(stacks(1000, 1000, 1000), 6, 0, 5, 10,
                deck("2c 3d 4h 5c 6d 7h As Ks Qs Js Ts"));
        round.start();
        round.fold(0);
        round.call(1);
        round.check(2);
        // フロップ以降はチェックだけ
        while (!round.finished()) {
            round.check(round.currentSeat());
        }
        assertEquals(Map.of(0, 1000L, 1, 1000L, 2, 1000L), round.stacks());
    }

    @Test
    void oddChipWithUnevenPot() {
        // 0 が 5 で降りた SB の分を含むポット 25 を 1 と 2 で分ける: 13 と 12
        PokerRound round = new PokerRound(stacks(1000, 1000, 1000), 6, 2, 5, 10,
                deck("2c 3d 4h 5c 6d 7h As Ks Qs Js Ts"));
        // ボタン 2、SB 0、BB 1、最初は 2
        round.start();
        round.call(2);
        round.fold(0);
        round.check(1);
        List<PokerEvent> last = List.of();
        while (!round.finished()) {
            last = round.check(round.currentSeat());
        }
        List<Won> wins = wins(last);
        assertEquals(2, wins.size());
        // ボタンの左（0 は降りている）から: 1 が先
        assertEquals(1, wins.get(0).seat());
        assertEquals(13, wins.get(0).amount());
        assertEquals(12, wins.get(1).amount());
    }

    @Test
    void shortBlindAllInGetsOnlyWhatItCovers() {
        // BB が 4 しか持っていない
        PokerRound round = new PokerRound(stacks(1000, 4), 6, 0, 5, 10, deck("As Ks Ah Kh 2c 7d 9s 3h 4c"));
        round.start();
        // ボタン（SB）が 5 を出して 5 を払えばよい（BB は 4 でオールイン）。ここでは コールせず チェック扱いはできない
        round.call(0);
        assertTrue(round.finished());
        long sum = round.stacks().values().stream().mapToLong(Long::longValue).sum();
        assertEquals(1004, sum);
    }

    @Test
    void timeoutChecksOrFolds() {
        PokerRound round = new PokerRound(stacks(1000, 1000, 1000), 6, 0, 5, 10, deck("2s 3s 4s 5s 6s 7s 8d 9d Td Jc Qc"));
        round.start();
        round.timeout(0);
        assertFalse(round.inHand(0));
        round.call(1);
        round.timeout(2);
        assertTrue(round.inHand(2));
        assertEquals(Street.FLOP, round.street());
    }

    @Test
    void randomHandsConserveChips() {
        Random random = new Random(42);
        for (int game = 0; game < 2000; game++) {
            int players = 2 + random.nextInt(5);
            Map<Integer, Long> stacks = new LinkedHashMap<>();
            long start = 0;
            for (int i = 0; i < players; i++) {
                long stack = 1 + random.nextInt(600);
                stacks.put(i * 1, stack);
                start += stack;
            }
            Shoe shoe = new Shoe(1, 0.5, random);
            PokerRound round = new PokerRound(stacks, 6, random.nextInt(players), 5, 10, shoe);
            round.start();
            int guard = 0;
            while (!round.finished()) {
                assertTrue(guard++ < 200, "too many actions");
                int seat = round.currentSeat();
                Legal legal = round.legal();
                int pick = random.nextInt(10);
                if (pick == 0) {
                    round.fold(seat);
                } else if (pick < 3 && legal.canRaise()) {
                    long span = legal.maxRaiseTo() - legal.minRaiseTo();
                    round.raiseTo(seat, legal.minRaiseTo() + (span > 0 ? random.nextLong(span + 1) : 0));
                } else if (pick < 4) {
                    round.allIn(seat);
                } else if (legal.canCheck()) {
                    round.check(seat);
                } else {
                    round.call(seat);
                }
                assertEquals(start, total(round));
            }
            long end = round.stacks().values().stream().mapToLong(Long::longValue).sum();
            assertEquals(start, end);
            assertEquals(0, round.pot());
        }
    }
}
