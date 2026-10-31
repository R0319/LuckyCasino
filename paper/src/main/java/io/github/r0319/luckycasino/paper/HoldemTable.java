package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bridge.CardCode;
import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Shoe;
import io.github.r0319.luckycasino.holdem.CasinoHoldem;
import io.github.r0319.luckycasino.holdem.CasinoHoldem.Outcome;
import io.github.r0319.luckycasino.poker.HandEvaluator;
import io.github.r0319.luckycasino.poker.HandValue;
import io.github.r0319.luckycasino.poker.HandValue.Category;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * カジノホールデム。客は 1 人ずつディーラーと勝負するので、1 人でも遊べる。アンティを賭けて締め切ると、
 * 席ごとに手札 2 枚と共通の場の 3 枚が表で配られ、全員が同時にコール（アンティの 2 倍を足す）かフォールドを選ぶ
 * （チャットのボタンか /casino call・fold。時間切れは自動でコール）。そのあと場の残り 2 枚とディーラーの 2 枚を開ける。
 * ルールと配当は common の CasinoHoldem。コールに備えて、アンティを賭けたあとも手元にアンティの 2 倍を残す。
 */
final class HoldemTable extends SpotTable {

    private static final HoldemLayout LAYOUT = new HoldemLayout();
    private static final int DEAL = 5;
    /** コールかフォールドを選ぶ時間（秒）。 */
    private static final int DECIDE_SECONDS = 20;

    private static final Spot ANTE = new Spot("ante", "アンティ（参加料）",
            "手札 2 枚と場の 3 枚を見てから、アンティの 2 倍をコールするか降りるかを選びます。"
                    + "ディーラーより強ければアンティは配当表（ストレート以下 1 倍〜ロイヤルフラッシュ 100 倍）、コールは 1 倍。"
                    + "賭けたあとも、コールの分（アンティの 2 倍）を手元に残してください",
            Material.LIME_CONCRETE, 22);
    private static final Spot CALL = new Spot("call", "コール", "アンティの 2 倍", Material.GOLD_NUGGET, -1);
    private static final List<Spot> SPOTS = List.of(ANTE);

    private final Shoe shoe;
    private CasinoHoldem game;
    /** 席 → 配った手の番号（賭けていない席は -1）。 */
    private final int[] hand = new int[HoldemLayout.SEATS];
    /** 席ごとのコール（true）・フォールド（false）。選ぶ前は null。 */
    private final Boolean[] called = new Boolean[HoldemLayout.SEATS];
    private final List<CardView> holeCards = new ArrayList<>();
    private final CardView[] boardCards = new CardView[5];
    private final CardView[] dealerCards = new CardView[2];
    private final TextDisplay[] handLabels = new TextDisplay[HoldemLayout.SEATS];
    private TextDisplay dealerLabel;
    private boolean deciding;
    private int decideLeft;
    /** 古い時間切れの予約を見分ける番号。 */
    private int token;

    HoldemTable(LuckyCasinoPaper plugin, String id, Frame frame, UUID owner, Bank bank, SpotSettings settings) {
        super(plugin, id, GameType.HOLDEM, frame, owner, bank, settings);
        this.shoe = Shoe.everyRound(1, random);
    }

    @Override
    TableShape shape() {
        return LAYOUT;
    }

    @Override
    List<Spot> spots() {
        return SPOTS;
    }

    /** コールの賭け場所はメニューには出さないが、チップの表示と精算には使う。 */
    @Override
    Spot spot(String id) {
        return CALL.id().equals(id) ? CALL : super.spot(id);
    }

    @Override
    String place(int index, String spotId, long amount) {
        if (CALL.id().equals(spotId)) {
            return "コールは手札を見てから選びます";
        }
        return super.place(index, spotId, amount);
    }

    @Override
    protected double[] spotCenter(Spot spot) {
        return LAYOUT.ante(2);
    }

    @Override
    protected double[] chipSpot(Spot spot, int seat) {
        return spot == CALL ? LAYOUT.call(seat) : LAYOUT.ante(seat);
    }

    @Override
    protected double[] croupierChips() {
        return LAYOUT.croupierChips();
    }

    @Override
    protected long reserve(long total) {
        return total * CasinoHoldem.CALL_MULTIPLE;
    }

    @Override
    protected List<String> botSpots(ThreadLocalRandom r) {
        return List.of(ANTE.id());
    }

    @Override
    protected void onSpawn() {
    }

    // ---- 勝負 ----

