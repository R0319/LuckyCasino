package io.github.r0319.luckycasino.roulette;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * ルーレット 1 回分。席ごとに賭けを集め、回したら結果と払い戻しを返す。
 * チップの出し入れは呼び出し側が行う（賭けるときに預かり、payouts の分を返す）。
 */
public final class RouletteRound {

    /** 1 つの賭けの結果。payout は戻ってくるチップ（賭け金を含む）。 */
    public record Payout(int seat, RouletteBet bet, long payout) {
    }

    private final long minBet;
    private final long maxBet;
    private final long maxTotal;
    private final Map<Integer, List<RouletteBet>> bets = new TreeMap<>();
    private Integer result;

    /**
     * @param minBet   1 つの賭けの最低額
     * @param maxBet   1 つの場所に置ける最高額
     * @param maxTotal 1 席が 1 回に賭けられる合計の最高額
     */
    public RouletteRound(long minBet, long maxBet, long maxTotal) {
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.maxTotal = maxTotal;
    }

    /**
     * 賭けを置く。同じ場所に置いてあれば額を足す。置けなければ IllegalArgumentException。
     *
     * @return その場所の合計の賭け
     */
    public RouletteBet place(int seat, RouletteBet bet) {
        requireOpen();
        List<RouletteBet> list = bets.computeIfAbsent(seat, k -> new ArrayList<>());
        long total = total(seat) + bet.amount();
        if (total > maxTotal) {
            throw new IllegalArgumentException("total bet exceeds " + maxTotal);
        }
        for (int i = 0; i < list.size(); i++) {
            RouletteBet existing = list.get(i);
            if (existing.samePlace(bet)) {
                long amount = existing.amount() + bet.amount();
                if (amount > maxBet) {
                    throw new IllegalArgumentException("bet exceeds " + maxBet);
                }
                RouletteBet merged = existing.withAmount(amount);
                list.set(i, merged);
                return merged;
            }
        }
        if (bet.amount() < minBet || bet.amount() > maxBet) {
            throw new IllegalArgumentException("bet out of range: " + bet.amount());
        }
        list.add(bet);
        return bet;
    }

    /** 席の賭けを全部取り消し、返す額を返す。 */
    public long clear(int seat) {
        requireOpen();
        long total = total(seat);
        bets.remove(seat);
        return total;
    }

    public long total(int seat) {
        return bets.getOrDefault(seat, List.of()).stream().mapToLong(RouletteBet::amount).sum();
    }

    public List<RouletteBet> bets(int seat) {
        return Collections.unmodifiableList(bets.getOrDefault(seat, List.of()));
    }

    public boolean hasBets() {
        return bets.values().stream().anyMatch(list -> !list.isEmpty());
    }

    public boolean spun() {
        return result != null;
    }

    /** 結果を乱数で決める。 */
    public int spin(Random random) {
        requireOpen();
        result = random.nextInt(Wheel.POCKETS);
        return result;
    }

    /** 結果を決めて回す（テスト用）。 */
    public int spinTo(int number) {
        requireOpen();
        if (number < 0 || number > 36) {
            throw new IllegalArgumentException("invalid number: " + number);
        }
        result = number;
        return result;
    }

    public int result() {
        if (result == null) {
            throw new IllegalStateException("not spun yet");
        }
        return result;
    }

    /** すべての賭けの結果（席順、置いた順）。 */
    public List<Payout> payouts() {
        int number = result();
        List<Payout> out = new ArrayList<>();
        bets.forEach((seat, list) -> list.forEach(bet -> out.add(new Payout(seat, bet, bet.payout(number)))));
        return out;
    }

    private void requireOpen() {
        if (result != null) {
            throw new IllegalStateException("already spun");
        }
    }
}
