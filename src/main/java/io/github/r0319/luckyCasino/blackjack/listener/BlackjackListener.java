package io.github.r0319.luckyCasino.blackjack.listener;

import io.github.r0319.luckyCasino.blackjack.BlackjackModule;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles game-related player events for the blackjack module.
 *
 * <ul>
 *   <li>Freezes seated players in place (prevents walking away from their slot).</li>
 *   <li>Cleans up game state when a player disconnects.</li>
 * </ul>
 */
public class BlackjackListener implements Listener {

    private final BlackjackModule module;

    public BlackjackListener(BlackjackModule module) {
        this.module = module;
    }

    /**
     * Cancels block-position movement for frozen (seated) players while
     * still allowing head rotation (yaw/pitch changes are passed through).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!module.isFrozen(event.getPlayer())) return;

        Location from = event.getFrom();
        Location to   = event.getTo();

        // Allow head rotation; block any actual position change
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return; // head rotation only — allow
        }

        // Keep the same position but pass the new viewing direction
        Location cancel = from.clone();
        cancel.setYaw(to.getYaw());
        cancel.setPitch(to.getPitch());
        event.setTo(cancel);
    }

    /**
     * Cancels all damage to the dealer NPC.
     * Belt-and-suspenders on top of {@code setInvulnerable(true)}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (module.isDealerNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Removes disconnected players from active games and restores their state. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        module.handlePlayerQuit(event.getPlayer());
    }
}