    @Override
    protected int play() {
        Arrays.fill(hand, -1);
        Arrays.fill(called, null);
        List<Integer> playing = new ArrayList<>();
        for (int i = 0; i < seats.length; i++) {
            if (total(i) > 0) {
                hand[i] = playing.size();
                playing.add(i);
            }
        }
        shoe.shuffleIfNeeded();
        game = CasinoHoldem.deal(shoe, playing.size());
        int t = 0;
        for (int k = 0; k < 2; k++) {
            for (int seat : playing) {
                int kk = k;
                Card card = game.hole(hand[seat]).get(kk);
                later(t, () -> holeCards.add(deal(LAYOUT.hole(seat, kk), card, true)));
                t += DEAL;
            }
        }
        for (int i = 0; i < 5; i++) {
            int index = i;
            Card card = game.board().get(i);
            later(t, () -> boardCards[index] = deal(LAYOUT.board(index), card, index < 3));
            t += DEAL;
        }
        for (int k = 0; k < 2; k++) {
            int kk = k;
            Card card = game.dealer().get(k);
            later(t, () -> dealerCards[kk] = deal(LAYOUT.dealerCard(kk), card, false));
            t += DEAL;
        }
        later(t + 10, this::startDecision);
        return UNTIL_FINISHED;
    }

    private CardView deal(double[] at, Card card, boolean faceUp) {
        swing();
        return CardView.deal(plugin, plugin.bridge(), frame.at(at), Frame.delta(LAYOUT.deck(), at),
                faceUp ? card.code() : CardCode.HIDDEN, faceUp);
    }

    private void startDecision() {
        if (game == null) {
            return;
        }
        deciding = true;
        decideLeft = DECIDE_SECONDS;
        int current = ++token;
        for (int seat = 0; seat < seats.length; seat++) {
            if (hand[seat] < 0) {
                continue;
            }
            Seat s = seats[seat];
            if (s != null && s.bot) {
                int index = seat;
                boolean call = CasinoHoldem.suggestCall(game.hole(hand[seat]), game.flop());
                later(20 + ThreadLocalRandom.current().nextInt(30), () -> decide(index, call));
            } else {
                prompt(seat);
            }
        }
        // 手札と場の 3 枚までで、今できている役を見せる
        for (int seat = 0; seat < seats.length; seat++) {
            if (hand[seat] >= 0) {
                showHand(seat, Component.text(flopHand(seat).displayName(), NamedTextColor.WHITE));
            }
        }
        broadcast(Component.text("コールかフォールドを選んでください（" + DECIDE_SECONDS + " 秒）", NamedTextColor.AQUA));
        later(DECIDE_SECONDS * 20L, () -> {
            if (current == token && deciding) {
                for (int seat = 0; seat < seats.length; seat++) {
                    if (hand[seat] >= 0 && called[seat] == null) {
                        send(seats[seat], Component.text("時間切れなので自動でコールしました", NamedTextColor.GRAY));
                        decide(seat, true);
                    }
                }
            }
        });
        updateStatus();
    }

    private HandValue flopHand(int seat) {
        List<Card> five = new ArrayList<>(game.hole(hand[seat]));
        five.addAll(game.flop());
        return HandEvaluator.best(five);
    }

    private long ante(int seat) {
        return bets(seat).getOrDefault(ANTE.id(), 0L);
    }

