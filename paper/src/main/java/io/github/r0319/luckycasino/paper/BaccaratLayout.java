package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.baccarat.Baccarat;

/**
 * バカラのテーブル上の位置。席ごとの賭けの枠はフェルトの絵（tools/gen_games.py の baccarat_felt）と数値を合わせること。
 * 手前から PLAYER・BANKER の枠、その奥に TIE と両側のペアの丸。カードは奥の中央で、左がプレイヤー、右がバンカー。
 */
final class BaccaratLayout implements TableShape {

    static final int SEATS = 5;
    static final double PLAYER_Z = 0.62;
    static final double BANKER_Z = 0.30;
    static final double TIE_Z = 0.03;
    static final double PAIR_DX = 0.25;
    static final double CARD_Z = -0.48;
    static final double PLAYER_CARD_X = -0.75;
    static final double BANKER_CARD_X = 0.35;
    static final double CARD_STEP = 0.2;

    static double seatX(int seat) {
        return 1.6 - 0.8 * seat;
    }

    @Override
    public double seatRise() {
        return RAISED_SEAT;
    }

    @Override
    public int seats() {
        return SEATS;
    }

    @Override
    public double[] seat(int index) {
        return new double[]{seatX(index), 0, 1.55};
    }

    @Override
    public double[] dealer() {
        return new double[]{0, 0, -1.55};
    }

    @Override
    public String model() {
        return "table/baccarat";
    }

    double[] shoe() {
        return new double[]{1.5, TOP + 0.05, -0.75};
    }

    /** 席 seat の賭け場所の中心。 */
    double[] box(Baccarat.Bet bet, int seat) {
        double x = seatX(seat);
        return switch (bet) {
            case PLAYER -> new double[]{x, TOP + 0.001, PLAYER_Z};
            case BANKER -> new double[]{x, TOP + 0.001, BANKER_Z};
            case TIE -> new double[]{x, TOP + 0.001, TIE_Z};
            case PLAYER_PAIR -> new double[]{x - PAIR_DX, TOP + 0.001, TIE_Z};
            case BANKER_PAIR -> new double[]{x + PAIR_DX, TOP + 0.001, TIE_Z};
        };
    }

    /** side の index 枚目のカード。 */
    double[] card(Baccarat.Side side, int index) {
        double x = (side == Baccarat.Side.PLAYER ? PLAYER_CARD_X : BANKER_CARD_X) + CARD_STEP * index;
        return new double[]{x, TOP + 0.004 + 0.003 * index, CARD_Z};
    }

    /** 合計を出す位置（カードの上）。 */
    double[] totalLabel(Baccarat.Side side) {
        double[] c = card(side, 1);
        return new double[]{c[0] - CARD_STEP / 2, TOP + 0.45, CARD_Z};
    }

    double[] croupierChips() {
        return new double[]{-1.35, TOP + 0.001, -0.6};
    }
}
