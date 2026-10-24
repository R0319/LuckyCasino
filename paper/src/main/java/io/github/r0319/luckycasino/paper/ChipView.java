package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** テーブルに置いたチップの山。額を額面ごとの柱に分けて見せる。 */
final class ChipView {

    /** 額面（大きい順）とモデル名。リソースパック（tools/gen_table.py）と合わせる。 */
    static final long[] DENOMINATIONS = {1000, 500, 100, 50, 10};
    private static final int MAX_STACK = 10;
    private static final float SCALE = 0.3f;
    private static final double SPACING = 0.15;
    private static final int SLIDE_TICKS = 8;

    private final List<ItemDisplay> stacks = new ArrayList<>();
    private TextDisplay label;
    private Location base;
    private long amount;

    long amount() {
        return amount;
    }

    /** base の位置に amount 分のチップを置き直す。0 なら片付ける。 */
    void show(Location base, long amount) {
        if (amount == this.amount && base.equals(this.base)) {
            return;
        }
        remove();
        this.base = base.clone();
        this.amount = amount;
        if (amount <= 0) {
            return;
        }
        List<ItemStack> columns = columns(amount);
        for (int i = 0; i < columns.size(); i++) {
            double offset = (i - (columns.size() - 1) / 2.0) * SPACING;
            Location at = offset(base, offset);
            ItemStack item = columns.get(i);
            stacks.add(at.getWorld().spawn(at, ItemDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setItemStack(item);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f()));
                entity.setTeleportDuration(SLIDE_TICKS);
            }));
        }
        Location labelAt = base.clone().add(0, 0.28, 0);
        label = labelAt.getWorld().spawn(labelAt, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.text(Component.text(amount, NamedTextColor.GOLD));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
            entity.setTeleportDuration(SLIDE_TICKS);
        });
    }

    /** チップを target へ滑らせる（回収・払い出しの動き）。 */
    void slideTo(Location target) {
        for (int i = 0; i < stacks.size(); i++) {
            double offset = (i - (stacks.size() - 1) / 2.0) * SPACING;
            stacks.get(i).teleport(offset(target, offset));
        }
        if (label != null) {
            label.teleport(target.clone().add(0, 0.28, 0));
        }
        base = target.clone();
    }

    void remove() {
        stacks.forEach(ItemDisplay::remove);
        stacks.clear();
        if (label != null) {
            label.remove();
            label = null;
        }
        amount = 0;
        base = null;
    }

    /** テーブルの横方向（局所 +X）へずらす。 */
    private static Location offset(Location base, double offset) {
        double rad = Math.toRadians(base.getYaw());
        return base.clone().add(offset * Math.cos(rad), 0, offset * Math.sin(rad));
    }

    /** 額面ごとの柱（多い額面から最大 MAX_STACK 枚ずつ）。10 未満の端数は見せない。 */
    static List<ItemStack> columns(long amount) {
        List<ItemStack> columns = new ArrayList<>();
        long rest = amount;
        for (long value : DENOMINATIONS) {
            long count = rest / value;
            rest -= count * value;
            if (count > 0) {
                columns.add(Items.model("chip/c" + value + "_" + Math.min(count, MAX_STACK)));
            }
        }
        return columns;
    }
}
