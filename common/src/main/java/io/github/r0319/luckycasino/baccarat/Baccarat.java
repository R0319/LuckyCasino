package io.github.r0319.luckycasino.baccarat;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Rank;
import io.github.r0319.luckycasino.card.Shoe;

import java.util.ArrayList;
import java.util.List;

/**
 * バカラ（プント・バンコ）1 回分の配り方と勝ち負け。3 枚目を引くかは決まったルールどおりで、誰も選ばない。
 * カードは配る順（プレイヤー、バンカー、プレイヤー、バンカー、プレイヤーの 3 枚目、バンカーの 3 枚目）に deals へ並べる。
 */
public final class Baccarat {

    public enum Side {
        PLAYER, BANKER
    }

    public enum Winner {
        PLAYER, BANKER, TIE
    }

    /** 配った 1 枚。 */
    public record Deal(Side side, Card card) {
    }

    /** 賭けられる場所。 */
    public enum Bet {
        PLAYER, BANKER, TIE, PLAYER_PAIR, BANKER_PAIR
    }

    private final List<Card> player = new ArrayList<>();
    private final List<Card> banker = new ArrayList<>();
    private final List<Deal> deals = new ArrayList<>();

    private Baccarat() {
    }

    /** シューから 1 回分を配る。 */
    public static Baccarat deal(Shoe shoe) {
        Baccarat b = new Baccarat();
        b.give(Side.PLAYER, shoe.draw());
        b.give(Side.BANKER, shoe.draw());
        b.give(Side.PLAYER, shoe.draw());
        b.give(Side.BANKER, shoe.draw());
        int p = b.playerTotal();
        int k = b.bankerTotal();
        if (p >= 8 || k >= 8) {
            return b;
        }
        Card third = null;
        if (p <= 5) {
            third = shoe.draw();
            b.give(Side.PLAYER, third);
        }
        if (bankerDraws(k, third == null ? -1 : value(third))) {
            b.give(Side.BANKER, shoe.draw());
        }
        return b;
    }

    /**
     * バンカーが 3 枚目を引くか。
     *
     * @param third プレイヤーの 3 枚目の点（引かなかったら -1）
     */
    static boolean bankerDraws(int banker, int third) {
        if (third < 0) {
            return banker <= 5;
        }
        return switch (banker) {
            case 0, 1, 2 -> true;
            case 3 -> third != 8;
            case 4 -> third >= 2 && third <= 7;
            case 5 -> third >= 4 && third <= 7;
            case 6 -> third == 6 || third == 7;
            default -> false;
        };
    }

    private void give(Side side, Card card) {
        (side == Side.PLAYER ? player : banker).add(card);
        deals.add(new Deal(side, card));
    }

    /** カードの点（A = 1、10 と絵札 = 0）。 */
    public static int value(Card card) {
        return card.rank().points() % 10;
    }

    public static int total(List<Card> cards) {
        int sum = 0;
        for (Card card : cards) {
            sum += value(card);
        }
        return sum % 10;
    }

    public List<Card> player() {
        return List.copyOf(player);
    }

    public List<Card> banker() {
        return List.copyOf(banker);
    }

    public List<Deal> deals() {
        return List.copyOf(deals);
    }

    public int playerTotal() {
        return total(player);
    }

    public int bankerTotal() {
        return total(banker);
    }

    /** 最初の 2 枚で 8 か 9（ナチュラル）。 */
    public boolean natural() {
        return total(player.subList(0, 2)) >= 8 || total(banker.subList(0, 2)) >= 8;
    }

    public Winner winner() {
        int p = playerTotal();
        int k = bankerTotal();
        return p > k ? Winner.PLAYER : k > p ? Winner.BANKER : Winner.TIE;
    }

    public boolean playerPair() {
        return pair(player);
    }

    public boolean bankerPair() {
        return pair(banker);
    }

    private static boolean pair(List<Card> cards) {
        Rank a = cards.get(0).rank();
        return a == cards.get(1).rank();
    }

    /**
     * 戻ってくるチップ（賭け金を含む。負けは 0）。プレイヤー 1:1、バンカー 1:1 から 5% の手数料（端数は切り捨て）、
     * タイ 8:1、ペア 11:1。タイのとき、プレイヤーとバンカーへの賭けはそのまま戻る。
     */
    public long payout(Bet bet, long amount) {
        Winner w = winner();
        return switch (bet) {
            case PLAYER -> w == Winner.PLAYER ? amount * 2 : w == Winner.TIE ? amount : 0;
            case BANKER -> w == Winner.BANKER ? amount + amount * 95 / 100 : w == Winner.TIE ? amount : 0;
            case TIE -> w == Winner.TIE ? amount * 9 : 0;
            case PLAYER_PAIR -> playerPair() ? amount * 12 : 0;
            case BANKER_PAIR -> bankerPair() ? amount * 12 : 0;
        };
    }
}