    private void prompt(int seat) {
        Seat s = seats[seat];
        Player player = s == null ? null : online(s);
        if (player == null) {
            return;
        }
        long call = ante(seat) * CasinoHoldem.CALL_MULTIPLE;
        player.sendMessage(Component.text("あなたの手: " + flopHand(seat).displayName() + " ", NamedTextColor.AQUA)
                .append(Component.text("[コール +" + call + " 枚]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/casino call")))
                .append(Component.text(" "))
                .append(Component.text("[フォールド]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/casino fold")))
                .append(Component.text(" " + DECIDE_SECONDS + " 秒で自動でコールします", NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
    }

    /** /casino call・fold。選ぶ時間に、まだ選んでいない席だけ。 */
    void choose(Player player, boolean call) {
        int seat = seatOf(player.getUniqueId());
        if (seat < 0 || !deciding || hand[seat] < 0 || called[seat] != null) {
            player.sendMessage(Component.text("今はコールかフォールドを選ぶときではありません", NamedTextColor.GRAY));
            return;
        }
        decide(seat, call);
    }

    @Override
    void openMenu(Player player) {
        int seat = seatOf(player.getUniqueId());
        if (seat >= 0 && deciding && hand[seat] >= 0 && called[seat] == null) {
            prompt(seat);
            return;
        }
        super.openMenu(player);
    }

    private void decide(int seat, boolean call) {
        if (!deciding || hand[seat] < 0 || called[seat] != null) {
            return;
        }
        long amount = ante(seat) * CasinoHoldem.CALL_MULTIPLE;
        if (call && !addBet(seat, CALL.id(), amount)) {
            send(seats[seat], Component.text("チップが足りないのでフォールドしました", NamedTextColor.RED));
            call = false;
        }
        called[seat] = call;
        showHand(seat, Component.text(call ? "コール" : "フォールド", call ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        if (!call) {
            playSound(Sound.ITEM_BOOK_PAGE_TURN, 1f);
        }
        for (int i = 0; i < seats.length; i++) {
            if (hand[i] >= 0 && called[i] == null) {
                updateStatus();
                return;
            }
        }
        deciding = false;
        token++;
        updateStatus();
        later(10, this::showdown);
    }

    /** 場の残り 2 枚とディーラーの 2 枚を開けて、席ごとに勝ち負けを出す。 */
    private void showdown() {
        if (game == null) {
            return;
        }
        later(0, () -> reveal(boardCards[3], game.board().get(3)));
        later(14, () -> reveal(boardCards[4], game.board().get(4)));
        later(34, () -> reveal(dealerCards[0], game.dealer().get(0)));
        later(42, () -> {
            reveal(dealerCards[1], game.dealer().get(1));
            showDealer();
        });
        later(62, this::showResults);
        later(110, this::playFinished);
    }

    private void reveal(CardView card, Card value) {
        if (card != null) {
            card.reveal(value.code());
        }
    }

    private void showDealer() {
        dealerLabel = label(LAYOUT.dealerLabel(), 0.55f);
        dealerLabel.text(resultText());
        playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 1.2f, 0.7f);
    }

    private void showResults() {
        boolean big = false;
        for (int seat = 0; seat < seats.length; seat++) {
            if (hand[seat] < 0) {
                continue;
            }
            boolean call = Boolean.TRUE.equals(called[seat]);
            Outcome outcome = game.outcome(hand[seat], call);
            HandValue best = game.playerBest(hand[seat]);
            NamedTextColor color = switch (outcome) {
                case WIN, NOT_QUALIFIED -> NamedTextColor.GREEN;
                case TIE -> NamedTextColor.YELLOW;
                case LOSE -> NamedTextColor.RED;
                case FOLD -> NamedTextColor.GRAY;
            };
            Component text = Component.text(best.displayName(), NamedTextColor.WHITE).append(Component.newline())
                    .append(Component.text(outcome.displayName(), color));
            showHand(seat, text);
            if (call && (outcome == Outcome.WIN || outcome == Outcome.NOT_QUALIFIED) && CasinoHoldem.anteMultiplier(best) > 1) {
                big = true;
            }
        }
        if (big) {
            Location at = frame.at(LAYOUT.dealerLabel());
            at.getWorld().spawnParticle(Particle.END_ROD, at, 15, 0.4, 0.15, 0.2, 0.02);
            playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.4f);
        }
    }

    private void showHand(int seat, Component text) {
        if (handLabels[seat] == null) {
            handLabels[seat] = label(LAYOUT.handLabel(seat), 0.45f);
        }
        handLabels[seat].text(text);
    }

    @Override
    protected void tickSecond() {
        super.tickSecond();
        if (deciding && decideLeft > 0) {
            decideLeft--;
            updateStatus();
        }
    }

    @Override
    protected Component resultText() {
        if (game == null) {
            return Component.text("勝負なし", NamedTextColor.GRAY);
        }
        HandValue dealer = game.dealerBest();
        Component text = Component.text("ディーラー ", NamedTextColor.WHITE).append(Component.text(dealer.displayName(), NamedTextColor.GOLD));
        return game.dealerQualifies() ? text : text.append(Component.text("（不成立）", NamedTextColor.GREEN));
    }

    @Override
    protected long payout(String spot, long amount) {
        return amount;
    }

    @Override
    protected long payout(int seat, String spot, long amount) {
        if (game == null || hand[seat] < 0) {
            return amount;
        }
        if (CALL.id().equals(spot)) {
            return game.callPayout(hand[seat], amount);
        }
        return game.antePayout(hand[seat], Boolean.TRUE.equals(called[seat]), amount);
    }

    @Override
    protected void settled(Seat seat, String spot, long amount, long payout) {
        if (game == null || !ANTE.id().equals(spot) || payout <= amount) {
            return;
        }
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == seat && hand[i] >= 0 && game.playerBest(hand[i]).category().compareTo(Category.FULL_HOUSE) >= 0) {
                achieve(seat, Achievements.Achievement.HOLDEM_FULL_HOUSE);
            }
        }
    }

    @Override
    protected Component statusText() {
        if (state() == State.PLAYING && deciding) {
            return Component.text("コールかフォールドを選んでいます（あと " + Math.max(decideLeft, 0) + " 秒）", NamedTextColor.YELLOW);
        }
        return super.statusText();
    }

    @Override
    protected void clearRound() {
        holeCards.forEach(CardView::remove);
        holeCards.clear();
        for (CardView[] cards : new CardView[][]{boardCards, dealerCards}) {
            for (int i = 0; i < cards.length; i++) {
                if (cards[i] != null) {
                    cards[i].remove();
                    cards[i] = null;
                }
            }
        }
        for (int i = 0; i < handLabels.length; i++) {
            if (handLabels[i] != null) {
                handLabels[i].remove();
                handLabels[i] = null;
            }
        }
        if (dealerLabel != null) {
            dealerLabel.remove();
        }
        dealerLabel = null;
        deciding = false;
        token++;
        game = null;
        Arrays.fill(hand, -1);
        Arrays.fill(called, null);
    }

    @Override
    void stateLines(List<String> out) {
        holeCards.forEach(card -> out.add(card.stateLine()));
        for (CardView card : boardCards) {
            if (card != null) {
                out.add(card.stateLine());
            }
        }
        for (CardView card : dealerCards) {
            if (card != null) {
                out.add(card.stateLine());
            }
        }
    }
}
