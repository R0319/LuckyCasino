package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 決まった賭け場所にチップを置き、締め切ったらディーラーが 1 回の勝負を見せて精算する卓の共通部分
 * （バカラ・ビッグホイール・シックボー）。ルーレットと同じく、賭けたあとと勝負の間は席を立って歩ける。
 * 結果は play で先に決め、演出が終わったら payout の額で精算する。
 */
abstract class SpotTable extends CasinoTable {

    enum State {
        IDLE, BETTING, PLAYING, PAYING
    }

    /**
     * 賭け場所。
     *
     * @param id   保存や表示の引き当てに使う名前
     * @param slot メニュー（54 マス）の上 4 段のどこに出すか
     */
    record Spot(String id, String name, String help, Material icon, int slot) {
    }

    /** 払い戻しのあと、次の回を受け付けるまで（tick）。 */
    private static final int PAYING_TICKS = 80;

    protected final SpotSettings settings;
    protected final Random random = new SecureRandom();
    private State state = State.IDLE;
    private int countdown;
    /** 席 → 賭け場所 → 額。 */
    private final Map<Integer, Map<String, Long>> bets = new TreeMap<>();
    private final Map<Integer, Map<String, ChipView>> chips = new HashMap<>();
    private final List<ChipView> payoutViews = new ArrayList<>();

    SpotTable(LuckyCasinoPaper plugin, String id, GameType type, Frame frame, UUID owner, Bank bank, SpotSettings settings) {
        super(plugin, id, type, frame, owner, bank);
        this.settings = settings;
    }

    // ---- ゲームごとに書くところ ----

    /** 賭け場所の一覧（メニューの並び）。 */
    abstract List<Spot> spots();

    /** 賭け場所のテーブル上の中心（局所座標）。 */
    protected abstract double[] spotCenter(Spot spot);

    /** 負けたチップを集める場所・払い出すチップが出てくる場所。 */
    protected abstract double[] croupierChips();

    /** play が返すと、結果が見えるまでの長さが決まっていないことを表す（あとで playFinished を呼ぶ）。 */
    protected static final int UNTIL_FINISHED = -1;

    /** 結果を決めて演出を始め、結果が見えるまでの tick を返す（客が振る番があるなど、長さが決まらなければ UNTIL_FINISHED）。 */
    protected abstract int play();

    /** UNTIL_FINISHED を返した play の勝負が終わった。払い戻しに進む。 */
    protected void playFinished() {
        if (state == State.PLAYING) {
            later(10, this::payoutAll);
        }
    }

    /** 賭け場所 spot に amount 賭けていたときに戻るチップ（賭け金を含む。負けは 0）。 */
    protected abstract long payout(String spot, long amount);

    /** 席 seat の賭けの払い戻し。席ごとに勝ち負けが違う卓が書き換える（賭け金より多く負けるときは負）。 */
    protected long payout(int seat, String spot, long amount) {
        return payout(spot, amount);
    }

    /** 賭けの合計が total のとき、賭けたあとも手元に残しておく必要があるチップ（賭け金より多く負けることがある卓）。 */
    protected long reserve(long total) {
        return 0;
    }

    /** 結果の短い文（タイトルとチャットに出す）。 */
    protected abstract Component resultText();

    /** 1 つの賭けを精算した（実績など）。 */
    protected void settled(Seat seat, String spot, long amount, long payout) {
    }

    /** 勝負の表示（カード・サイコロなど）を片付ける。 */
    protected abstract void clearRound();

    /** 状況の文字の一行目に出す名前。 */
    protected String title() {
        return type().displayName();
    }

    /** ボットが賭ける場所（自己テスト用）。既定は場所を 1〜2 か所選ぶ。 */
    protected List<String> botSpots(ThreadLocalRandom r) {
        List<Spot> all = spots();
        List<String> out = new ArrayList<>();
        int count = 1 + r.nextInt(2);
        for (int i = 0; i < count; i++) {
            out.add(all.get(r.nextInt(all.size())).id());
        }
        return out;
    }

    // ---- 共通 ----

    SpotSettings settings() {
        return settings;
    }

    State state() {
        return state;
    }

    Spot spot(String id) {
        for (Spot spot : spots()) {
            if (spot.id().equals(id)) {
                return spot;
            }
        }
        return null;
    }

    @Override
    List<Long> buyIns() {
        return settings.buyIns();
    }

    @Override
    long minChips() {
        return settings.minBet();
    }

    @Override
    protected double standingRange() {
        return settings.standingRange();
    }

    boolean canBet() {
        return state == State.IDLE || state == State.BETTING;
    }

    /** 席 seat の賭け（賭け場所 → 額、置いた順）。 */
    Map<String, Long> bets(int seat) {
        return bets.getOrDefault(seat, Map.of());
    }

