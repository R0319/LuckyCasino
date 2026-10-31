package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.baccarat.Baccarat;
import io.github.r0319.luckycasino.bridge.CardCode;
import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Shoe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * バカラ（プント・バンコ）。プレイヤーとバンカーのどちらが 9 に近いかに賭ける。カードはディーラーが決まった手順で配り、
 * 最初の 4 枚は伏せて配ってから、プレイヤー側・バンカー側の順にめくる。
 */
final class BaccaratTable extends SpotTable {

    private static final BaccaratLayout LAYOUT = new BaccaratLayout();
    private static final int DEAL = 8;
    private static final int REVEAL = 8;

    private static final List<Spot> SPOTS = List.of(
            new Spot("player_pair", "プレイヤーペア", "プレイヤーの最初の 2 枚が同じ数。配当 11:1（12 倍が戻る）", Material.LIGHT_BLUE_DYE, 11),
            new Spot("player", "プレイヤー", "プレイヤーが勝つ。配当 1:1（2 倍が戻る）。タイなら賭け金が戻る", Material.BLUE_CONCRETE, 20),
            new Spot("tie", "タイ（引き分け）", "同じ点で引き分け。配当 8:1（9 倍が戻る）", Material.LIME_CONCRETE, 22),
            new Spot("banker", "バンカー", "バンカーが勝つ。配当 1:1 から 5%% の手数料（端数切り捨て）。タイなら賭け金が戻る", Material.RED_CONCRETE, 24),
            new Spot("banker_pair", "バンカーペア", "バンカーの最初の 2 枚が同じ数。配当 11:1（12 倍が戻る）", Material.PINK_DYE, 15));

    private final Shoe shoe;
    private Baccarat hand;
    private final List<CardView> cards = new ArrayList<>();
    private TextDisplay playerLabel;
    private TextDisplay bankerLabel;
    private TextDisplay resultLabel;

    BaccaratTable(LuckyCasinoPaper plugin, String id, Frame frame, UUID owner, Bank bank, SpotSettings settings) {
        super(plugin, id, GameType.BACCARAT, frame, owner, bank, settings);
        this.shoe = Shoe.everyRound(8, random);
    }

    @Override
    TableShape shape() {
        return LAYOUT;
    }

    @Override
    List<Spot> spots() {
        return SPOTS;
    }

    private static Baccarat.Bet bet(String spot) {
        return Baccarat.Bet.valueOf(spot.toUpperCase(Locale.ROOT));
    }

    @Override
    protected double[] spotCenter(Spot spot) {
        return LAYOUT.box(bet(spot.id()), 2);
    }

    /** 席ごとに自分の枠がある。 */
    @Override
    protected double[] chipSpot(Spot spot, int seat) {
        return LAYOUT.box(bet(spot.id()), seat);
    }

    @Override
    protected double[] croupierChips() {
        return LAYOUT.croupierChips();
    }

    @Override
    protected void onSpawn() {
    }

    @Override
    protected List<String> botSpots(ThreadLocalRandom r) {
        List<String> out = new ArrayList<>();
        out.add(r.nextBoolean() ? "player" : "banker");
        if (r.nextInt(4) == 0) {
            out.add(SPOTS.get(r.nextInt(SPOTS.size())).id());
        }
        return out;
    }

    // ---- 勝負 ----

    @Override
    protected int play() {
        shoe.shuffleIfNeeded();
        hand = Baccarat.deal(shoe);
        playerLabel = label(LAYOUT.totalLabel(Baccarat.Side.PLAYER), 0.5f);
        bankerLabel = label(LAYOUT.totalLabel(Baccarat.Side.BANKER), 0.5f);
        resultLabel = label(new double[]{-0.2, TableShape.TOP + 0.85, BaccaratLayout.CARD_Z}, 0.9f);
        List<Baccarat.Deal> deals = hand.deals();
        // 最初の 4 枚は伏せて配る
        for (int i = 0; i < 4; i++) {
            int index = i;
            later(DEAL * i, () -> dealCard(index, deals.get(index), false));
        }
        int t = DEAL * 4 + 8;
        t = revealPair(Baccarat.Side.PLAYER, 0, 2, t);
        t = revealPair(Baccarat.Side.BANKER, 1, 3, t);
        if (hand.natural()) {
            later(t, () -> {
                broadcast(L10n.tr("ナチュラル！").color(NamedTextColor.GOLD));
                playSound(Sound.BLOCK_NOTE_BLOCK_CHIME, 1.4f);
            });
            t += 12;
        }
        // 3 枚目は伏せて配り、少し間を置いてめくる
        for (int i = 4; i < deals.size(); i++) {
            int index = i;
            Baccarat.Deal deal = deals.get(i);
            later(t, () -> {
                broadcast(L10n.tr(deal.side() == Baccarat.Side.PLAYER ? "プレイヤーが 3 枚目を引きます" : "バンカーが 3 枚目を引きます").color(NamedTextColor.GRAY));
                dealCard(index, deal, false);
            });
            later(t + 16, () -> {
                cards.get(index).reveal(deal.card().code());
                updateTotals(index + 1);
            });
            t += 28;
        }
        later(t, this::showWinner);
        return t + 10;
    }

