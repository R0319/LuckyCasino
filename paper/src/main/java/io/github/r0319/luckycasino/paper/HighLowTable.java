package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.highlow.HighLow;
import io.github.r0319.luckycasino.bridge.CardCode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ハイ＆ロー。表向きの基準のカードより、次に引くカードが上か下か同じかに賭ける。配当は基準のカードで変わる。
 * めくったカードは次の回の基準になり、それまでの基準は奥の履歴に並ぶ。
 */
final class HighLowTable extends SpotTable {

    private static final HighLowLayout LAYOUT = new HighLowLayout();
    private static final int REVEAL_AT = 30;

    private Card base;
    private Card next;
    private CardView baseView;
    private CardView nextView;
    private final LinkedList<CardView> history = new LinkedList<>();
    private TextDisplay baseLabel;
    private TextDisplay nextLabel;
    private final Map<HighLow.Bet, TextDisplay> oddsLabels = new EnumMap<>(HighLow.Bet.class);

    HighLowTable(LuckyCasinoPaper plugin, String id, Frame frame, UUID owner, Bank bank, SpotSettings settings) {
        super(plugin, id, GameType.HIGHLOW, frame, owner, bank, settings);
    }

    @Override
    TableShape shape() {
        return LAYOUT;
    }

    private static HighLow.Bet bet(String spot) {
        return HighLow.Bet.valueOf(spot.toUpperCase(Locale.ROOT));
    }

    static String rankText(int value) {
        return switch (value) {
            case 1 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> String.valueOf(value);
        };
    }

    /** 今の基準で当たりようのある賭けだけを出す（基準が A なら LOW、K なら HIGH はない）。 */
    @Override
    List<Spot> spots() {
        List<Spot> out = new ArrayList<>();
        int b = base == null ? 7 : HighLow.value(base);
        String bt = rankText(b);
        if (b < 13) {
            out.add(new Spot("high", "ハイ（" + bt + " より上）", rankText(b + 1) + "〜K が出たら当たり。" + odds(HighLow.Bet.HIGH, b) + "が戻る。同じ数は負け",
                    Material.LIME_CONCRETE, 20));
        }
        out.add(new Spot("same", "同じ（" + bt + "）", bt + " が出たら当たり（マークは問わない）。" + odds(HighLow.Bet.SAME, b) + "が戻る",
                Material.GOLD_BLOCK, 22));
        if (b > 1) {
            out.add(new Spot("low", "ロー（" + bt + " より下）", "A〜" + rankText(b - 1) + " が出たら当たり。" + odds(HighLow.Bet.LOW, b) + "が戻る。同じ数は負け",
                    Material.LIGHT_BLUE_CONCRETE, 24));
        }
        return out;
    }

    private static String odds(HighLow.Bet bet, int base) {
        return HighLow.percentText(HighLow.percent(bet, base));
    }

    @Override
    protected double[] spotCenter(Spot spot) {
        return LAYOUT.box(bet(spot.id()));
    }

    @Override
    protected double[] croupierChips() {
        return LAYOUT.croupierChips();
    }

    @Override
    protected List<String> botSpots(ThreadLocalRandom r) {
        List<Spot> all = spots();
        // 当たりやすいほうを多めに
        int b = base == null ? 7 : HighLow.value(base);
        String likely = b <= 7 ? "high" : "low";
        return List.of(r.nextInt(4) > 0 && spot(likely) != null ? likely : all.get(r.nextInt(all.size())).id());
    }

    @Override
    protected void onSpawn() {
        base = HighLow.draw(random);
        baseView = CardView.deal(plugin, plugin.bridge(), frame.at(LAYOUT.base()), Frame.delta(LAYOUT.shoe(), LAYOUT.base()), base.code(), true);
        baseLabel = label(LAYOUT.cardLabel(false), 0.5f);
        nextLabel = label(LAYOUT.cardLabel(true), 0.5f);
        fixtures().add(baseLabel);
        fixtures().add(nextLabel);
        for (HighLow.Bet bet : HighLow.Bet.values()) {
            TextDisplay label = label(LAYOUT.oddsLabel(bet), 0.45f);
            oddsLabels.put(bet, label);
            fixtures().add(label);
        }
        showBase();
    }

