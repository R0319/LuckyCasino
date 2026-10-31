package io.github.r0319.luckycasino;

import io.github.r0319.luckycasino.baccarat.Baccarat;
import io.github.r0319.luckycasino.bigwheel.BigWheel;
import io.github.r0319.luckycasino.blackjack.Action;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent;
import io.github.r0319.luckycasino.blackjack.BlackjackRound;
import io.github.r0319.luckycasino.blackjack.Hand;
import io.github.r0319.luckycasino.blackjack.Rules;
import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Rank;
import io.github.r0319.luckycasino.card.Shoe;
import io.github.r0319.luckycasino.highlow.HighLow;
import io.github.r0319.luckycasino.holdem.CasinoHoldem;
import io.github.r0319.luckycasino.horserace.HorseRace;
import io.github.r0319.luckycasino.roulette.BetType;
import io.github.r0319.luckycasino.roulette.RouletteBet;
import io.github.r0319.luckycasino.slot.Paytable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * どのゲームのどの賭け方でも、還元率（戻る額の期待値 ÷ 賭け金）が 100% を下回ること。
 * 数え上げられるものは数え上げ、ブラックジャックとバカラは乱数を固定して大きく試す。一覧は標準出力に出す。
 */
class ReturnToPlayerTest {

    private final Map<String, Double> report = new LinkedHashMap<>();

    private void record(String name, double rtp) {
        report.put(name, rtp);
        assertTrue(rtp < 1.0, name + " の還元率が " + rtp);
    }

    @Test
    void everyBetLosesOnAverage() {
        bigWheel();
        roulette();
        highLow();
        horseRace();
        record("スロット（5 ライン）", Paytable.DEFAULT.returnRate());
        baccarat();
        blackjack();
        casinoHoldem();
        report.forEach((name, rtp) -> System.out.printf("還元率 %-28s %6.2f%%%n", name, rtp * 100));
    }

    private void bigWheel() {
        for (BigWheel.Segment s : BigWheel.Segment.values()) {
            double back = 0;
            for (int i = 0; i < BigWheel.SEGMENTS; i++) {
                back += BigWheel.payout(s, 100, i);
            }
            record("ビッグホイール " + s, back / BigWheel.SEGMENTS / 100);
        }
    }

    private void roulette() {
        List<RouletteBet> bets = new ArrayList<>();
        for (int n = 0; n <= 36; n++) {
            bets.add(RouletteBet.straight(n, 100));
        }
        for (int a = 0; a <= 36; a++) {
            for (int b = a + 1; b <= 36; b++) {
                tryAdd(bets, a, b);
            }
        }
        for (IntFunction<RouletteBet> maker : List.<IntFunction<RouletteBet>>of(
                i -> RouletteBet.street(i, 100), i -> RouletteBet.corner(i, 100), i -> RouletteBet.line(i, 100),
                i -> RouletteBet.dozen(i, 100), i -> RouletteBet.column(i, 100))) {
            for (int i = 0; i <= 36; i++) {
                try {
                    bets.add(maker.apply(i));
                } catch (IllegalArgumentException ignored) {
                    // 置けない場所
                }
            }
        }
        for (BetType type : List.of(BetType.RED, BetType.BLACK, BetType.EVEN, BetType.ODD, BetType.LOW, BetType.HIGH)) {
            bets.add(RouletteBet.outside(type, 100));
        }
        Map<BetType, Double> worst = new LinkedHashMap<>();
        for (RouletteBet bet : bets) {
            double back = 0;
            for (int result = 0; result <= 36; result++) {
                back += bet.payout(result);
            }
            worst.merge(bet.type(), back / 37 / 100, Math::max);
        }
        worst.forEach((type, rtp) -> record("ルーレット " + type.displayName(), rtp));
    }

    private static void tryAdd(List<RouletteBet> bets, int a, int b) {
        try {
            bets.add(RouletteBet.split(a, b, 100));
        } catch (IllegalArgumentException ignored) {
            // 隣り合っていない
        }
    }

    private void highLow() {
        for (HighLow.Bet bet : HighLow.Bet.values()) {
            double best = 0;
            for (int base = 1; base <= 13; base++) {
                double back = 0;
                for (int next = 1; next <= 13; next++) {
                    back += HighLow.payout(bet, base, next, 10000);
                }
                best = Math.max(best, back / 13 / 10000);
            }
            record("ハイ＆ロー " + bet + "（一番よい基準）", best);
        }
    }

    private void horseRace() {
        Random random = new Random(1);
        double best = 0;
        for (int i = 0; i < 20000; i++) {
            HorseRace race = HorseRace.card(random);
            for (int h = 0; h < race.horses(); h++) {
                best = Math.max(best, race.chance(h) * race.percent(h) / 100);
            }
        }
        record("競馬 単勝（一番よい馬）", best);
    }

