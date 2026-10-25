package io.github.r0319.luckycasino.paper;

/** テーブルの種類ごとの、席・ディーラー・モデルの位置（Frame の局所座標）。 */
interface TableShape {

    /** 4 x 2 ブロックのテーブルが占めるブロック（中心の局所座標）。 */
    double[][] TABLE_CELLS = tableCells();

    private static double[][] tableCells() {
        double[][] cells = new double[8][];
        int i = 0;
        for (int bx = -2; bx < 2; bx++) {
            for (int bz = -1; bz < 1; bz++) {
                cells[i++] = new double[]{bx + 0.5, 0.5, bz + 0.5};
            }
        }
        return cells;
    }

    /** フェルトの高さ（床から）。どのテーブルも同じ。 */
    double TOP = 0.95;
    /** テーブルのモデルの表示倍率（モデル 48 単位 = 4 ブロック）。 */
    float MODEL_SCALE = 4f / 3f;

    int seats();

    /** 席の椅子の位置（床）。 */
    double[] seat(int index);

    /** ディーラーが立つ位置（床）。ディーラーがいなければ null。 */
    double[] dealer();

    /** 状況を表示する文字の位置。 */
    default double[] status() {
        double[] dealer = dealer();
        return new double[]{dealer[0], 2.45, dealer[2]};
    }

    /** リソースパックのテーブルモデル（luckycasino:item/...）。 */
    String model();

    /** テーブルのモデルを出す位置・倍率・向き（台の向きからの角度）。 */
    default double[] modelAt() {
        return new double[]{0, 0, 0};
    }

    default float modelScale() {
        return MODEL_SCALE;
    }

    default float modelYaw() {
        return 0f;
    }

    /** 占めるブロックの中心（局所座標）。当たり判定のバリアを置き、置く前に空いているか確かめる。 */
    default double[][] cells() {
        return TABLE_CELLS;
    }

    /** cells の 1 つ上のブロックも空いている必要があるか（テーブルの上に頭が入る高さ）。 */
    default boolean needsHeadroom() {
        return true;
    }

    /** 置くときの説明（空きが足りないとき）。 */
    default String spaceText() {
        return "4 x 2 ブロック、高さ 2 ブロックの空きが必要です";
    }
}
