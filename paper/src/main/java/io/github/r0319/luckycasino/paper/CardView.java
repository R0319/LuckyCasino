package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bridge.BridgeProtocol;
import io.github.r0319.luckycasino.bridge.CardCode;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.concurrent.TimeUnit;

/**
 * テーブル上のカード 1 枚。Java 版には ItemDisplay を、統合版には Geyser 拡張が差し替える運び役の TextDisplay を出す。
 * 運び役は文字も背景も空なので Java 版では何も描かれない。
 */
final class CardView {

    /** 配る動き・めくる動きの片道（tick）。統合版パックのアニメーション（0.25 秒 × 2）と合わせる。 */
    static final int STEP_TICKS = 5;
    private static final Vector3f FLIP_LIFT = new Vector3f(0f, 0.15f, 0f);
    private static final Vector3f ZERO = new Vector3f();

    private final Plugin plugin;
    private final BridgeClient bridge;
    private final ItemDisplay display;
    private final TextDisplay carrier;
    private int code;
    private boolean faceUp;
    private boolean settled;
    private boolean removed;

    private CardView(Plugin plugin, BridgeClient bridge, ItemDisplay display, TextDisplay carrier, int code, boolean faceUp) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.display = display;
        this.carrier = carrier;
        this.code = code;
        this.faceUp = faceUp;
    }

    /**
     * target の位置へ、from（target から見た局所座標の差）から弧を描いて配る。
     * 裏向きで配るときは code に CardCode.HIDDEN を渡し、中身を誰にも送らない。
     */
    static CardView deal(Plugin plugin, BridgeClient bridge, Location target, Vector3f from, int code, boolean faceUp) {
        Location at = target.clone();
        at.setPitch(0f);
        TextDisplay carrier = at.getWorld().spawn(at, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            // 拡張がカードとして登録し終えるまで誰にも送らない（先に届くと統合版で差し替えられない）
            entity.setVisibleByDefault(false);
            entity.text(Component.empty());
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setShadowed(false);
            entity.setBillboard(Display.Billboard.FIXED);
        });
        float flip = faceUp ? 180f : 0f;
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.card(faceUp ? code : CardCode.HIDDEN));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(transform(from, flip));
            entity.setTeleportDuration(STEP_TICKS);
        });

        CardView card = new CardView(plugin, bridge, display, carrier, code, faceUp);
        bridge.sendCard(carrier.getUniqueId(), card.visibleCode(), faceUp, false)
                .completeOnTimeout(null, 1, TimeUnit.SECONDS)
                .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!card.removed) {
                        carrier.setVisibleByDefault(true);
                    }
                }));

        at.getWorld().playSound(at, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
        Vector3f mid = new Vector3f(from).mul(0.5f).add(0f, 0.3f, 0f);
        card.animate(transform(mid, flip), 1);
        card.animate(transform(ZERO, flip), 1 + STEP_TICKS);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!card.removed) {
                card.settled = true;
                card.sync();
            }
        }, 2L + STEP_TICKS * 2);
        return card;
    }

    /** 伏せていたカードを表に返す。番号はこのとき初めて表示側へ渡す。 */
    void reveal(int code) {
        if (faceUp || removed) {
            return;
        }
        this.code = code;
        faceUp = true;
        display.setItemStack(Items.card(code));
        sync();
        display.getWorld().playSound(display.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.9f);
        animate(transform(FLIP_LIFT, 90f), 0);
        animate(transform(ZERO, 180f), STEP_TICKS);
    }

    /** 置き場所を変える（スプリットで手を分けたときなど）。 */
    void moveTo(Location target) {
        if (!removed) {
            Location at = target.clone();
            at.setPitch(0f);
            display.teleport(at);
            carrier.teleport(at);
        }
    }

    /** このカードの表示に使っているエンティティか。 */
    boolean owns(org.bukkit.entity.Entity entity) {
        return entity.equals(display) || entity.equals(carrier);
    }

    void remove() {
        if (removed) {
            return;
        }
        removed = true;
        bridge.sendRemove(carrier.getUniqueId());
        display.remove();
        carrier.remove();
    }

    /** 再接続時に拡張へ送り直す行。 */
    String stateLine() {
        return BridgeProtocol.card(carrier.getUniqueId(), visibleCode(), faceUp, settled);
    }

    private void sync() {
        bridge.sendCard(carrier.getUniqueId(), visibleCode(), faceUp, settled);
    }

    private int visibleCode() {
        return faceUp ? code : CardCode.HIDDEN;
    }

    private void animate(Transformation target, long delayTicks) {
        Runnable apply = () -> {
            if (removed) {
                return;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(STEP_TICKS);
            display.setTransformation(target);
        };
        if (delayTicks <= 0) {
            apply.run();
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, apply, delayTicks);
        }
    }

    /** カードの長い辺（Z 軸）を軸に degrees 度回した姿勢。 */
    static Transformation transform(Vector3f translation, float degrees) {
        AxisAngle4f rotation = new AxisAngle4f((float) Math.toRadians(degrees), 0f, 0f, 1f);
        float s = BlackjackLayout.CARD_SCALE;
        return new Transformation(new Vector3f(translation), rotation, new Vector3f(s, s, s), new AxisAngle4f());
    }
}
