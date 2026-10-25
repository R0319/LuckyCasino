package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.blackjack.Action;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent;
import io.github.r0319.luckycasino.blackjack.BlackjackRound;
import io.github.r0319.luckycasino.blackjack.Hand;
import io.github.r0319.luckycasino.blackjack.Outcome;
import io.github.r0319.luckycasino.bridge.CardCode;
import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Rank;
import io.github.r0319.luckycasino.card.Shoe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** ブラックジャックのテーブル。BlackjackRound のイベントを順に再生して見せる。 */
final class BlackjackTable extends CasinoTable {

    enum State {
        /** 誰も賭けていない */
        IDLE,
        /** 誰かが賭けて、締め切りを数えている */
        BETTING,
        /** 配ってから片付けるまで */
        PLAYING
    }

    private static final BlackjackLayout LAYOUT = new BlackjackLayout();

    /** 手札の合計の文字の大きさ。席の間隔（0.8 ブロック）に「ブラックジャック」が収まる大きさ。 */
    private static final float HAND_LABEL_SCALE = 0.45f;

    private final BlackjackSettings settings;
    private final Shoe shoe;
    private final boolean[] confirmed = new boolean[BlackjackLayout.SEATS];

    private State state = State.IDLE;
    private BlackjackRound round;
    private final Deque<BlackjackEvent> queue = new ArrayDeque<>();
    private boolean waitingInput;
    private boolean pumping;
    private int countdown;

    // 勝負中の表示
    private final List<List<List<Card>>> handCards = new ArrayList<>();
    private final List<List<List<CardView>>> handViews = new ArrayList<>();
    private final List<List<ChipView>> betViews = new ArrayList<>();
    private final List<List<TextDisplay>> handLabels = new ArrayList<>();
    private final List<ChipView> payoutViews = new ArrayList<>();
    private final List<Card> dealerCards = new ArrayList<>();
    private final List<CardView> dealerViews = new ArrayList<>();
    private TextDisplay dealerLabel;
    private final ChipView[] pendingBetViews = new ChipView[BlackjackLayout.SEATS];

    BlackjackTable(LuckyCasinoPaper plugin, String id, Frame frame, UUID owner, Bank bank, BlackjackSettings settings) {
        super(plugin, id, GameType.BLACKJACK, frame, owner, bank);
        this.settings = settings;
        this.shoe = new Shoe(settings.rules().decks(), settings.rules().penetration(), new SecureRandom());
        for (int i = 0; i < BlackjackLayout.SEATS; i++) {
            handCards.add(new ArrayList<>());
            handViews.add(new ArrayList<>());
            betViews.add(new ArrayList<>());
            handLabels.add(new ArrayList<>());
            pendingBetViews[i] = new ChipView();
        }
        newRound();
    }

    @Override
    TableShape shape() {
        return LAYOUT;
    }

    @Override
    List<Long> buyIns() {
        return settings.buyIns();
    }

    @Override
    long minChips() {
        return settings.rules().minBet();
    }

    BlackjackSettings settings() {
        return settings;
    }

    State state() {
        return state;
    }

    @Override
    protected void onSpawn() {
    }

    @Override
    protected void onDespawn() {
        queue.clear();
        waitingInput = false;
        pumping = false;
        clearRoundViews();
        for (ChipView view : pendingBetViews) {
            view.remove();
        }
        newRound();
        state = State.IDLE;
        java.util.Arrays.fill(confirmed, false);
    }

    @Override
    protected void onSeated(int seat, Player player) {
        if (player == null) {
            if (state != State.PLAYING) {
                later(10, () -> botBet(seat));
            }
        } else if (state != State.PLAYING) {
            plugin.menus().openBet(player, this);
        } else {
            player.sendMessage(Component.text("今の勝負が終わったら賭けられます", NamedTextColor.GRAY));
        }
    }

    @Override
    void openMenu(Player player) {
        plugin.menus().openBet(player, this);
    }

