package io.github.r0319.luckyCasino.blackjack.animation;

import io.github.r0319.luckyCasino.blackjack.game.Card;
import io.github.r0319.luckyCasino.blackjack.game.Rank;
import io.github.r0319.luckyCasino.blackjack.game.Suit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages card {@link TextDisplay} entities on blackjack tables.
 *
 * <p>Cards are represented as flat {@link TextDisplay} entities that lie
 * horizontally on the table surface and show the rank + suit (or "?" for
 * face-down cards).  Movement is animated via a per-tick {@link BukkitRunnable}
 * that arcs the entity from the deck position to the destination slot.</p>
 *
 * <h3>Test animation ({@link #runTestAnimation(Player)})</h3>
 * <p>Run {@code /bj testcard} to trigger a demo sequence that shows all card
 * visual elements in order — useful for validating the look of animations
 * before a real game.</p>
 */
public class CardAnimationManager {

    /** Ticks over which the deal arc travels. */
    private static final int DEAL_TICKS = 15;
    /** Maximum height (in blocks) of the parabolic arc. */
    private static final float ARC_HEIGHT = 0.6f;

    private static final Color CARD_FACE_BG = Color.fromRGB(0xFFFAF0); // ivory
    private static final Color CARD_BACK_BG = Color.fromRGB(0x1A237E); // dark blue
    private static final int   BG_ALPHA     = 220;

    private final JavaPlugin plugin;

    /** All TextDisplay entities tracked per table, keyed by tableId. */
    private final Map<String, List<TextDisplay>> tableEntities = new HashMap<>();
    /** Maps entity UUID → Card data so flip() can reveal the card face. */
    private final Map<UUID, Card> hiddenCards = new HashMap<>();

    public CardAnimationManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public interface
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Spawns a card entity at {@code from} and animates it to {@code to}.
     *
     * @param tableId table this card belongs to (used for batch cleanup)
     * @param card    card data
     * @param from    spawn / deck position
     * @param to      resting position on the table
     * @param faceUp  true = show card face, false = show card back
     * @return the spawned {@link TextDisplay}
     */
    public TextDisplay dealCard(String tableId, Card card, Location from, Location to, boolean faceUp) {
        TextDisplay display = from.getWorld().spawn(from, TextDisplay.class, entity ->
                applyCardAppearance(entity, card, faceUp));

        if (!faceUp) {
            hiddenCards.put(display.getUniqueId(), card);
        }

        trackEntity(tableId, display);
        animateDeal(display, from, to);
        return display;
    }

    /**
     * Flips a face-down card entity to face-up with an interpolated 180° rotation.
     * The card's data must have been recorded by an earlier {@link #dealCard} call.
     */
    public void flipCard(Display entity) {
        if (!(entity instanceof TextDisplay td)) return;
        Card card = hiddenCards.remove(td.getUniqueId());
        if (card == null) return; // already face-up or unknown entity

        td.text(buildCardText(card));
        td.setBackgroundColor(withAlpha(CARD_FACE_BG, BG_ALPHA));
        td.setInterpolationDelay(0);
        td.setInterpolationDuration(10);
        td.setTransformation(buildFlatTransform(true));
    }

    /**
     * Removes all card entities that belong to the given table.
     * Call after SETTLEMENT is complete.
     */
    public void clearTableCards(String tableId) {
        List<TextDisplay> entities = tableEntities.remove(tableId);
        if (entities == null) return;
        for (TextDisplay td : entities) {
            if (!td.isDead()) {
                hiddenCards.remove(td.getUniqueId());
                td.remove();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Test / debug animation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs a self-contained demo animation relative to the given player's
     * position.  No table setup is required.
     *
     * <h4>Sequence</h4>
     * <ol>
     *   <li>t = 0  – 4 face-up player cards dealt one by one (A♠ K♥ Q♦ 7♣)</li>
     *   <li>t = 30 – 2 dealer cards: 1 face-up (J♠) + 1 face-down (?)</li>
     *   <li>t = 60 – Hidden dealer card flips to reveal (5♥)</li>
     *   <li>t = 200 – All test entities removed</li>
     * </ol>
     *
     * <p>This lets you verify: deal arc, face-up card colours, face-down back
     * design, and the flip reveal animation — all in one command.</p>
     *
     * @param player the player who ran the command; used as the origin point
     */
    public void runTestAnimation(Player player) {
        Location origin = player.getLocation().clone();
        // Deck source: 1.5 blocks above the player (simulates dealer hand)
        Location deck = origin.clone().add(0, 1.5, 0);

        // 4 player card destinations laid out horizontally in front
        Location[] pPos = {
            origin.clone().add(-0.9, 0,  2.0),
            origin.clone().add(-0.3, 0,  2.0),
            origin.clone().add( 0.3, 0,  2.0),
            origin.clone().add( 0.9, 0,  2.0),
        };
        // Dealer card destinations further in front
        Location dLeft  = origin.clone().add(-0.35, 0, 3.0);
        Location dRight = origin.clone().add( 0.35, 0, 3.0);

        String testId = "test_" + UUID.randomUUID().toString().substring(0, 8);

        Card[] playerCards = {
            new Card(Suit.SPADES,   Rank.ACE),
            new Card(Suit.HEARTS,   Rank.KING),
            new Card(Suit.DIAMONDS, Rank.QUEEN),
            new Card(Suit.CLUBS,    Rank.SEVEN),
        };

        player.sendMessage("§b§l── テストアニメーション開始 ──");
        player.sendMessage("§7① プレイヤーカード配布（A♠ K♥ Q♦ 7♣）× 4");

        // Deal player cards one by one with stagger
        for (int i = 0; i < 4; i++) {
            final int fi = i;
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                dealCard(testId, playerCards[fi], deck, pPos[fi], true), i * 7L);
        }

        // Deal dealer cards after a pause
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage("§7② ディーラーカード配布（表向きJ♠ ＋ 裏向き）");
            dealCard(testId, new Card(Suit.SPADES, Rank.JACK), deck, dLeft, true);
            TextDisplay hidden = dealCard(testId, new Card(Suit.HEARTS, Rank.FIVE), deck, dRight, false);

            // Flip after another pause
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage("§7③ ディーラー裏向きカードをフリップ（→ 5♥）");
                flipCard(hidden);
            }, 30L);
        }, 35L);

        // Cleanup
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            clearTableCards(testId);
            player.sendMessage("§7④ テストカード消去 ─ §a完了！");
        }, 200L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void applyCardAppearance(TextDisplay entity, Card card, boolean faceUp) {
        entity.text(faceUp
                ? buildCardText(card)
                : Component.text("  ?  ").color(NamedTextColor.WHITE));
        entity.setBackgroundColor(withAlpha(faceUp ? CARD_FACE_BG : CARD_BACK_BG, BG_ALPHA));
        entity.setBillboard(Display.Billboard.FIXED);
        entity.setTransformation(buildFlatTransform(false));
        entity.setShadowed(false);
        entity.setDefaultBackground(false);
        entity.setTextOpacity((byte) -1);
    }

    /** Card face text: e.g. " A♠ " in red/black depending on suit. */
    private Component buildCardText(Card card) {
        TextColor color = card.getSuit().isRed()
                ? TextColor.color(0xCC0000)
                : TextColor.color(0x111111);
        return Component.text(" " + card.getRank().getSymbol() + card.getSuit().getSymbol() + " ")
                .color(color);
    }

    /**
     * Transformation that lays the TextDisplay flat like a card on a table.
     * Pass {@code flipped = true} to add 180° rotation around Y for the
     * flip-reveal animation.
     */
    private Transformation buildFlatTransform(boolean flipped) {
        float yRot = flipped ? (float) Math.PI : 0f;
        return new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f((float) (Math.PI / 2), 1, 0, 0), // lay flat
                new Vector3f(0.45f, 0.45f, 0.45f),               // card size
                new AxisAngle4f(yRot, 0, 1, 0)                   // optional flip
        );
    }

    /** Parabolic arc from {@code from} to {@code to} over {@link #DEAL_TICKS} ticks. */
    private void animateDeal(TextDisplay display, Location from, Location to) {
        new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (tick >= DEAL_TICKS || display.isDead()) {
                    cancel();
                    if (!display.isDead()) display.teleport(to);
                    return;
                }
                double t = (double) tick / DEAL_TICKS;
                double x = lerp(from.getX(), to.getX(), t);
                double z = lerp(from.getZ(), to.getZ(), t);
                double y = lerp(from.getY(), to.getY(), t) + ARC_HEIGHT * Math.sin(Math.PI * t);
                display.teleport(new Location(from.getWorld(), x, y, z,
                        display.getLocation().getYaw(), display.getLocation().getPitch()));
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void trackEntity(String tableId, TextDisplay display) {
        tableEntities.computeIfAbsent(tableId, k -> new ArrayList<>()).add(display);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static Color withAlpha(Color color, int alpha) {
        return Color.fromARGB(alpha, color.getRed(), color.getGreen(), color.getBlue());
    }
}