    private void baccarat() {
        Random random = new Random(2);
        Shoe shoe = Shoe.everyRound(8, random);
        Map<Baccarat.Bet, Long> back = new LinkedHashMap<>();
        int rounds = 300_000;
        for (int i = 0; i < rounds; i++) {
            shoe.shuffleIfNeeded();
            Baccarat hand = Baccarat.deal(shoe);
            for (Baccarat.Bet bet : Baccarat.Bet.values()) {
                back.merge(bet, hand.payout(bet, 100), Long::sum);
            }
        }
        back.forEach((bet, sum) -> record("バカラ " + bet + "（試行）", sum / (double) rounds / 100));
    }

    /** 基本戦略（6 組、ディーラーはソフト 17 で止まる、スプリット後のダブルあり）で遊んだときの還元率。 */
    private void blackjack() {
        Random random = new Random(3);
        Rules rules = Rules.DEFAULT;
        Shoe shoe = Shoe.everyRound(rules.decks(), random);
        long staked = 0;
        long returned = 0;
        int rounds = 400_000;
        for (int i = 0; i < rounds; i++) {
            BlackjackRound round = new BlackjackRound(rules, shoe, 1);
            round.bet(0, 100);
            List<BlackjackEvent> events = new ArrayList<>(round.deal());
            int up = 0;
            for (BlackjackEvent e : events) {
                if (e instanceof BlackjackEvent.DealerCard d && !d.hidden()) {
                    up = value(d.card());
                }
            }
            while (round.phase() == BlackjackRound.Phase.PLAYING) {
                Hand hand = round.hands(0).get(round.currentHand());
                events.addAll(round.act(0, basic(hand, up, round.allowedActions())));
            }
            for (BlackjackEvent e : events) {
                if (e instanceof BlackjackEvent.Settled s) {
                    staked += s.bet();
                    returned += s.payout();
                }
            }
        }
        // 最初の賭け金あたりの損益で表す（ダブル・スプリットで足した分は損益にだけ入る）
        record("ブラックジャック（基本戦略・試行）", 1 + (returned - staked) / (double) rounds / 100);
    }

    /** アンティ 1 枚あたりの還元率。目安どおりにコールしたとき（完全な戦略だと 97.84%）。 */
    private void casinoHoldem() {
        Random random = new Random(4);
        Shoe shoe = Shoe.everyRound(1, random);
        long back = 0;
        long staked = 0;
        int rounds = 400_000;
        for (int i = 0; i < rounds; i++) {
            shoe.shuffleIfNeeded();
            CasinoHoldem game = CasinoHoldem.deal(shoe, 1);
            boolean call = CasinoHoldem.suggestCall(game.hole(0), game.flop());
            staked += call ? 300 : 100;
            back += game.antePayout(0, call, 100) + (call ? game.callPayout(0, 200) : 0);
        }
        record("カジノホールデム（目安どおり・試行）", 1 + (back - staked) / (double) rounds / 100);
    }

    private static int value(Card card) {
        Rank rank = card.rank();
        return rank.ordinal() == 0 ? 11 : Math.min(10, rank.ordinal() + 1);
    }

    private static Action basic(Hand hand, int up, List<Action> allowed) {
        List<Card> cards = hand.cards();
        boolean canDouble = allowed.contains(Action.DOUBLE);
        if (allowed.contains(Action.SPLIT)) {
            int v = value(cards.get(0));
            boolean split = switch (v) {
                case 11, 8 -> true;
                case 9 -> up <= 9 && up != 7;
                case 7 -> up <= 7;
                case 6 -> up <= 6;
                case 4 -> up == 5 || up == 6;
                case 2, 3 -> up <= 7;
                default -> false;
            };
            if (split) {
                return Action.SPLIT;
            }
        }
        int total = hand.total();
        if (hand.soft() && total <= 21) {
            if (total >= 19) {
                return Action.STAND;
            }
            if (total == 18) {
                if (up >= 3 && up <= 6 && canDouble) {
                    return Action.DOUBLE;
                }
                return up >= 9 ? Action.HIT : Action.STAND;
            }
            int from = total == 17 ? 3 : total >= 15 ? 4 : 5;
            return up >= from && up <= 6 && canDouble ? Action.DOUBLE : Action.HIT;
        }
        if (total >= 17) {
            return Action.STAND;
        }
        if (total >= 13) {
            return up <= 6 ? Action.STAND : Action.HIT;
        }
        if (total == 12) {
            return up >= 4 && up <= 6 ? Action.STAND : Action.HIT;
        }
        if (total == 11) {
            return canDouble && up <= 10 ? Action.DOUBLE : Action.HIT;
        }
        if (total == 10) {
            return canDouble && up <= 9 ? Action.DOUBLE : Action.HIT;
        }
        if (total == 9) {
            return canDouble && up >= 3 && up <= 6 ? Action.DOUBLE : Action.HIT;
        }
        return Action.HIT;
    }
}
