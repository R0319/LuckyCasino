package io.github.r0319.luckycasino.paper;

/**
 * スロットマシンの部品の位置。筐体は 1 x 1 ブロック・高さ 2 で、ブロックの中心が原点。
 * 部品はどれも台の向き + 180 度の ItemDisplay で出すので、モデルの座標（tools/gen_slot.py、16 単位 = 1 ブロック）が
 * そのまま局所座標になる（モデルの (8, 8, 8) が出した位置）。
 */
final class SlotLayout implements TableShape {

    /** リールの軸の高さ（モデルの y = 21）と、3 本の中心（モデルの x = 3.9, 8, 12.1）。 */
    static final double AXIS_Y = 0.5 + (21 - 8) / 16.0;
    private static final double[] REEL_X = {(3.9 - 8) / 16, 0, (12.1 - 8) / 16};

    private static final double[][] CELLS = {{0, 0.5, 0}, {0, 1.5, 0}};

    @Override
    public int seats() {
        return 1;
    }

    @Override
    public double[] seat(int index) {
        return new double[]{0, 0, 1.0};
    }

    @Override
    public double[] dealer() {
        return null;
    }

    @Override
    public double[] status() {
        return new double[]{0, 2.45, 0};
    }

    @Override
    public String model() {
        return "table/slot";
    }

    /** 筐体・ランプ・当たりの帯を出す位置（モデルの中心）。 */
    @Override
    public double[] modelAt() {
        return new double[]{0, 0.5, 0};
    }

    @Override
    public float modelScale() {
        return 1f;
    }

    @Override
    public float modelYaw() {
        return 180f;
    }

    @Override
    public double[][] cells() {
        return CELLS;
    }

    @Override
    public boolean needsHeadroom() {
        return false;
    }

    @Override
    public String spaceText() {
        return "1 x 1 ブロック、高さ 2 ブロックの空きと、手前に椅子の場所が必要です";
    }

    double[] reel(int index) {
        return new double[]{REEL_X[index], AXIS_Y, 0};
    }

    /** レバーの根元（筐体の右側面）。 */
    double[] lever() {
        return new double[]{0.56, 1.0, 0.05};
    }

    /** レバーの当たり判定の足元。 */
    double[] leverHit() {
        return new double[]{0.56, 0.85, 0.05};
    }

    /** 大当たりでチップが噴き出す受け皿。 */
    double[] tray() {
        return new double[]{0, 0.3, 0.5};
    }

    double[] resultLabel() {
        return new double[]{0, 2.1, 0.25};
    }
}