    long total(int seat) {
        return bets(seat).values().stream().mapToLong(Long::longValue).sum();
    }

    private boolean hasBets() {
        return !bets.isEmpty();
    }

    @Override
    protected void onDespawn() {
        clearChips();
        clearRound();
        bets.clear();
        state = State.IDLE;
    }

    @Override
    protected void onSeated(int seat, Player player) {
        if (player == null) {
            if (canBet()) {
                later(10 + seat * 4L, () -> botBet(seat));
            }
        } else if (canBet()) {
            openMenu(player);
        } else {
            player.sendMessage(Component.text("今の回が終わったら賭けられます", NamedTextColor.GRAY));
        }
    }

    @Override
    void openMenu(Player player) {
        plugin.spotMenu().open(player, this);
    }

    @Override
    protected boolean prepareLeave(int index) {
        Seat seat = seats[index];
        if (canBet() && total(index) > 0) {
            refund(index);
            later(1, this::checkBets);
        }
        return seat.atRisk == 0;
    }

    /** 賭けを置く。置けなければ理由を返す（null なら成功）。 */
    String place(int index, String spotId, long amount) {
        Seat seat = seats[index];
        if (seat == null || seat.away || !canBet() || spot(spotId) == null) {
            return "今は賭けられません";
        }
        if (amount > seat.chips) {
            return "チップが足りません";
        }
        Map<String, Long> mine = bets.computeIfAbsent(index, k -> new LinkedHashMap<>());
        long here = mine.getOrDefault(spotId, 0L) + amount;
        if (amount < settings.minBet() || here > settings.maxBet() || total(index) + amount > settings.maxTotal()) {
            if (mine.isEmpty()) {
                bets.remove(index);
            }
            return "1 か所 " + settings.minBet() + "〜" + settings.maxBet() + " 枚、1 回の合計 " + settings.maxTotal() + " 枚までです";
        }
        long keep = reserve(total(index) + amount);
        if (seat.chips - amount < keep) {
            if (mine.isEmpty()) {
                bets.remove(index);
            }
            return "負けたときの支払いに備えて、賭けたあとも手元に " + keep + " 枚必要です";
        }
        mine.put(spotId, here);
        seat.chips -= amount;
        seat.atRisk += amount;
        writeLedger(seat);
        showChips(index, spotId, here);
        playSound(Sound.BLOCK_CHAIN_PLACE, 1.4f);
        if (state == State.IDLE) {
            state = State.BETTING;
            countdown = settings.bettingSeconds();
            broadcast(Component.text("ベットを受け付けています（あと " + countdown + " 秒）", NamedTextColor.AQUA));
        }
        updateStatus();
        return null;
    }

    /** 自分の賭けを全部取り消す。 */
    void clearBets(int index) {
        if (seats[index] == null || !canBet()) {
            return;
        }
        refund(index);
        checkBets();
        updateStatus();
    }

    private void refund(int index) {
        Seat seat = seats[index];
        Map<String, Long> mine = bets.remove(index);
        if (mine != null) {
            long sum = mine.values().stream().mapToLong(Long::longValue).sum();
            seat.atRisk -= sum;
            seat.chips += sum;
            writeLedger(seat);
        }
        Map<String, ChipView> views = chips.remove(index);
        if (views != null) {
            views.values().forEach(ChipView::remove);
        }
    }

    private void checkBets() {
        if (state == State.BETTING && !hasBets()) {
            state = State.IDLE;
            updateStatus();
        }
    }

    private void botBet(int index) {
        Seat seat = seats[index];
        if (seat == null || !seat.bot || !canBet()) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (String spot : botSpots(r)) {
            place(index, spot, settings.minBet() * (1 + r.nextInt(3)));
        }
    }

    // ---- 進行 ----

    @Override
    protected void tickSecond() {
        if (state != State.BETTING) {
            return;
        }
        countdown--;
        if (countdown == 3) {
            broadcast(Component.text("まもなく締め切ります", NamedTextColor.YELLOW));
        }
        if (countdown <= 0) {
            start();
        }
        updateStatus();
    }

    private void start() {
        if (!hasBets()) {
            state = State.IDLE;
            return;
        }
        state = State.PLAYING;
        plugin.spotMenu().close(this);
        broadcast(Component.text("ノーモアベット", NamedTextColor.AQUA));
        int ticks = play();
        if (ticks != UNTIL_FINISHED) {
            later(ticks + 10, this::payoutAll);
        }
        updateStatus();
    }