    @Override
    protected boolean prepareLeave(int index) {
        Seat seat = seats[index];
        if (state != State.PLAYING && confirmed[index]) {
            round.clearBet(index);
            seat.chips += seat.atRisk;
            seat.atRisk = 0;
            confirmed[index] = false;
            pendingBetViews[index].remove();
            later(1, this::checkAllConfirmed);
        }
        return !(state == State.PLAYING && seat.atRisk > 0);
    }

    @Override
    protected void onAway(int index) {
        if (waitingInput && round.currentSeat() == index) {
            act(index, Action.STAND);
        }
    }

    // ---- 賭ける ----

    boolean canBet(int index) {
        Seat seat = seats[index];
        return seat != null && state != State.PLAYING && !confirmed[index] && !seat.away;
    }

    /** 賭け金を決める。 */
    void confirmBet(Player player, long amount) {
        int index = seatOf(player.getUniqueId());
        if (index < 0 || !canBet(index)) {
            return;
        }
        if (!placeBet(index, amount)) {
            player.sendMessage(Component.text("賭け金は " + settings.rules().minBet() + "〜" + settings.rules().maxBet()
                    + " 枚、手元のチップ以内で決めてください", NamedTextColor.RED));
        }
    }

    private boolean placeBet(int index, long amount) {
        Seat seat = seats[index];
        if (amount < settings.rules().minBet() || amount > settings.rules().maxBet() || amount > seat.chips) {
            return false;
        }
        round.bet(index, amount);
        seat.chips -= amount;
        seat.atRisk += amount;
        confirmed[index] = true;
        writeLedger(seat);
        pendingBetViews[index].show(frame.at(LAYOUT.chips(index)), amount);
        playSound(Sound.BLOCK_CHAIN_PLACE, 1.4f);
        Player player = online(seat);
        if (player != null) {
            player.sendActionBar(Component.text(amount + " 枚賭けました", NamedTextColor.GOLD)
                    .append(Component.text("（手元 " + seat.chips + " 枚）", NamedTextColor.GRAY)));
        }
        if (state == State.IDLE) {
            state = State.BETTING;
            countdown = settings.bettingSeconds();
            broadcast(Component.text("ベットを受け付けています（あと " + countdown + " 秒）", NamedTextColor.AQUA));
        }
        checkAllConfirmed();
        updateStatus();
        return true;
    }

    private void botBet(int index) {
        Seat seat = seats[index];
        if (seat == null || !seat.bot || !canBet(index)) {
            return;
        }
        long min = settings.rules().minBet();
        long max = Math.min(settings.rules().maxBet(), seat.chips);
        if (max < min) {
            return;
        }
        long amount = min * ThreadLocalRandom.current().nextLong(1, Math.max(2, max / min / 4 + 2));
        placeBet(index, Math.min(amount, max));
    }

    /** 席にいる全員が賭け終えたら、すぐ配る。 */
    private void checkAllConfirmed() {
        if (state != State.BETTING) {
            return;
        }
        boolean anyBet = false;
        for (int i = 0; i < seats.length; i++) {
            Seat seat = seats[i];
            if (seat == null || seat.away) {
                continue;
            }
            if (confirmed[i]) {
                anyBet = true;
            } else if (seat.chips >= settings.rules().minBet()) {
                return;
            }
        }
        if (!anyBet && round.bets().isEmpty()) {
            state = State.IDLE;
            updateStatus();
            return;
        }
        countdown = Math.min(countdown, 2);
    }

    // ---- 進行 ----

    private void newRound() {
        round = new BlackjackRound(settings.rules(), shoe, BlackjackLayout.SEATS);
    }

