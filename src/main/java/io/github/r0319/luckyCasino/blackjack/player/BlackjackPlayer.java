package io.github.r0319.luckyCasino.blackjack.player;

import io.github.r0319.luckyCasino.blackjack.game.Hand;

import java.util.UUID;

/**
 * Represents one player's in-game state during a blackjack round.
 */
public class BlackjackPlayer {

    private final UUID uuid;
    private final String name;
    /** 0-indexed table slot (0–3). */
    private final int slot;

    private double bet;
    private final Hand hand = new Hand();

    private boolean stood;
    private boolean busted;
    private boolean doubledDown;

    public BlackjackPlayer(UUID uuid, String name, int slot) {
        this.uuid = uuid;
        this.name = name;
        this.slot = slot;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public int getSlot() { return slot; }

    public double getBet() { return bet; }
    public void setBet(double bet) { this.bet = bet; }

    public Hand getHand() { return hand; }

    public boolean isStood() { return stood; }
    public void setStood(boolean stood) { this.stood = stood; }

    public boolean isBusted() { return busted; }
    public void setBusted(boolean busted) { this.busted = busted; }

    public boolean isDoubledDown() { return doubledDown; }
    public void setDoubledDown(boolean doubledDown) { this.doubledDown = doubledDown; }

    /** True once the player has placed a non-zero bet. */
    public boolean hasBet() { return bet > 0; }

    /** True when the player cannot take any more actions this round. */
    public boolean isDone() { return stood || busted || doubledDown; }

    /** Clears hand and resets all per-round state (keeps slot and identity). */
    public void reset() {
        hand.clear();
        bet = 0;
        stood = false;
        busted = false;
        doubledDown = false;
    }
}
