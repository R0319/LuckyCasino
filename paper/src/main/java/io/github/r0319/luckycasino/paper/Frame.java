package io.github.r0319.luckycasino.paper;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * テーブルの局所座標。テーブル中心の床を原点、プレイヤー側を +Z、プレイヤーから見て右を +X とし、
 * ワールド座標に直す。占めるブロックは種類ごとに TableShape.cells で決まる。
 */
final class Frame {

    private final World world;
    private final double ox;
    private final double oy;
    private final double oz;
    /** 局所 +Z（プレイヤー側）がこの yaw のエンティティの正面になる。90 度刻み。 */
    private final float yaw;
    private final double sin;
    private final double cos;

    Frame(World world, double ox, double oy, double oz, float yaw) {
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

    Location at(double[] local) {
        return at(local[0], local[1], local[2]);
    }

    /** from - to を局所座標で表したもの（同じ向きの Display の translation に使う）。 */
    static Vector3f delta(double[] from, double[] to) {
        return new Vector3f((float) (from[0] - to[0]), (float) (from[1] - to[1]), (float) (from[2] - to[2]));
    }

    /**
     * from - to を、テーブルから yawOffset 度回した向きの Display の局所座標で表したもの
     * （テーブルの端の席に向けて置くカードなど）。
     */
    static Vector3f delta(double[] from, double[] to, float yawOffset) {
        double dx = from[0] - to[0];
        double dz = from[2] - to[2];
        double rad = Math.toRadians(yawOffset);
        double x = dx * Math.cos(rad) + dz * Math.sin(rad);
        double z = -dx * Math.sin(rad) + dz * Math.cos(rad);
        return new Vector3f((float) x, (float) (from[1] - to[1]), (float) z);
    }

    /** 局所座標の位置に、テーブルから yawOffset 度回した向きで。 */
    Location at(double[] local, float yawOffset) {
        Location at = at(local);
        at.setYaw(yaw + yawOffset);
        return at;
    }

    /** 局所座標で中心を指したブロックの一覧。 */
    List<Block> blocks(double[][] cells) {
        List<Block> blocks = new ArrayList<>();
        for (double[] cell : cells) {
            blocks.add(at(cell).getBlock());
        }
        return blocks;
    }
}