    @Override
    protected void tickSecond() {
        if (state == State.BETTING) {
            countdown--;
            if (countdown <= 0) {
                deal();
            }
            updateStatus();
        } else if (state == State.PLAYING && waitingInput) {
            countdown--;
            int index = round.currentSeat();
            Seat seat = index >= 0 ? seats[index] : null;
            showStakes(index);
            Player player = online(seat);
            if (player != null) {
                player.sendActionBar(Component.text("あなたの番です（あと " + Math.max(countdown, 0) + " 秒）", NamedTextColor.YELLOW)
                        .append(Component.text("　賭け " + seat.atRisk + " 枚", NamedTextColor.GOLD)));
            }
            if (countdown <= 0 && index >= 0) {
                if (seat != null) {
                    send(seat, Component.text("時間切れのため、スタンドしました", NamedTextColor.GRAY));
                }
                act(index, Action.STAND);
            }
        }
    }

    /** 勝負中、自分の番でない人のアクションバーに、賭けている額と手元を出し続ける。 */
    private void showStakes(int current) {
        for (int i = 0; i < seats.length; i++) {
            Seat seat = seats[i];
            Player player = i == current ? null : online(seat);
            if (player != null && seat.atRisk > 0) {
                player.sendActionBar(Component.text("あなたの賭け " + seat.atRisk + " 枚", NamedTextColor.GOLD)
                        .append(Component.text("（手元 " + seat.chips + " 枚）", NamedTextColor.GRAY)));
            }
        }
    }

    private void deal() {
        if (round.bets().isEmpty()) {
            state = State.IDLE;
            return;
        }
        state = State.PLAYING;
        for (int i = 0; i < seats.length; i++) {
            pendingBetViews[i].remove();
            Long bet = round.bets().get(i);
            if (bet != null) {
                handCards.get(i).add(new ArrayList<>());
                handViews.get(i).add(new ArrayList<>());
                ChipView chips = new ChipView();
                chips.showPlain(frame.at(LAYOUT.chips(i)), bet);
                betViews.get(i).add(chips);
                handLabels.get(i).add(label(LAYOUT.handLabel(i, 0, 1), HAND_LABEL_SCALE));
                updateHandLabel(i, 0, null);
            }
        }
        dealerLabel = label(LAYOUT.dealerLabel(), HAND_LABEL_SCALE);
        broadcast(Component.text("ノーモアベット。配ります", NamedTextColor.AQUA));
        enqueue(round.deal());
    }

    /** 席の行動（コマンドやチャットのボタンから）。 */
    void act(Player player, Action action) {
        int index = seatOf(player.getUniqueId());
        if (index < 0 || state != State.PLAYING || !waitingInput || round.currentSeat() != index) {
            player.sendMessage(Component.text("今はあなたの番ではありません", NamedTextColor.RED));
            return;
        }
        if (!affordableActions(index).contains(action)) {
            player.sendMessage(Component.text("その行動は今は選べません", NamedTextColor.RED));
            return;
        }
        act(index, action);
    }

    private void act(int index, Action action) {
        if (!waitingInput || round.phase() != BlackjackRound.Phase.PLAYING || round.currentSeat() != index) {
            return;
        }
        Seat seat = seats[index];
        long extra = round.extraStake(action);
        if (seat != null && extra > 0) {
            seat.chips -= extra;
            seat.atRisk += extra;
            writeLedger(seat);
        }
        waitingInput = false;
        enqueue(round.act(index, action));
    }

    private List<Action> affordableActions(int index) {
        Seat seat = seats[index];
        List<Action> actions = new ArrayList<>();
        for (Action action : round.allowedActions()) {
            if (seat != null && round.extraStake(action) <= seat.chips) {
                actions.add(action);
            }
        }
        return actions;
    }

    private void enqueue(List<BlackjackEvent> events) {
        queue.addAll(events);
        pump();
    }

    /** イベントを 1 つずつ、それぞれの動きの長さだけ間を空けて再生する。 */
    private void pump() {
        if (pumping) {
            return;
        }
        pumping = true;
        try {
            while (!queue.isEmpty() && !waitingInput) {
                long delay = play(queue.poll());
                if (delay > 0) {
                    pumping = false;
                    later(delay, this::pump);
                    return;
                }
            }
        } finally {
            pumping = false;
        }
    }

