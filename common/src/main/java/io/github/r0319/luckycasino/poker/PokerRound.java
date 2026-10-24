package io.github.r0319.luckycasino.poker;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Shoe;
import io.github.r0319.luckycasino.poker.PokerEvent.Acted;
import io.github.r0319.luckycasino.poker.PokerEvent.BetsCollected;
import io.github.r0319.luckycasino.poker.PokerEvent.Blind;
import io.github.r0319.luckycasino.poker.PokerEvent.Board;
import io.github.r0319.luckycasino.poker.PokerEvent.ButtonMoved;
import io.github.r0319.luckycasino.poker.PokerEvent.HandOver;
import io.github.r0319.luckycasino.poker.PokerEvent.HoleCards;
import io.github.r0319.luckycasino.poker.PokerEvent.Refund;
import io.github.r0319.luckycasino.poker.PokerEvent.Shown;
import io.github.r0319.luckycasino.poker.PokerEvent.Turn;
import io.github.r0319.luckycasino.poker.PokerEvent.Won;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * テキサスホールデム（ノーリミット）1 回分。席番号は時計回り（左隣 = 番号が 1 つ大きい席）。
 * 操作を受けるたびに起きたこと（PokerEvent）の一覧を返す。チップは席ごとの stack で持ち、
 * 終わったら {@link #stacks()} を呼び出し側の手元に戻す。
 */
public final class PokerRound {

    private static final class Player {
        final int seat;
        long stack;
        long streetBet;
        long contributed;
        boolean folded;
        boolean allIn;
        boolean acted;
        boolean mayRaise = true;
        Card first;
        Card second;

        Player(int seat, long stack) {
            this.seat = seat;
            this.stack = stack;
        }

        boolean canAct() {
            return !folded && !allIn;
        }
    }

    private final Map<Integer, Player> players = new TreeMap<>();
    private final List<Integer> order;
    private final int seatCount;
    private final int button;
    private final long smallBlind;
    private final long bigBlind;
    private final Shoe deck;
    private final List<Card> board = new ArrayList<>();

    private Street street = Street.PREFLOP;
    private long currentBet;
    private long lastRaise;
    private long collected;
    private int current = -1;
    private boolean finished;

    /**
     * @param stacks    勝負に参加する席と持っているチップ（2 人以上、それぞれ 1 枚以上）
     * @param seatCount テーブルの席の数（時計回りの判定に使う）
     * @param button    ディーラーボタンの席（参加者の席）
     */
    public PokerRound(Map<Integer, Long> stacks, int seatCount, int button, long smallBlind, long bigBlind, Shoe deck) {
        if (stacks.size() < 2) {
            throw new IllegalArgumentException("need at least 2 players");
        }
        if (!stacks.containsKey(button)) {
            throw new IllegalArgumentException("button must be a player");
        }
        stacks.forEach((seat, stack) -> {
            if (stack <= 0) {
                throw new IllegalArgumentException("stack must be positive");
            }
            players.put(seat, new Player(seat, stack));
        });
        this.order = new ArrayList<>(players.keySet());
        this.seatCount = seatCount;
        this.button = button;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.deck = deck;
    }

    // ---- 始める ----

    /** ブラインドを出し、手札を配り、最初の番まで進める。 */
    public List<PokerEvent> start() {
        List<PokerEvent> events = new ArrayList<>();
        events.add(new ButtonMoved(button));
        boolean headsUp = players.size() == 2;
        // ヘッズアップではボタンがスモールブラインド
        int sb = headsUp ? button : next(button);
        int bb = next(sb);
        postBlind(sb, smallBlind, false, events);
        postBlind(bb, bigBlind, true, events);
        currentBet = bigBlind;
        lastRaise = bigBlind;

        for (int round = 0; round < 2; round++) {
            for (int seat : clockwiseFrom(next(button))) {
                Player p = players.get(seat);
                if (round == 0) {
                    p.first = deck.draw();
                } else {
                    p.second = deck.draw();
                }
            }
        }
        for (Player p : players.values()) {
            events.add(new HoleCards(p.seat, p.first, p.second));
        }
        current = bb;
        advance(events);
        return events;
    }

    private void postBlind(int seat, long amount, boolean big, List<PokerEvent> events) {
        Player p = players.get(seat);
        long paid = Math.min(amount, p.stack);
        p.stack -= paid;
        p.streetBet += paid;
        p.contributed += paid;
        if (p.stack == 0) {
            p.allIn = true;
        }
        events.add(new Blind(seat, paid, big));
    }

    // ---- 状態 ----

    public boolean finished() {
        return finished;
    }

    public int currentSeat() {
        return finished ? -1 : current;
    }

    public Street street() {
        return street;
    }

    public List<Card> board() {
        return Collections.unmodifiableList(board);
    }

    /** ポットの合計（集め終えた分 + このラウンドで出ている分）。 */
    public long pot() {
        long pot = collected;
        for (Player p : players.values()) {
            pot += p.streetBet;
        }
        return pot;
    }

    /** 席ごとの今のチップ（終わった後は精算済み）。 */
    public Map<Integer, Long> stacks() {
        Map<Integer, Long> out = new TreeMap<>();
        players.forEach((seat, p) -> out.put(seat, p.stack));
        return out;
    }

    public boolean inHand(int seat) {
        Player p = players.get(seat);
        return p != null && !p.folded;
    }

    public long streetBet(int seat) {
        Player p = players.get(seat);
        return p == null ? 0 : p.streetBet;
    }

    /** 今の番で選べる行動。 */
    public Legal legal() {
        if (finished) {
            throw new IllegalStateException("hand is over");
        }
        Player p = players.get(current);
        long toCall = Math.min(currentBet - p.streetBet, p.stack);
        long max = p.streetBet + p.stack;
        boolean othersCanAct = players.values().stream().anyMatch(o -> o != p && o.canAct());
        boolean canRaise = p.mayRaise && p.stack > currentBet - p.streetBet && othersCanAct;
        long minTo = currentBet == 0 ? bigBlind : currentBet + lastRaise;
        return new Legal(toCall, canRaise, Math.min(minTo, max), max, p.streetBet);
    }

    // ---- 行動 ----

    public List<PokerEvent> fold(int seat) {
        Player p = turn(seat);
        p.folded = true;
        p.acted = true;
        List<PokerEvent> events = new ArrayList<>();
        events.add(new Acted(seat, PokerAction.FOLD, 0, p.streetBet, false));
        advance(events);
        return events;
    }

    public List<PokerEvent> check(int seat) {
        Player p = turn(seat);
        if (currentBet != p.streetBet) {
            throw new IllegalStateException("cannot check");
        }
        p.acted = true;
        p.mayRaise = false;
        List<PokerEvent> events = new ArrayList<>();
        events.add(new Acted(seat, PokerAction.CHECK, 0, p.streetBet, false));
        advance(events);
        return events;
    }

    public List<PokerEvent> call(int seat) {
        Player p = turn(seat);
        long toCall = Math.min(currentBet - p.streetBet, p.stack);
        if (toCall <= 0) {
            throw new IllegalStateException("nothing to call");
        }
        pay(p, toCall);
        p.acted = true;
        p.mayRaise = false;
        List<PokerEvent> events = new ArrayList<>();
        events.add(new Acted(seat, PokerAction.CALL, toCall, p.streetBet, p.allIn));
        advance(events);
        return events;
    }

    /**
     * ベット（誰も賭けていないとき）またはレイズ。amount はこのラウンドで出す合計。
     * 最低額に届かないのはオールインのときだけ認める。
     */
    public List<PokerEvent> raiseTo(int seat, long amount) {
        Player p = turn(seat);
        Legal legal = legal();
        if (!legal.canRaise()) {
            throw new IllegalStateException("cannot raise");
        }
        if (amount > legal.maxRaiseTo() || (amount < legal.minRaiseTo() && amount != legal.maxRaiseTo()) || amount <= currentBet) {
            throw new IllegalArgumentException("invalid raise: " + amount);
        }
        PokerAction action = currentBet == 0 ? PokerAction.BET : PokerAction.RAISE;
        long raise = amount - currentBet;
        boolean full = raise >= lastRaise;
        long added = amount - p.streetBet;
        pay(p, added);
        currentBet = amount;
        if (full) {
            lastRaise = raise;
        }
        for (Player o : players.values()) {
            if (o == p || !o.canAct()) {
                continue;
            }
            if (full) {
                o.mayRaise = true;
            }
            // 足りないオールインのときは、既に行動した人はコールかフォールドだけ（mayRaise はそのまま）
            o.acted = false;
        }
        p.acted = true;
        p.mayRaise = false;
        List<PokerEvent> events = new ArrayList<>();
        events.add(new Acted(seat, action, added, p.streetBet, p.allIn));
        advance(events);
        return events;
    }

    /** 手持ちを全部出す（レイズできなければコール）。 */
    public List<PokerEvent> allIn(int seat) {
        turn(seat);
        Legal legal = legal();
        if (legal.canRaise()) {
            return raiseTo(seat, legal.maxRaiseTo());
        }
        if (legal.toCall() > 0) {
            return call(seat);
        }
        return check(seat);
    }

    /** 時間切れや離席。チェックできればチェック、でなければフォールド。 */
    public List<PokerEvent> timeout(int seat) {
        Player p = turn(seat);
        return currentBet == p.streetBet ? check(seat) : fold(seat);
    }

    private Player turn(int seat) {
        if (finished) {
            throw new IllegalStateException("hand is over");
        }
        if (seat != current) {
            throw new IllegalStateException("not seat " + seat + "'s turn");
        }
        return players.get(seat);
    }

    private void pay(Player p, long amount) {
        p.stack -= amount;
        p.streetBet += amount;
        p.contributed += amount;
        if (p.stack == 0) {
            p.allIn = true;
        }
    }

    // ---- 進行 ----

    private void advance(List<PokerEvent> events) {
        List<Player> live = players.values().stream().filter(p -> !p.folded).toList();
        if (live.size() == 1) {
            collect(events);
            finishByFold(live.getFirst(), events);
            return;
        }
        if (!roundComplete()) {
            int next = nextToAct(current);
            if (next >= 0) {
                current = next;
                events.add(new Turn(current, legal()));
                return;
            }
        }
        collect(events);
        long canAct = players.values().stream().filter(Player::canAct).count();
        if (street == Street.RIVER || canAct <= 1) {
            // 行動できる人が 1 人以下なら、残りの共有カードを全部開けてショーダウン
            while (street != Street.RIVER) {
                nextStreet(events);
            }
            showdown(events);
            return;
        }
        nextStreet(events);
        current = button;
        int first = nextToAct(button);
        current = first;
        events.add(new Turn(current, legal()));
    }

    private boolean roundComplete() {
        for (Player p : players.values()) {
            if (p.canAct() && (!p.acted || p.streetBet != currentBet)) {
                return false;
            }
        }
        return true;
    }

    /** from の左隣から時計回りに、行動が必要な人を探す。 */
    private int nextToAct(int from) {
        for (int seat : clockwiseFrom(next(from))) {
            Player p = players.get(seat);
            if (p.canAct() && (!p.acted || p.streetBet != currentBet)) {
                return seat;
            }
        }
        return -1;
    }

    private void collect(List<PokerEvent> events) {
        long bets = 0;
        for (Player p : players.values()) {
            bets += p.streetBet;
            collected += p.streetBet;
            p.streetBet = 0;
        }
        if (bets > 0) {
            events.add(new BetsCollected(collected));
        }
    }

    private void nextStreet(List<PokerEvent> events) {
        street = Street.values()[street.ordinal() + 1];
        List<Card> cards = new ArrayList<>();
        int count = street == Street.FLOP ? 3 : 1;
        for (int i = 0; i < count; i++) {
            cards.add(deck.draw());
        }
        board.addAll(cards);
        events.add(new Board(street, List.copyOf(cards)));
        currentBet = 0;
        lastRaise = bigBlind;
        for (Player p : players.values()) {
            p.acted = false;
            p.mayRaise = true;
        }
    }

    /** 誰も合わせられなかった分（一番多く出した人と 2 番目の差）を返す。 */
    private void refundUncalled(List<PokerEvent> events) {
        Player top = null;
        long second = 0;
        for (Player p : players.values()) {
            if (top == null || p.contributed > top.contributed) {
                if (top != null) {
                    second = Math.max(second, top.contributed);
                }
                top = p;
            } else {
                second = Math.max(second, p.contributed);
            }
        }
        long excess = top.contributed - second;
        if (excess > 0) {
            top.contributed -= excess;
            top.stack += excess;
            collected -= excess;
            events.add(new Refund(top.seat, excess));
        }
    }

    private void finishByFold(Player winner, List<PokerEvent> events) {
        refundUncalled(events);
        long pot = collected;
        winner.stack += pot;
        collected = 0;
        events.add(new Won(winner.seat, pot, 0, null));
        end(events);
    }

    private void showdown(List<PokerEvent> events) {
        refundUncalled(events);
        Map<Integer, HandValue> values = new TreeMap<>();
        for (int seat : clockwiseFrom(next(button))) {
            Player p = players.get(seat);
            if (p.folded) {
                continue;
            }
            List<Card> seven = new ArrayList<>(board);
            seven.add(p.first);
            seven.add(p.second);
            HandValue value = HandEvaluator.best(seven);
            values.put(seat, value);
            events.add(new Shown(seat, p.first, p.second, value));
        }
        int potIndex = 0;
        for (Pot pot : pots()) {
            long best = -1;
            List<Integer> winners = new ArrayList<>();
            for (int seat : clockwiseFrom(next(button))) {
                if (!pot.eligible.contains(seat)) {
                    continue;
                }
                long score = values.get(seat).score();
                if (score > best) {
                    best = score;
                    winners.clear();
                    winners.add(seat);
                } else if (score == best) {
                    winners.add(seat);
                }
            }
            long share = pot.amount / winners.size();
            long odd = pot.amount % winners.size();
            // 割り切れない分は、ボタンの左から順に 1 枚ずつ
            for (int i = 0; i < winners.size(); i++) {
                int seat = winners.get(i);
                long amount = share + (i < odd ? 1 : 0);
                players.get(seat).stack += amount;
                events.add(new Won(seat, amount, potIndex, values.get(seat)));
            }
            potIndex++;
        }
        collected = 0;
        end(events);
    }

    private record Pot(long amount, TreeSet<Integer> eligible) {
    }

    /** 出した額の段ごとにポットを分ける（メインポットとサイドポット）。 */
    private List<Pot> pots() {
        TreeSet<Long> levels = new TreeSet<>();
        for (Player p : players.values()) {
            if (!p.folded && p.contributed > 0) {
                levels.add(p.contributed);
            }
        }
        List<Pot> pots = new ArrayList<>();
        long previous = 0;
        for (long level : levels) {
            long amount = 0;
            TreeSet<Integer> eligible = new TreeSet<>();
            for (Player p : players.values()) {
                amount += Math.max(0, Math.min(p.contributed, level) - previous);
                if (!p.folded && p.contributed >= level) {
                    eligible.add(p.seat);
                }
            }
            if (amount > 0) {
                pots.add(new Pot(amount, eligible));
            }
            previous = level;
        }
        // 降りた人が一番上の段より多く出していた分（ふつうは返金で無くなる）も最後のポットへ
        long rest = 0;
        for (Player p : players.values()) {
            rest += Math.max(0, p.contributed - previous);
        }
        if (rest > 0 && !pots.isEmpty()) {
            Pot last = pots.removeLast();
            pots.add(new Pot(last.amount + rest, last.eligible));
        }
        return pots;
    }

    private void end(List<PokerEvent> events) {
        finished = true;
        current = -1;
        events.add(new HandOver());
    }

    // ---- 席の順番 ----

    private int next(int seat) {
        for (int i = 1; i <= seatCount; i++) {
            int candidate = (seat + i) % seatCount;
            if (players.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("no players");
    }

    /** start から時計回りに、参加者の席を 1 周分。 */
    private List<Integer> clockwiseFrom(int start) {
        List<Integer> out = new ArrayList<>();
        int seat = start;
        for (int i = 0; i < order.size(); i++) {
            out.add(seat);
            seat = next(seat);
        }
        return out;
    }

    /** 自分の手札（本人に見せる表示用）。 */
    public List<Card> holeCards(int seat) {
        Player p = players.get(seat);
        return p == null ? List.of() : List.of(p.first, p.second);
    }
}
