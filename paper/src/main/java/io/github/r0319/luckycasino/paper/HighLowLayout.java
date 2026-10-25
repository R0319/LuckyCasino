package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.highlow.HighLow;

/**
 * ハイ＆ローのテーブル上の位置。賭けの枠はフェルトの絵（tools/gen_games.py の highlow_felt）と数値を合わせること。
 * 奥の中央に基準のカード（左）と次のカード（右）、その奥に出たカードの履歴。手前に HIGH・SAME・LOW の枠。
 */
final class HighLowLayout implements TableShape {

    static final int SEATS = 5;
    static final double CARD_Z = -0.4;
    static final double BASE_X = -0.3;
    static final double NEXT_X = 0.3;
    static final double BOX_Z = 0.45;
    static final double HISTORY_Z = -0.8;
    static final int HISTORY = 7;

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
        return new double[]{1.6 - 0.8 * index, 0, 1.55};
    }

    @Override
    public double[] dealer() {
        return new double[]{0, 0, -1.55};
    }

    @Override
    public String model() {
        return "table/highlow";
    }

    double[] shoe() {
        return new double[]{1.5, TOP + 0.05, -0.75};
    }

    double[] base() {
        return new double[]{BASE_X, TOP + 0.004, CARD_Z};
    }

    double[] next() {
        return new double[]{NEXT_X, TOP + 0.004, CARD_Z};
    }

    /** 履歴の i 枚目（0 がいちばん新しい。右から左へ並ぶ）。 */
    double[] history(int i) {
        return new double[]{-0.42 - 0.2 * i, TOP + 0.003, HISTORY_Z};
    }

    double[] cardLabel(boolean next) {
        return new double[]{next ? NEXT_X : BASE_X, TOP + 0.3, CARD_Z};
    }

    double[] box(HighLow.Bet bet) {
        double x = switch (bet) {
            case HIGH -> -1.15;
            case SAME -> 0;
            case LOW -> 1.15;
        };
        return new double[]{x, TOP + 0.001, BOX_Z};
    }

    /** 枠の倍率を出す位置（枠の奥の縁の上）。 */
    double[] oddsLabel(HighLow.Bet bet) {
        double[] b = box(bet);
        return new double[]{b[0], TOP + 0.08, BOX_Z - 0.2};
    }

    double[] croupierChips() {
        return new double[]{1.2, TOP + 0.001, -0.3};
    }
}
