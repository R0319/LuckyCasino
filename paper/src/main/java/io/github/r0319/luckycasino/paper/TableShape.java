package io.github.r0319.luckycasino.paper;

/** テーブルの種類ごとの、席・ディーラー・モデルの位置（Frame の局所座標）。 */
interface TableShape {

    /** フェルトの高さ（床から）。どのテーブルも同じ。 */
    double TOP = 0.95;
    /** テーブルのモデルの表示倍率（モデル 48 単位 = 4 ブロック）。 */
    float MODEL_SCALE = 4f / 3f;

    int seats();

    /** 席の椅子の位置（床）。 */
    double[] seat(int index);

    /** ディーラーが立つ位置（床）。 */
    double[] dealer();

    /** 状況を表示する文字の位置。 */
    default double[] status() {
        double[] dealer = dealer();
        return new double[]{dealer[0], 2.45, dealer[2]};
    }

    /** リソースパックのテーブルモデル（luckycasino:item/...）。 */
    String model();
}