    /** 基準のカードの文字と、枠ごとの倍率を出し直す。 */
    private void showBase() {
        int b = HighLow.value(base);
        if (baseLabel != null) {
            baseLabel.text(Component.text("基準", NamedTextColor.GRAY).append(Component.newline())
                    .append(Component.text(rankText(b), NamedTextColor.WHITE)));
        }
        if (nextLabel != null) {
            nextLabel.text(Component.text("次は？", NamedTextColor.YELLOW));
        }
        oddsLabels.forEach((bet, label) -> {
            int percent = HighLow.percent(bet, b);
            label.text(percent == 0 ? Component.text("なし", NamedTextColor.DARK_GRAY)
                    : Component.text(HighLow.percentText(percent), NamedTextColor.GOLD));
        });
    }

    // ---- 勝負 ----

    @Override
    protected int play() {
        next = HighLow.draw(random);
        nextView = CardView.deal(plugin, plugin.bridge(), frame.at(LAYOUT.next()), Frame.delta(LAYOUT.shoe(), LAYOUT.next()), CardCode.HIDDEN, false);
        swing();
        later(14, () -> {
            broadcast(Component.text("めくります…", NamedTextColor.GRAY));
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f);
        });
        later(22, () -> playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f));
        Card shown = next;
        later(REVEAL_AT, () -> {
            if (nextView != null) {
                nextView.reveal(shown.code());
            }
            if (nextLabel != null) {
                nextLabel.text(Component.text("次", NamedTextColor.GRAY).append(Component.newline()).append(verdict()));
            }
            Location at = frame.at(LAYOUT.cardLabel(true));
            at.getWorld().spawnParticle(Particle.END_ROD, at, 12, 0.15, 0.1, 0.1, 0.02);
            playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f);
        });
        return REVEAL_AT + 6;
    }

    /** 「10 ハイ」のような、出た数と判定。 */
    private Component verdict() {
        int b = HighLow.value(base);
        int n = HighLow.value(next);
        Component word = n > b ? Component.text("ハイ", NamedTextColor.GREEN)
                : n < b ? Component.text("ロー", NamedTextColor.AQUA) : Component.text("同じ！", NamedTextColor.GOLD);
        return Component.text(rankText(n) + " ", NamedTextColor.WHITE).append(word);
    }

    @Override
    protected Component resultText() {
        return Component.text(rankText(HighLow.value(base)) + " → ", NamedTextColor.WHITE).append(verdict());
    }

    @Override
    protected long payout(String spot, long amount) {
        return HighLow.payout(bet(spot), HighLow.value(base), HighLow.value(next), amount);
    }

    @Override
    protected void settled(Seat seat, String spot, long amount, long payout) {
        if (payout > amount && spot.equals("same")) {
            achieve(seat, Achievements.Achievement.HIGHLOW_SAME);
        }
    }

    /** めくったカードを次の基準にし、それまでの基準を履歴へ送る。 */
    @Override
    protected void clearRound() {
        if (next == null || nextView == null) {
            return;
        }
        history.addFirst(baseView);
        while (history.size() > HighLowLayout.HISTORY) {
            history.removeLast().remove();
        }
        for (int i = 0; i < history.size(); i++) {
            history.get(i).moveTo(frame.at(LAYOUT.history(i)));
        }
        nextView.moveTo(frame.at(LAYOUT.base()));
        baseView = nextView;
        base = next;
        nextView = null;
        next = null;
        showBase();
    }

    @Override
    protected void onDespawn() {
        for (CardView view : allCards()) {
            view.remove();
        }
        history.clear();
        baseView = null;
        nextView = null;
        next = null;
        super.onDespawn();
        oddsLabels.clear();
        baseLabel = null;
        nextLabel = null;
    }

    private List<CardView> allCards() {
        List<CardView> out = new ArrayList<>(history);
        if (baseView != null) {
            out.add(baseView);
        }
        if (nextView != null) {
            out.add(nextView);
        }
        return out;
    }

    @Override
    void stateLines(List<String> out) {
        allCards().forEach(card -> out.add(card.stateLine()));
    }
}
