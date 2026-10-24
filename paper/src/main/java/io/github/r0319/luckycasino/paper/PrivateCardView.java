package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bridge.CardCode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

/**
 * 持ち主にだけ表が見えるカード（ポーカーの手札）。表の ItemDisplay は持ち主にだけ送り、
 * ほかの人には裏の ItemDisplay を見せる。表は最初から誰にも送らない設定で出すので、中身が漏れることはない。
 * 統合版（Geyser 拡張）への表示はまだ無い。
 */
final class PrivateCardView {

    private static final Vector3f FLIP_LIFT = new Vector3f(0f, 0.15f, 0f);
    private static final Vector3f ZERO = new Vector3f();

    private final Plugin plugin;
    private final ItemDisplay front;
    private final ItemDisplay back;
    private final int code;
    private boolean revealed;
    private boolean removed;

    private PrivateCardView(Plugin plugin, ItemDisplay front, ItemDisplay back, int code) {
        this.plugin = plugin;
        this.front = front;
        this.back = back;
        this.code = code;
    }

    /** target へ from（target から見た局所座標の差）から配る。owner が null（ボット）なら誰にも表を見せない。 */
    static PrivateCardView deal(Plugin plugin, Location target, Vector3f from, int code, Player owner) {
        Location at = target.clone();
        at.setPitch(0f);
        ItemDisplay front = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            entity.setItemStack(Items.card(code));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(CardView.transform(from, 180f));
            entity.setTeleportDuration(CardView.STEP_TICKS);
        });
        ItemDisplay back = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.card(CardCode.HIDDEN));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(CardView.transform(from, 0f));
            entity.setTeleportDuration(CardView.STEP_TICKS);
        });
        if (owner != null) {
            owner.showEntity(plugin, front);
            owner.hideEntity(plugin, back);
        }
        PrivateCardView card = new PrivateCardView(plugin, front, back, code);
        at.getWorld().playSound(at, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
        Vector3f mid = new Vector3f(from).mul(0.5f).add(0f, 0.3f, 0f);
        card.animate(front, CardView.transform(mid, 180f), 1);
        card.animate(back, CardView.transform(mid, 0f), 1);
        card.animate(front, CardView.transform(ZERO, 180f), 1 + CardView.STEP_TICKS);
        card.animate(back, CardView.transform(ZERO, 0f), 1 + CardView.STEP_TICKS);
        return card;
    }

    /** ショーダウンで全員に表を見せる。 */
    void reveal() {
        if (revealed || removed) {
            return;
        }
        revealed = true;
        front.remove();
        back.setItemStack(Items.card(code));
        for (Player player : back.getWorld().getPlayers()) {
            player.showEntity(plugin, back);
        }
        back.getWorld().playSound(back.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.9f);
        animate(back, CardView.transform(FLIP_LIFT, 90f), 0);
        animate(back, CardView.transform(ZERO, 180f), CardView.STEP_TICKS);
    }

    /** 降りたカードをディーラーの手元へ滑らせてから片付ける。 */
    void muck(Location target) {
        if (removed) {
            return;
        }
        Location at = target.clone();
        at.setPitch(0f);
        at.setYaw(back.getLocation().getYaw());
        if (!revealed) {
            front.teleport(at);
        }
        back.teleport(at);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::remove, CardView.STEP_TICKS + 1L);
    }

    void remove() {
        if (removed) {
            return;
        }
        removed = true;
        front.remove();
        back.remove();
    }

    private void animate(ItemDisplay display, Transformation target, long delayTicks) {
        Runnable apply = () -> {
            if (removed || !display.isValid()) {
                return;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(CardView.STEP_TICKS);
            display.setTransformation(target);
        };
        if (delayTicks <= 0) {
            apply.run();
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, apply, delayTicks);
        }
    }
}
