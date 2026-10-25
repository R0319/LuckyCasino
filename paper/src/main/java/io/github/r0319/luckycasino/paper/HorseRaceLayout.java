package io.github.r0319.luckycasino.paper;

/**
 * 競馬のテーブル上の位置。奥の半分がダートのコース（6 レーン、左から右へ走る）、手前に単勝の枠。
 * フェルトの絵（tools/gen_games.py の horserace_felt）と数値を合わせること。
 */
final class HorseRaceLayout implements TableShape {

    static final int SEATS = 5;
    static final double START_X = -1.62;
    static final double FINISH_X = 1.55;
    static final double BOX_Z = 0.5;
    /** 馬のモデルの表示倍率。 */
    static final float HORSE_SCALE = 0.32f;

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
        return "table/horserace";
    }

    static double laneZ(int horse) {
        return -0.88 + 0.14 * horse;
    }

    /** 馬 horse（0 から）のスタート位置（モデルの中心。足がフェルトに乗る）。 */
    double[] start(int horse) {
        return new double[]{START_X, TOP + HORSE_SCALE / 2, laneZ(horse)};
    }

    /** 単勝の枠 horse の中心。 */
    double[] box(int horse) {
        return new double[]{-1.6 + 0.64 * horse, TOP + 0.001, BOX_Z};
    }

    double[] oddsLabel(int horse) {
        double[] b = box(horse);
        return new double[]{b[0], TOP + 0.08, BOX_Z - 0.3};
    }

    /** 先頭や着順を出す位置（コースの上）。 */
    double[] board() {
        return new double[]{0, TOP + 0.75, -0.5};
    }

    double[] croupierChips() {
        return new double[]{1.6, TOP + 0.001, 0.05};
    }
}
