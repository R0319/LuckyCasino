package io.github.r0319.luckycasino.paper;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * テーブル上の位置。テーブル中心の床を原点、プレイヤー側を +Z、プレイヤーから見て右を +X とする局所座標を
 * ワールド座標に直す。モデルとフェルトの絵（tools/gen_table.py）と数値を合わせること。
 */
final class TableLayout {

    static final int SEATS = 5;
    /** フェルトの高さ（床から）。 */
    static final double TOP = 0.95;
    /** テーブルのモデルの表示倍率（モデル 48 単位 = 4 ブロック）。 */
    static final float TABLE_SCALE = 4f / 3f;
    static final float CARD_SCALE = 0.4f;

    private static final double CARD_Z = 0.25;
    private static final double CHIP_Z = 0.68;
    private static final double SEAT_Z = 1.55;
    private static final double DEALER_Z = -1.55;
    private static final double DEALER_CARD_Z = -0.55;
    private static final double SHOE_X = 1.4;
    private static final double SHOE_Z = -0.6;

    private final World world;
    private final double ox;
    private final double oy;
    private final double oz;
    /** テーブルの向き。局所 +Z（プレイヤー側）がこの yaw のエンティティの正面になる。90 度刻み。 */
    private final float yaw;
    private final double sin;
    private final double cos;

    TableLayout(World world, double ox, double oy, double oz, float yaw) {
        this.world = world;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.yaw = yaw;
        double rad = Math.toRadians(yaw);
        this.sin = Math.sin(rad);
        this.cos = Math.cos(rad);
    }

    World world() {
        return world;
    }

    float yaw() {
        return yaw;
    }

    Location origin() {
        return at(0, 0, 0);
    }

    /** 局所座標 → ワールド座標。向きはテーブルと同じ。 */
    Location at(double lx, double ly, double lz) {
        double wx = ox + lx * cos - lz * sin;
        double wz = oz + lx * sin + lz * cos;
        return new Location(world, wx, oy + ly, wz, yaw, 0f);
    }

    /** to - from を局所座標で表したもの（同じ向きの Display の translation に使う）。 */
    static Vector3f delta(double[] from, double[] to) {
        return new Vector3f((float) (from[0] - to[0]), (float) (from[1] - to[1]), (float) (from[2] - to[2]));
    }

    static double seatX(int seat) {
        return 1.6 - 0.8 * seat;
    }

    /** 席に座る位置（床）。 */
    double[] seat(int seat) {
        return new double[]{seatX(seat), 0, SEAT_Z};
    }

    double[] dealer() {
        return new double[]{0, 0, DEALER_Z};
    }

    double[] shoe() {
        return new double[]{SHOE_X, TOP + 0.05, SHOE_Z};
    }

    /** 席の手札 hand（hands 個のうち）の index 枚目のカード。 */
    double[] playerCard(int seat, int hand, int hands, int index) {
        double base = seatX(seat) + (hand - (hands - 1) / 2.0) * 0.34;
        return new double[]{base + 0.06 * index, TOP + 0.004 + 0.003 * index, CARD_Z - 0.05 * index};
    }

    double[] dealerCard(int index) {
        return new double[]{-0.2 + 0.14 * index, TOP + 0.004 + 0.003 * index, DEALER_CARD_Z};
    }

    double[] chips(int seat) {
        return new double[]{seatX(seat), TOP + 0.001, CHIP_Z};
    }

    double[] dealerChips() {
        return new double[]{-0.9, TOP + 0.001, -0.6};
    }

    /** 手札の合計を表示する位置。 */
    double[] handLabel(int seat, int hand, int hands) {
        double[] card = playerCard(seat, hand, hands, 0);
        return new double[]{card[0], TOP + 0.45, card[2]};
    }

    double[] dealerLabel() {
        return new double[]{0, TOP + 0.7, DEALER_CARD_Z};
    }

    Location at(double[] local) {
        return at(local[0], local[1], local[2]);
    }

    /** テーブルが占めるブロック（4 x 2）。当たり判定のバリアを置く。 */
    List<Block> footprint() {
        List<Block> blocks = new ArrayList<>();
        for (int bx = -2; bx < 2; bx++) {
            for (int bz = -1; bz < 1; bz++) {
                blocks.add(at(bx + 0.5, 0.5, bz + 0.5).getBlock());
            }
        }
        return blocks;
    }

    boolean contains(Block block) {
        return footprint().contains(block);
    }
}
