package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bridge.BridgeProtocol;
import io.github.r0319.luckycasino.bridge.CardCode;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.concurrent.TimeUnit;

/**
 * 試作のカード 1 枚。Java 版には ItemDisplay を、統合版には Geyser 拡張が差し替える運び役の TextDisplay を出す。
 * 運び役は文字も背景も空なので Java 版では何も描かれない。
 */
final class PrototypeCard {

    /** 配る動き・めくる動きの片道（tick）。統合版パックのアニメーション（0.25 秒 × 2）と合わせる。 */
    private static final int STEP_TICKS = 5;
    /** 配り始めの位置（カードの向きから見て奥、ディーラー側）。 */
    private static final Vector3f DEAL_FROM = new Vector3f(0f, 0f, -1.5f);
    private static final Vector3f DEAL_MID = new Vector3f(0f, 0.4f, -0.75f);
    private static final Vector3f FLIP_LIFT = new Vector3f(0f, 0.25f, 0f);
    private static final Vector3f ZERO = new Vector3f();
    private static final Vector3f ONE = new Vector3f(1f, 1f, 1f);

    private final Plugin plugin;
    private final BridgeClient bridge;
    private final int code;
    private final ItemDisplay display;
    private final TextDisplay carrier;
    private boolean faceUp;
    /** 中身を見せているか（Java 版の表面モデルと統合版へ送る番号）。裏返し終えるまでは faceUp と別に持つ。 */
    private boolean revealed;
    private boolean settled;
    private boolean removed;

    private PrototypeCard(Plugin plugin, BridgeClient bridge, int code, ItemDisplay display, TextDisplay carrier) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.code = code;
        this.display = display;
        this.carrier = carrier;
    }

    /** location の位置に、奥から滑ってくる動きでカードを裏向きに配る。 */
    static PrototypeCard deal(Plugin plugin, BridgeClient bridge, Location location, int code) {
        Location at = location.clone();
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
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(cardItem(CardCode.HIDDEN));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(transform(DEAL_FROM, 0f));
        });

        PrototypeCard card = new PrototypeCard(plugin, bridge, code, display, carrier);
        bridge.sendCard(carrier.getUniqueId(), CardCode.HIDDEN, false, false)
                .completeOnTimeout(null, 1, TimeUnit.SECONDS)
                .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!card.removed) {
                        carrier.setVisibleByDefault(true);
                    }
                }));

        at.getWorld().playSound(at, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        card.animate(transform(DEAL_MID, 0f), 1);
        card.animate(transform(ZERO, 0f), 1 + STEP_TICKS);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!card.removed) {
                card.settled = true;
                card.sync();
            }
        }, 2L + STEP_TICKS * 2);
        return card;
    }

    /** 表に返す。番号はこのとき初めて統合版へ送る（伏せている間は送らない）。 */
    void flip() {
        if (faceUp || removed) {
            return;
        }
        faceUp = true;
        revealed = true;
        display.setItemStack(cardItem(code));
        sync();
        display.getWorld().playSound(display.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.9f);
        animate(transform(FLIP_LIFT, 90f), 0);
        animate(transform(ZERO, 180f), STEP_TICKS);
    }

    /** 裏に戻す。動きが終わってから中身を隠す。 */
    void unflip() {
        if (!faceUp || removed) {
            return;
        }
        faceUp = false;
        sync();
        animate(transform(FLIP_LIFT, 90f), 0);
        animate(transform(ZERO, 0f), STEP_TICKS);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!removed && !faceUp) {
                revealed = false;
                display.setItemStack(cardItem(CardCode.HIDDEN));
                sync();
            }
        }, STEP_TICKS * 2L + 1);
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

    boolean isFaceUp() {
        return faceUp;
    }

    int code() {
        return code;
    }

    /** 再接続時に拡張へ送り直す行。 */
    String stateLine() {
        return BridgeProtocol.card(carrier.getUniqueId(), visibleCode(), faceUp, settled);
    }

    private void sync() {
        bridge.sendCard(carrier.getUniqueId(), visibleCode(), faceUp, settled);
    }

    /** 統合版へ送る番号。裏向きで動き終えたら 0（中身を知らせない）。 */
    private int visibleCode() {
        return revealed ? code : CardCode.HIDDEN;
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
    private static Transformation transform(Vector3f translation, float degrees) {
        AxisAngle4f rotation = new AxisAngle4f((float) Math.toRadians(degrees), 0f, 0f, 1f);
        return new Transformation(new Vector3f(translation), rotation, new Vector3f(ONE), new AxisAngle4f());
    }

    private static ItemStack cardItem(int code) {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, modelKey(code));
        return item;
    }

    private static Key modelKey(int code) {
        return Key.key("luckycasino", "card/" + CardCode.modelName(code));
    }
}