    private void payoutAll() {
        state = State.PAYING;
        Component result = resultText();
        for (Map.Entry<Integer, Map<String, Long>> entry : bets.entrySet()) {
            int index = entry.getKey();
            Seat seat = seats[index];
            long sumBet = 0;
            long sumBack = 0;
            for (Map.Entry<String, Long> bet : entry.getValue().entrySet()) {
                long amount = bet.getValue();
                long back = payout(index, bet.getKey(), amount);
                sumBet += amount;
                sumBack += back;
                if (seat != null) {
                    seat.atRisk -= amount;
                    seat.chips += back;
                    settled(seat, bet.getKey(), amount, back);
                }
                if (observer != null) {
                    observer.payout(index, amount, back);
                }
                animatePayout(index, bet.getKey(), amount, back);
            }
            if (seat == null) {
                continue;
            }
            writeLedger(seat);
            achieve(seat, Achievements.Achievement.FIRST_PLAY);
            long net = sumBack - sumBet;
            Player player = online(seat);
            if (player != null) {
                player.showTitle(Title.title(result, Component.text(net > 0 ? "+" + net + " 枚" : net == 0 ? "±0" : net + " 枚",
                                net > 0 ? NamedTextColor.GREEN : net == 0 ? NamedTextColor.YELLOW : NamedTextColor.RED),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2000), Duration.ofMillis(400))));
                if (net > 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                }
                player.sendMessage(Component.text("結果 ", NamedTextColor.WHITE).append(result)
                        .append(Component.text((sumBack < 0 ? "：追加の支払い " + -sumBack : "：払い戻し " + sumBack)
                                + " 枚（手元 " + seat.chips + " 枚）", NamedTextColor.GRAY)));
            }
        }
        updateStatus();
        later(PAYING_TICKS, this::finishRound);
    }

    private void animatePayout(int index, String spot, long amount, long back) {
        ChipView view = chips.getOrDefault(index, Map.of()).get(spot);
        if (view == null || back == amount) {
            return;
        }
        if (back <= 0) {
            view.slideTo(frame.at(croupierChips()));
            return;
        }
        ChipView win = new ChipView();
        win.show(frame.at(croupierChips()), back - amount, false);
        payoutViews.add(win);
        double[] at = chipSpot(spot(spot), index);
        later(3, () -> win.slideTo(frame.at(at[0] + 0.05, at[1], at[2] - 0.05)));
    }

    private void finishRound() {
        clearChips();
        clearRound();
        bets.clear();
        state = State.IDLE;
        settleAwaySeats();
        if (observer != null) {
            observer.roundFinished();
        }
        for (int i = 0; i < seats.length; i++) {
            Seat seat = seats[i];
            if (seat == null) {
                continue;
            }
            if (seat.bot) {
                int index = i;
                later(10 + i * 4L, () -> botBet(index));
            } else {
                Player player = online(seat);
                if (player != null) {
                    player.sendMessage(Component.text("次の回のベットを受け付けています", NamedTextColor.AQUA));
                    openMenu(player);
                }
            }
        }
        updateStatus();
    }

    // ---- 表示 ----

    /** 席 seat のチップを置く位置。既定は場所の中心から、席ごとに少しずらす（重ならないように）。 */
    protected double[] chipSpot(Spot spot, int seat) {
        double[] c = spotCenter(spot);
        double d = (seat - (seatCount() - 1) / 2.0) * 0.014;
        return new double[]{c[0] + d, c[1] + seat * 0.0005, c[2] + d * 0.5};
    }

    private void showChips(int index, String spot, long amount) {
        ChipView view = chips.computeIfAbsent(index, k -> new HashMap<>()).computeIfAbsent(spot, k -> new ChipView());
        view.show(frame.at(chipSpot(spot(spot), index)), amount, false);
    }

    private void clearChips() {
        chips.values().forEach(map -> map.values().forEach(ChipView::remove));
        chips.clear();
        payoutViews.forEach(ChipView::remove);
        payoutViews.clear();
    }

    @Override
    protected Component statusText() {
        Component text = switch (state) {
            case IDLE -> empty()
                    ? Component.text(title(), NamedTextColor.GOLD).append(Component.newline())
                    .append(Component.text("椅子をクリックして座る", NamedTextColor.GRAY))
                    : Component.text("ベットを待っています", NamedTextColor.AQUA);
            case BETTING -> Component.text("ベット受付中（あと " + Math.max(countdown, 0) + " 秒）", NamedTextColor.AQUA);
            case PLAYING -> Component.text("ノーモアベット", NamedTextColor.YELLOW);
            case PAYING -> Component.text("払い戻し中", NamedTextColor.YELLOW);
        };
        return text.append(limitsLine("1 か所 " + settings.minBet() + "〜" + settings.maxBet() + "、合計 " + settings.maxTotal() + " まで"));
    }
}
