package io.github.r0319.luckycasino.paper;

/**
 * 競馬のコース。奥に長さ 9.6 ブロックの直線ダート（6 レーン、左のゲートから右のゴールへ走る）と、
 * その奥にオッズ板。手前に 2 段の観客席（各段 3 席）と、席ごとのベット台、右端に払い戻し所。
 * コース・観客席・ゲートの扉はリソースパックのモデル（tools/gen_games.py の horserace_course_model など）で、
 * 位置の数値はそちらと合わせること。
 */
final class HorseRaceLayout implements TableShape {

    static final int SEATS = 6;
    static final double START_X = -4.1;
    static final double FINISH_X = 4.2;
    /** ゲートの扉の位置（コース側の面）。 */
    static final double GATE_X = -3.8;
    /** 馬のモデルの表示倍率。 */
    static final float HORSE_SCALE = 0.6f;
    /** ダートの表面の高さ。 */
    static final double DIRT = 0.06;
    /** コース・観客席のモデルの表示倍率（モデル 16 / 3.5 単位 = 1 ブロック）。 */
    static final float COURSE_SCALE = 3.5f;
    /** 観客席の上の段の床の高さ。 */
    static final double UPPER = 0.7;
    private static final double[] SEAT_X = {-2.4, 0, 2.4};
    private static final double LOWER_Z = 0.9;
    private static final double UPPER_Z = 1.8;
    /** 当たり判定のバリアはコース（10 x 2 ブロック）だけ。 */
    private static final double[][] CELLS = courseCells();

    private static double[][] courseCells() {
        double[][] cells = new double[20][];
        int i = 0;
        for (int bx = -5; bx < 5; bx++) {
            for (int bz = -2; bz < 0; bz++) {
                cells[i++] = new double[]{bx + 0.5, 0.5, bz + 0.5};
            }
        }
        return cells;
    }

    @Override
    public double seatRise() {
        return RAISED_SEAT;
    }

    @Override
    public int seats() {
        return SEATS;
    }

    /** 席 0〜2 が下の段（右から）、3〜5 が上の段。 */
    @Override
    public double[] seat(int index) {
        boolean upper = index >= 3;
        return new double[]{SEAT_X[2 - index % 3], upper ? UPPER : 0, upper ? UPPER_Z : LOWER_Z};
    }

    @Override
    public double[] dealer() {
        return new double[]{4.4, 0, 1.0};
    }

    @Override
    public double[] status() {
        return new double[]{-4.3, 1.9, 0.7};
    }

    @Override
    public String model() {
        return "horserace/course";
    }

    @Override
    public double[] modelAt() {
        return new double[]{0, 1, 0};
    }

    @Override
    public float modelScale() {
        return COURSE_SCALE;
    }

    @Override
    public double[][] cells() {
        return CELLS;
    }

    @Override
    public int headroom() {
        return 2;
    }

    @Override
    public String spaceText() {
        return "コース（10 x 2 ブロック）に高さ 3 ブロックの空きと、手前に観客席（10 x 3 ブロック）の場所が必要です";
    }

    static double laneZ(int horse) {
        return -1.75 + 0.3 * horse;
    }

    /** 馬 horse（0 から）のスタート位置（モデルの中心。足がダートに乗る）。 */
    double[] start(int horse) {
        return new double[]{START_X, DIRT + HORSE_SCALE / 2, laneZ(horse)};
    }

    /** レーン horse のゲートの扉の蝶番（手前側の端）。 */
    double[] gate(int horse) {
        return new double[]{GATE_X, DIRT, laneZ(horse) + 0.14};
    }

    /** 席 seat のベット台の天板の中心。 */
    double[] stand(int seat) {
        double[] s = seat(seat);
        return new double[]{s[0], s[1] + 0.951, s[2] - 0.6};
    }

    /** ベット台の上の、馬 horse のチップの位置（3 x 2）。 */
    double[] chip(int seat, int horse) {
        double[] s = stand(seat);
        return new double[]{s[0] + (horse % 3 - 1) * 0.13, s[1] + seat * 0.0005, s[2] - 0.06 + (horse / 3) * 0.12};
    }

    /** オッズ板の文字（6 頭の倍率）。 */
    double[] odds() {
        return new double[]{-0.7, 0.65, -1.9};
    }

    /** オッズ板の右の、先頭や着順の文字。 */
    double[] board() {
        return new double[]{1.05, 1.35, -1.9};
    }

    /** 払い戻し所のカウンター。 */
    double[] croupierChips() {
        return new double[]{4.4, 0.951, 0.4};
    }
}
