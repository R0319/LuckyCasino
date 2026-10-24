package io.github.r0319.luckycasino.blackjack;

import io.github.r0319.luckycasino.blackjack.BlackjackEvent.DealerCard;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.DealerDone;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.Doubled;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.HandDone;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.HoleCardRevealed;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.PlayerCard;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.RoundOver;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.Settled;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.Split;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent.Turn;
import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Shoe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ブラックジャック 1 回分の勝負。時間やエンティティを持たない純粋な処理で、
 * 操作を受けるたびに起きたこと（BlackjackEvent）の一覧を返す。表示側はそれを順に再生する。
 *
 * <p>チップの出し入れは呼び出し側が行う。賭けるとき・ダブルダウン・スプリットの前に
 * {@link #extraStake(int)} の分を預かり、精算イベントの payout を返す。
 */
public final class BlackjackRound {

    public enum Phase {
        BETTING, PLAYING, FINISHED
    }

    private final Rules rules;
    private final Shoe shoe;
    private final int seatCount;
    private final Map<Integer, Long> bets = new TreeMap<>();
    private final Map<Integer, List<Hand>> hands = new TreeMap<>();
    private final Hand dealer = new Hand(0, false);
    private Phase phase = Phase.BETTING;
    private int currentSeat = -1;
    private int currentHand = -1;

    public BlackjackRound(Rules rules, Shoe shoe, int seatCount) {
        this.rules = rules;
        this.shoe = shoe;
        this.seatCount = seatCount;
    }

    // ---- 賭ける ----

    /** 賭け金を決める（置き直しは上書き）。 */
    public void bet(int seat, long amount) {
        requirePhase(Phase.BETTING);
        checkSeat(seat);
        if (amount < rules.minBet() || amount > rules.maxBet()) {
            throw new IllegalArgumentException("bet out of range: " + amount);
        }
        bets.put(seat, amount);
    }

    public void clearBet(int seat) {
        requirePhase(Phase.BETTING);
        bets.remove(seat);
    }

    public Map<Integer, Long> bets() {
        return Collections.unmodifiableMap(bets);
    }

    // ---- 配る ----

    /** 賭けた席とディーラーに 2 枚ずつ配り、最初の番まで進める。 */
    public List<BlackjackEvent> deal() {
        requirePhase(Phase.BETTING);
        if (bets.isEmpty()) {
            throw new IllegalStateException("no bets");
        }
        shoe.shuffleIfNeeded();
        phase = Phase.PLAYING;
        List<BlackjackEvent> events = new ArrayList<>();
        bets.forEach((seat, bet) -> hands.put(seat, new ArrayList<>(List.of(new Hand(bet, false)))));

        for (int round = 0; round < 2; round++) {
            for (Map.Entry<Integer, List<Hand>> entry : hands.entrySet()) {
                Card card = shoe.draw();
                entry.getValue().getFirst().add(card);
                events.add(new PlayerCard(entry.getKey(), 0, card));
            }
            Card card = shoe.draw();
            dealer.add(card);
            boolean hidden = round == 1;
            events.add(new DealerCard(hidden ? null : card, hidden));
        }

        // 表のカードが A か 10 点のときは伏せたカードを確認し、ブラックジャックならすぐ精算する
        int upPoints = dealer.cards().getFirst().rank().points();
        if (upPoints == 10 || upPoints == 1) {
            if (dealer.blackjack()) {
                hands.values().forEach(list -> list.forEach(Hand::finish));
                events.add(new HoleCardRevealed(dealer.cards().get(1)));
                events.add(new DealerDone(dealer.total(), false));
                settle(events);
                return events;
            }
        }

        hands.forEach((seat, list) -> {
            Hand hand = list.getFirst();
            if (hand.blackjack()) {
                hand.finish();
                events.add(new HandDone(seat, 0, 21, false));
            }
        });
        advance(events);
        return events;
    }

    // ---- 行動 ----

    public int currentSeat() {
        return currentSeat;
    }

    public int currentHand() {
        return currentHand;
    }

    public Phase phase() {
        return phase;
    }

    /** 今の番の手で選べる行動。 */
    public List<Action> allowedActions() {
        if (phase != Phase.PLAYING || currentSeat < 0) {
            return List.of();
        }
        List<Hand> list = hands.get(currentSeat);
        Hand hand = list.get(currentHand);
        List<Action> actions = new ArrayList<>(List.of(Action.HIT, Action.STAND));
        if (hand.cards().size() == 2) {
            if (!hand.fromSplit() || rules.doubleAfterSplit()) {
                actions.add(Action.DOUBLE);
            }
            if (list.size() == 1 && hand.cards().get(0).rank() == hand.cards().get(1).rank()) {
                actions.add(Action.SPLIT);
            }
        }
        return List.copyOf(actions);
    }

    /**
     * その行動のために追加で預かるチップ。ダブルダウンとスプリットは今の手の賭け金と同額、それ以外は 0。
     */
    public long extraStake(Action action) {
        if (action != Action.DOUBLE && action != Action.SPLIT) {
            return 0;
        }
        return hands.get(currentSeat).get(currentHand).bet();
    }

    /** 今の番の席が行動する。番でない席や選べない行動は例外。 */
    public List<BlackjackEvent> act(int seat, Action action) {
        requirePhase(Phase.PLAYING);
        if (seat != currentSeat) {
            throw new IllegalStateException("not seat " + seat + "'s turn");
        }
        if (!allowedActions().contains(action)) {
            throw new IllegalStateException(action + " is not allowed now");
        }
        List<BlackjackEvent> events = new ArrayList<>();
        List<Hand> list = hands.get(seat);
        Hand hand = list.get(currentHand);
        switch (action) {
            case HIT -> {
                draw(seat, currentHand, hand, events);
                if (hand.total() >= 21) {
                    finish(seat, currentHand, hand, events);
                }
            }
            case STAND -> finish(seat, currentHand, hand, events);
            case DOUBLE -> {
                hand.doubleBet();
                events.add(new Doubled(seat, currentHand, hand.bet()));
                draw(seat, currentHand, hand, events);
                finish(seat, currentHand, hand, events);
            }
            case SPLIT -> {
                Hand second = new Hand(hand.bet(), true);
                Hand first = new Hand(hand.bet(), true);
                first.add(hand.cards().getFirst());
                second.add(hand.cards().get(1));
                list.set(currentHand, first);
                list.add(second);
                events.add(new Split(seat, currentHand, list.size() - 1));
                draw(seat, currentHand, first, events);
                if (first.splitAces()) {
                    // A を分けたときは各 1 枚だけ
                    finish(seat, currentHand, first, events);
                    draw(seat, list.size() - 1, second, events);
                    finish(seat, list.size() - 1, second, events);
                } else if (first.total() == 21) {
                    finish(seat, currentHand, first, events);
                }
            }
        }
        advance(events);
        return events;
    }

    /** 時間切れや離席。今の番の手をスタンドとして進める。 */
    public List<BlackjackEvent> timeout() {
        requirePhase(Phase.PLAYING);
        return act(currentSeat, Action.STAND);
    }

    public List<Hand> hands(int seat) {
        return Collections.unmodifiableList(hands.getOrDefault(seat, List.of()));
    }

    /** ディーラーの手。勝負中は伏せたカードも含むので、表示や通信に使わないこと。 */
    Hand dealerHand() {
        return dealer;
    }

    // ---- 内部 ----

    private void draw(int seat, int index, Hand hand, List<BlackjackEvent> events) {
        Card card = shoe.draw();
        hand.add(card);
        events.add(new PlayerCard(seat, index, card));
    }

    private void finish(int seat, int index, Hand hand, List<BlackjackEvent> events) {
        if (!hand.finished()) {
            hand.finish();
            events.add(new HandDone(seat, index, hand.total(), hand.bust()));
        }
    }

    /** 次に行動する手へ進める。残っていなければディーラーの番と精算まで進める。 */
    private void advance(List<BlackjackEvent> events) {
        for (Map.Entry<Integer, List<Hand>> entry : hands.entrySet()) {
            int seat = entry.getKey();
            List<Hand> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                Hand hand = list.get(i);
                if (hand.finished()) {
                    continue;
                }
                if (hand.cards().size() == 1) {
                    // スプリットした 2 つ目の手は、番が来たときに 2 枚目を配る
                    draw(seat, i, hand, events);
                    if (hand.total() == 21) {
                        finish(seat, i, hand, events);
                        continue;
                    }
                }
                currentSeat = seat;
                currentHand = i;
                events.add(new Turn(seat, i, allowedActions()));
                return;
            }
        }
        currentSeat = -1;
        currentHand = -1;
        playDealer(events);
        settle(events);
    }

    private void playDealer(List<BlackjackEvent> events) {
        events.add(new HoleCardRevealed(dealer.cards().get(1)));
        boolean anyLive = hands.values().stream().flatMap(List::stream)
                .anyMatch(hand -> !hand.bust() && !hand.blackjack());
        if (anyLive) {
            while (dealer.total() < 17 || (rules.dealerHitsSoft17() && dealer.total() == 17 && dealer.soft())) {
                Card card = shoe.draw();
                dealer.add(card);
                events.add(new DealerCard(card, false));
            }
        }
        events.add(new DealerDone(dealer.total(), dealer.bust()));
    }

    private void settle(List<BlackjackEvent> events) {
        hands.forEach((seat, list) -> {
            for (int i = 0; i < list.size(); i++) {
                Hand hand = list.get(i);
                Outcome outcome = outcome(hand);
                events.add(new Settled(seat, i, outcome, hand.bet(), outcome.payout(hand.bet())));
            }
        });
        phase = Phase.FINISHED;
        events.add(new RoundOver());
    }

    private Outcome outcome(Hand hand) {
        if (hand.bust()) {
            return Outcome.BUST;
        }
        if (dealer.blackjack()) {
            return hand.blackjack() ? Outcome.PUSH : Outcome.LOSE;
        }
        if (hand.blackjack()) {
            return Outcome.BLACKJACK;
        }
        if (dealer.bust() || hand.total() > dealer.total()) {
            return Outcome.WIN;
        }
        return hand.total() == dealer.total() ? Outcome.PUSH : Outcome.LOSE;
    }

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("phase is " + phase + ", expected " + expected);
        }
    }

    private void checkSeat(int seat) {
        if (seat < 0 || seat >= seatCount) {
            throw new IllegalArgumentException("invalid seat: " + seat);
        }
    }
}