    private long play(BlackjackEvent event) {
        return switch (event) {
            case BlackjackEvent.PlayerCard e -> {
                List<List<Card>> cards = handCards.get(e.seat());
                List<List<CardView>> views = handViews.get(e.seat());
                while (cards.size() <= e.hand()) {
                    cards.add(new ArrayList<>());
                    views.add(new ArrayList<>());
                }
                int index = cards.get(e.hand()).size();
                double[] at = LAYOUT.playerCard(e.seat(), e.hand(), cards.size(), index);
                views.get(e.hand()).add(CardView.deal(plugin, plugin.bridge(), frame.at(at),
                        Frame.delta(LAYOUT.shoe(), at), e.card().code(), true));
                cards.get(e.hand()).add(e.card());
                updateHandLabel(e.seat(), e.hand(), null);
                swing();
                yield 8;
            }
            case BlackjackEvent.DealerCard e -> {
                double[] at = LAYOUT.dealerCard(dealerViews.size());
                int code = e.hidden() ? CardCode.HIDDEN : e.card().code();
                dealerViews.add(CardView.deal(plugin, plugin.bridge(), frame.at(at),
                        Frame.delta(LAYOUT.shoe(), at), code, !e.hidden()));
                if (!e.hidden()) {
                    dealerCards.add(e.card());
                }
                updateDealerLabel();
                swing();
                yield 8;
            }
            case BlackjackEvent.HoleCardRevealed e -> {
                dealerCards.add(1, e.card());
                dealerViews.get(1).reveal(e.card().code());
                updateDealerLabel();
                yield 14;
            }
            case BlackjackEvent.Split e -> {
                splitViews(e.seat(), e.hand(), e.newHand());
                playSound(Sound.BLOCK_CHAIN_PLACE, 1.2f);
                yield 10;
            }
            case BlackjackEvent.Doubled e -> {
                betViews.get(e.seat()).get(e.hand()).showPlain(chipSpot(e.seat(), e.hand()), e.bet());
                updateHandLabel(e.seat(), e.hand(), null);
                playSound(Sound.BLOCK_CHAIN_PLACE, 1.4f);
                yield 4;
            }
            case BlackjackEvent.Turn e -> {
                startTurn(e.seat(), e.hand());
                yield 0;
            }
            case BlackjackEvent.HandDone e -> {
                updateHandLabel(e.seat(), e.hand(), null);
                if (e.bust()) {
                    playSound(Sound.ENTITY_VILLAGER_NO, 1f);
                }
                yield 6;
            }
            case BlackjackEvent.DealerDone e -> {
                updateDealerLabel();
                yield 12;
            }
            case BlackjackEvent.Settled e -> {
                settle(e);
                yield 10;
            }
            case BlackjackEvent.RoundOver e -> {
                for (Seat seat : seats) {
                    if (seat != null) {
                        writeLedger(seat);
                    }
                }
                later(70, this::finishRound);
                yield 0;
            }
        };
    }