    /** a 枚目と b 枚目（同じ側の 2 枚）を順にめくり、合計を出す。 */
    private int revealPair(Baccarat.Side side, int a, int b, int t) {
        List<Baccarat.Deal> deals = hand.deals();
        later(t, () -> cards.get(a).reveal(deals.get(a).card().code()));
        later(t + REVEAL, () -> {
            cards.get(b).reveal(deals.get(b).card().code());
            updateTotals(Math.max(a, b) + 1);
        });
        return t + REVEAL + 18;
    }

    private void dealCard(int index, Baccarat.Deal deal, boolean faceUp) {
        Baccarat.Side side = deal.side();
        int n = 0;
        for (int i = 0; i < index; i++) {
            if (hand.deals().get(i).side() == side) {
                n++;
            }
        }
        double[] at = LAYOUT.card(side, n);
        cards.add(CardView.deal(plugin, plugin.bridge(), frame.at(at), Frame.delta(LAYOUT.shoe(), at),
                faceUp ? deal.card().code() : CardCode.HIDDEN, faceUp));
        swing();
    }

    /** めくり終えた shown 枚までで、両側の合計を出す（まだ 2 枚そろっていない側は出さない）。 */
    private void updateTotals(int shown) {
        List<Card> player = new ArrayList<>();
        List<Card> banker = new ArrayList<>();
        List<Baccarat.Deal> deals = hand.deals();
        for (int i = 0; i < shown; i++) {
            (deals.get(i).side() == Baccarat.Side.PLAYER ? player : banker).add(deals.get(i).card());
        }
        // 最初の 4 枚は P, B, P, B の順。プレイヤーの 2 枚は 3 枚目（index 2）、バンカーは 4 枚目（index 3）でそろう
        if (playerLabel != null && shown >= 3) {
            playerLabel.text(totalText("プレイヤー", Baccarat.total(player), NamedTextColor.AQUA));
        }
        if (bankerLabel != null && shown >= 4) {
            bankerLabel.text(totalText("バンカー", Baccarat.total(banker), NamedTextColor.RED));
        }
        playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 1.2f, 0.7f);
    }

    private static Component totalText(String name, int total, NamedTextColor color) {
        return L10n.tr(name).color(color).append(Component.newline()).append(Component.text(total, NamedTextColor.WHITE));
    }

    private void showWinner() {
        if (resultLabel == null) {
            return;
        }
        resultLabel.text(resultText());
        Location at = frame.at(-0.2, TableShape.TOP + 0.85, BaccaratLayout.CARD_Z);
        at.getWorld().spawnParticle(Particle.END_ROD, at, 15, 0.3, 0.15, 0.1, 0.02);
        playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f);
    }

    @Override
    protected Component resultText() {
        int p = hand.playerTotal();
        int b = hand.bankerTotal();
        Component text = switch (hand.winner()) {
            case PLAYER -> L10n.tr("プレイヤーの勝ち").color(NamedTextColor.AQUA);
            case BANKER -> L10n.tr("バンカーの勝ち").color(NamedTextColor.RED);
            case TIE -> L10n.tr("タイ").color(NamedTextColor.GREEN);
        };
        text = text.append(L10n.tr("（%s 対 %s）", p, b).color(NamedTextColor.WHITE));
        if (hand.playerPair() || hand.bankerPair()) {
            String pairs = hand.playerPair() && hand.bankerPair() ? "両ペア" : hand.playerPair() ? "プレイヤーペア" : "バンカーペア";
            text = text.append(Component.space()).append(L10n.tr(pairs).color(NamedTextColor.GOLD));
        }
        return text;
    }

    @Override
    protected long payout(String spot, long amount) {
        return hand.payout(bet(spot), amount);
    }

    @Override
    protected void settled(Seat seat, String spot, long amount, long payout) {
        if (payout > amount && (spot.equals("tie") || spot.endsWith("_pair"))) {
            achieve(seat, spot.equals("tie") ? Achievements.Achievement.BACCARAT_TIE : Achievements.Achievement.BACCARAT_PAIR);
        }
    }

    @Override
    protected void clearRound() {
        cards.forEach(CardView::remove);
        cards.clear();
        for (TextDisplay label : new TextDisplay[]{playerLabel, bankerLabel, resultLabel}) {
            if (label != null) {
                label.remove();
            }
        }
        playerLabel = null;
        bankerLabel = null;
        resultLabel = null;
        hand = null;
    }

    @Override
    void stateLines(List<String> out) {
        cards.forEach(card -> out.add(card.stateLine()));
    }
}