    private void startTurn(int index, int hand) {
        Seat seat = seats[index];
        if (seat != null && seat.bot && !seat.away) {
            waitingInput = true;
            countdown = settings.turnSeconds();
            updateStatus();
            later(12, () -> botAct(index, hand));
            return;
        }
        Player player = online(seat);
        if (seat == null || seat.away || player == null) {
            // 席を立った人は自動でスタンド
            waitingInput = true;
            later(10, () -> act(index, Action.STAND));
            return;
        }
        waitingInput = true;
        countdown = settings.turnSeconds();
        updateStatus();
        int total = Hand.total(handCards.get(index).get(hand));
        player.showTitle(Title.title(Component.text("あなたの番", NamedTextColor.YELLOW),
                Component.text("合計 " + total, NamedTextColor.WHITE)
                        .append(Component.text("　賭け " + betViews.get(index).get(hand).amount() + " 枚", NamedTextColor.GOLD)),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(300))));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
        Component buttons = Component.empty();
        for (Action action : affordableActions(index)) {
            buttons = buttons.append(button(action)).append(Component.space());
        }
        player.sendMessage(Component.text("手札 " + (handCards.get(index).size() > 1 ? (hand + 1) + " つ目 " : "")
                + "合計 " + total + "（賭け " + betViews.get(index).get(hand).amount() + " 枚）： ", NamedTextColor.WHITE).append(buttons));
    }

    /** ボットの行動。分岐をひととおり通るよう、簡単な基本戦略に少し乱数を混ぜる。 */
    private void botAct(int index, int hand) {
        if (!waitingInput || round.currentSeat() != index) {
            return;
        }
        List<Action> actions = affordableActions(index);
        int total = Hand.total(handCards.get(index).get(hand));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Action action;
        if (actions.contains(Action.SPLIT) && random.nextInt(3) > 0) {
            action = Action.SPLIT;
        } else if (actions.contains(Action.DOUBLE) && total >= 9 && total <= 11) {
            action = Action.DOUBLE;
        } else if (total < 12 || (total < 17 && random.nextBoolean())) {
            action = Action.HIT;
        } else {
            action = Action.STAND;
        }
        act(index, action);
    }

    private static Component button(Action action) {
        String label = switch (action) {
            case HIT -> "ヒット";
            case STAND -> "スタンド";
            case DOUBLE -> "ダブルダウン";
            case SPLIT -> "スプリット";
        };
        String help = switch (action) {
            case HIT -> "もう 1 枚引く";
            case STAND -> "引かずに止める";
            case DOUBLE -> "賭け金を 2 倍にして 1 枚だけ引く";
            case SPLIT -> "同額を追加で賭けて 2 つの手に分ける";
        };
        NamedTextColor color = switch (action) {
            case HIT -> NamedTextColor.GREEN;
            case STAND -> NamedTextColor.RED;
            case DOUBLE -> NamedTextColor.GOLD;
            case SPLIT -> NamedTextColor.AQUA;
        };
        return Component.text("[" + label + "]", color)
                .hoverEvent(HoverEvent.showText(Component.text(help)))
                .clickEvent(ClickEvent.runCommand("/casino " + action.name().toLowerCase()));
    }

    private void splitViews(int seat, int hand, int newHand) {
        List<List<Card>> cards = handCards.get(seat);
        List<List<CardView>> views = handViews.get(seat);
        while (cards.size() <= newHand) {
            cards.add(new ArrayList<>());
            views.add(new ArrayList<>());
        }
        cards.get(newHand).add(cards.get(hand).remove(1));
        views.get(newHand).add(views.get(hand).remove(1));
        int hands = cards.size();
        for (int h = 0; h < hands; h++) {
            List<CardView> list = views.get(h);
            for (int i = 0; i < list.size(); i++) {
                list.get(i).moveTo(frame.at(LAYOUT.playerCard(seat, h, hands, i)));
            }
        }
        List<ChipView> bets = betViews.get(seat);
        long amount = bets.getFirst().amount();
        bets.add(new ChipView());
        for (int h = 0; h < bets.size(); h++) {
            bets.get(h).showPlain(chipSpot(seat, h), amount);
        }
        List<TextDisplay> labels = handLabels.get(seat);
        labels.add(label(LAYOUT.handLabel(seat, newHand, hands), HAND_LABEL_SCALE));
        for (int h = 0; h < labels.size(); h++) {
            labels.get(h).teleport(frame.at(LAYOUT.handLabel(seat, h, hands)));
            updateHandLabel(seat, h, null);
        }
    }

    private Location chipSpot(int seat, int hand) {
        int hands = handCards.get(seat).size();
        double[] spot = LAYOUT.chips(seat);
        double[] card = LAYOUT.playerCard(seat, hand, hands, 0);
        return frame.at(card[0], spot[1], spot[2]);
    }

    /** 払い出したチップを置く位置（賭けたチップの横）。 */
    private Location payoutSpot(int seat, int hand) {
        int hands = handCards.get(seat).size();
        double[] spot = LAYOUT.chips(seat);
        double[] card = LAYOUT.playerCard(seat, hand, hands, 0);
        return frame.at(card[0] - 0.17, spot[1], spot[2] + 0.1);
    }

    private void settle(BlackjackEvent.Settled e) {
        Seat seat = seats[e.seat()];
        if (seat != null) {
            seat.atRisk -= e.bet();
            seat.chips += e.payout();
        }
        if (observer != null) {
            observer.payout(e.seat(), e.bet(), e.payout());
        }
        ChipView bet = betViews.get(e.seat()).get(e.hand());
        String result = switch (e.outcome()) {
            case BLACKJACK -> "ブラックジャック！";
            case WIN -> "勝ち";
            case PUSH -> "引き分け";
            case LOSE -> "負け";
            case BUST -> "バースト";
        };
        NamedTextColor color = switch (e.outcome()) {
            case BLACKJACK, WIN -> NamedTextColor.GREEN;
            case PUSH -> NamedTextColor.YELLOW;
            case LOSE, BUST -> NamedTextColor.RED;
        };
        // 合計の行に「ブラックジャック」と出ているので、結果の行は短く
        long gain = e.payout() - e.bet();
        String change = gain > 0 ? " +" + gain + " 枚" : gain < 0 ? " " + gain + " 枚" : " ±0";
        updateHandLabel(e.seat(), e.hand(), Component.text((e.outcome() == Outcome.BLACKJACK ? "3:2 で勝ち" : result) + change, color));
        switch (e.outcome()) {
            case LOSE, BUST -> bet.slideTo(frame.at(LAYOUT.dealerChips()));
            case WIN, BLACKJACK -> {
                ChipView win = new ChipView();
                win.showPlain(frame.at(LAYOUT.dealerChips()), e.payout() - e.bet());
                payoutViews.add(win);
                Location spot = payoutSpot(e.seat(), e.hand());
                later(2, () -> win.slideTo(spot));
                playSound(e.outcome() == Outcome.BLACKJACK ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
                if (e.outcome() == Outcome.BLACKJACK) {
                    Location at = frame.at(LAYOUT.handLabel(e.seat(), e.hand(), handCards.get(e.seat()).size()));
                    at.getWorld().spawnParticle(Particle.FIREWORK, at, 30, 0.2, 0.2, 0.2, 0.05);
                }
            }
            case PUSH -> {
            }
        }
        if (seat != null) {
            unlock(seat, e);
            long net = e.payout() - e.bet();
            String sign = net > 0 ? "+" + net : String.valueOf(net);
            send(seat, Component.text(result + "（" + sign + " 枚、手元 " + seat.chips + " 枚）", color));
        }
    }

    private void unlock(Seat seat, BlackjackEvent.Settled e) {
        achieve(seat, Achievements.Achievement.FIRST_PLAY);
        List<Card> cards = handCards.get(e.seat()).get(e.hand());
        boolean won = e.outcome() == Outcome.WIN || e.outcome() == Outcome.BLACKJACK;
        if (e.outcome() == Outcome.BLACKJACK) {
            achieve(seat, Achievements.Achievement.BLACKJACK);
        }
        if (cards.contains(null) || Hand.total(cards) != 21) {
            return;
        }
        if (cards.size() == 3 && cards.stream().allMatch(card -> card.rank() == Rank.SEVEN)) {
            achieve(seat, Achievements.Achievement.TRIPLE_SEVEN_21);
        }
        if (cards.size() >= 5 && won) {
            achieve(seat, Achievements.Achievement.FIVE_CARD_21);
        }
    }

    private void finishRound() {
        clearRoundViews();
        newRound();
        state = State.IDLE;
        java.util.Arrays.fill(confirmed, false);
        settleAwaySeats();
        if (observer != null) {
            observer.roundFinished();
        }
        for (int i = 0; i < seats.length; i++) {
            Seat seat = seats[i];
            if (seat == null) {
                continue;
            }
            Player player = online(seat);
            if (seat.bot) {
                int index = i;
                later(10 + i * 3L, () -> botBet(index));
            } else if (player != null) {
                plugin.menus().openBet(player, this);
            }
        }
        updateStatus();
    }

    private void clearRoundViews() {
        for (int i = 0; i < BlackjackLayout.SEATS; i++) {
            handViews.get(i).forEach(list -> list.forEach(CardView::remove));
            handViews.get(i).clear();
            handCards.get(i).clear();
            betViews.get(i).forEach(ChipView::remove);
            betViews.get(i).clear();
            handLabels.get(i).forEach(Entity::remove);
            handLabels.get(i).clear();
        }
        payoutViews.forEach(ChipView::remove);
        payoutViews.clear();
        dealerViews.forEach(CardView::remove);
        dealerViews.clear();
        dealerCards.clear();
        if (dealerLabel != null) {
            dealerLabel.remove();
            dealerLabel = null;
        }
    }

    // ---- 表示の更新 ----

    private void updateHandLabel(int seat, int hand, Component result) {
        List<TextDisplay> labels = handLabels.get(seat);
        if (hand >= labels.size()) {
            return;
        }
        List<Card> cards = handCards.get(seat).get(hand);
        Component text = totalText(cards, handCards.get(seat).size() == 1);
        // 合計の下に、その手に賭けている額（決着したら勝ち負けと増減）
        Component second = result;
        if (second == null && hand < betViews.get(seat).size()) {
            second = Component.text("賭け " + betViews.get(seat).get(hand).amount() + " 枚", NamedTextColor.GOLD);
        }
        if (second != null) {
            text = cards.isEmpty() ? second : text.append(Component.newline()).append(second);
        }
        labels.get(hand).text(text);
    }

    private void updateDealerLabel() {
        if (dealerLabel != null) {
            dealerLabel.text(dealerCards.isEmpty() ? Component.empty() : totalText(dealerCards, dealerViews.size() == dealerCards.size()));
        }
    }

    /** 合計の表示。最初の 2 枚で 21 ならブラックジャック、A を 11 で数えているときはソフトと書く。 */
    private static Component totalText(List<Card> cards, boolean canBeBlackjack) {
        if (cards.isEmpty()) {
            return Component.empty();
        }
        int total = Hand.total(cards);
        if (canBeBlackjack && cards.size() == 2 && total == 21) {
            return Component.text("ブラックジャック", NamedTextColor.GOLD);
        }
        if (total > 21) {
            return Component.text(total + " バースト", NamedTextColor.RED);
        }
        return Component.text((Hand.soft(cards) && total < 21 ? "ソフト " : "") + total, NamedTextColor.WHITE);
    }

    @Override
    protected Component statusText() {
        Component text = switch (state) {
            case IDLE -> empty()
                    ? Component.text("ブラックジャック", NamedTextColor.GOLD).append(Component.newline())
                    .append(Component.text("椅子をクリックして座る", NamedTextColor.GRAY))
                    : Component.text("ベットを待っています", NamedTextColor.AQUA);
            case BETTING -> Component.text("ベット受付中（あと " + Math.max(countdown, 0) + " 秒）", NamedTextColor.AQUA);
            case PLAYING -> {
                int index = round.currentSeat();
                Seat seat = index >= 0 && waitingInput ? seats[index] : null;
                yield seat == null ? Component.text("勝負中", NamedTextColor.YELLOW)
                        : Component.text(seat.name + " の番", NamedTextColor.YELLOW);
            }
        };
        return text.append(limitsLine("賭け金 " + settings.rules().minBet() + "〜" + settings.rules().maxBet()));
    }

    @Override
    void stateLines(List<String> out) {
        handViews.forEach(seat -> seat.forEach(hand -> hand.forEach(card -> out.add(card.stateLine()))));
        dealerViews.forEach(card -> out.add(card.stateLine()));
    }
}
