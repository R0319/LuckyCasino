package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 両替と賭け金のチェスト GUI（Java 版向け。統合版のフォームは後で足す）。 */
final class Menus implements Listener {

    private static final int[] CHIP_SLOTS = {10, 11, 12, 13, 14};
    private static final long[] CHIP_VALUES = {10, 50, 100, 500, 1000};
    private static final int INFO = 4;
    private static final int CLEAR = 16;
    private static final int CONFIRM = 22;
    private static final int LEAVE = 26;

    private final LuckyCasinoPaper plugin;

    Menus(LuckyCasinoPaper plugin) {
        this.plugin = plugin;
    }

    // ---- 両替 ----

    private static final class BuyIn implements InventoryHolder {
        final BlackjackTable table;
        final int seat;
        final List<Long> options = new ArrayList<>();
        Inventory inventory;

        BuyIn(BlackjackTable table, int seat) {
            this.table = table;
            this.seat = seat;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    void openBuyIn(Player player, BlackjackTable table, int seat) {
        BuyIn holder = new BuyIn(table, seat);
        Bank bank = table.bank();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(bank.practice() ? "練習用チップを受け取る" : "チップに両替する"));
        holder.inventory = inventory;
        long min = table.settings().rules().minBet();
        if (bank.practice()) {
            long chips = bank.affordable(player);
            holder.options.add(chips);
            inventory.setItem(13, named(Items.model("chip/c100_5"), Component.text("チップ " + chips + " 枚を受け取る", NamedTextColor.GOLD),
                    List.of(Component.text("練習用なのでお金は使いません", NamedTextColor.GRAY),
                            Component.text("席を立つと回収します", NamedTextColor.GRAY))));
        } else {
            long affordable = bank.affordable(player);
            List<Long> buyIns = table.settings().buyIns();
            int start = 13 - Math.min(buyIns.size(), 5) / 2;
            for (int i = 0; i < Math.min(buyIns.size(), 5); i++) {
                long chips = buyIns.get(i);
                boolean ok = chips <= affordable && chips >= min;
                holder.options.add(chips);
                inventory.setItem(start + i, named(
                        ok ? Items.model("chip/" + icon(chips)) : ItemStack.of(Material.GRAY_DYE),
                        Component.text("チップ " + chips + " 枚", ok ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY),
                        List.of(Component.text(ok ? "クリックで両替して座る" : "お金が足りません", ok ? NamedTextColor.GREEN : NamedTextColor.RED),
                                Component.text("両替できる上限: " + affordable + " 枚", NamedTextColor.GRAY))));
            }
        }
        player.openInventory(inventory);
    }

    private static String icon(long chips) {
        for (long value : ChipView.DENOMINATIONS) {
            if (chips >= value) {
                return "c" + value + "_" + Math.min(chips / value, 10);
            }
        }
        return "c10_1";
    }

    // ---- 賭ける ----

    private static final class Bet implements InventoryHolder {
        final BlackjackTable table;
        long amount;
        Inventory inventory;

        Bet(BlackjackTable table) {
            this.table = table;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    void openBet(Player player, BlackjackTable table) {
        int seat = table.seatOf(player.getUniqueId());
        if (seat < 0 || !table.canBet(seat)) {
            return;
        }
        Bet holder = new Bet(table);
        holder.amount = Math.min(table.settings().rules().minBet(), table.seat(seat).chips);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("賭け金を決める"));
        holder.inventory = inventory;
        for (int i = 0; i < CHIP_SLOTS.length; i++) {
            inventory.setItem(CHIP_SLOTS[i], named(Items.model("chip/c" + CHIP_VALUES[i] + "_1"),
                    Component.text(CHIP_VALUES[i] + " 枚チップ", NamedTextColor.GOLD),
                    List.of(Component.text("左クリック: 足す / 右クリック: 減らす", NamedTextColor.GRAY))));
        }
        inventory.setItem(CLEAR, named(ItemStack.of(Material.BARRIER), Component.text("最低額に戻す", NamedTextColor.RED), List.of()));
        inventory.setItem(LEAVE, named(ItemStack.of(Material.OAK_DOOR), Component.text("席を立つ", NamedTextColor.GRAY),
                List.of(Component.text("チップを精算して席を離れます", NamedTextColor.GRAY))));
        render(holder, player);
        player.openInventory(inventory);
        player.sendMessage(Component.text("閉じてしまったら ", NamedTextColor.GRAY)
                .append(Component.text("[賭ける]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/casino bet")))
                .append(Component.text(" で開き直せます", NamedTextColor.GRAY)));
    }

    private void render(Bet holder, Player player) {
        BlackjackTable table = holder.table;
        int seat = table.seatOf(player.getUniqueId());
        long chips = seat < 0 ? 0 : table.seat(seat).chips;
        holder.inventory.setItem(INFO, named(ItemStack.of(Material.PAPER), Component.text("賭け金 " + holder.amount + " 枚", NamedTextColor.YELLOW),
                List.of(Component.text("手元のチップ: " + chips + " 枚", NamedTextColor.GRAY),
                        Component.text("この卓の賭け金: " + table.settings().rules().minBet() + "〜" + table.settings().rules().maxBet() + " 枚", NamedTextColor.GRAY))));
        holder.inventory.setItem(CONFIRM, named(ItemStack.of(Material.LIME_WOOL), Component.text(holder.amount + " 枚で決定", NamedTextColor.GREEN),
                List.of(Component.text("全員が決めるか、締め切りで配り始めます", NamedTextColor.GRAY))));
    }

    // ---- クリック ----

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof BuyIn) && !(holder instanceof Bet)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder instanceof BuyIn buyIn) {
            int index = slot - (13 - (buyIn.table.bank().practice() ? 0 : Math.min(buyIn.table.settings().buyIns().size(), 5) / 2));
            if (index < 0 || index >= buyIn.options.size()) {
                return;
            }
            player.closeInventory();
            plugin.tables().sit(player, buyIn.table, buyIn.seat, buyIn.options.get(index));
        } else if (holder instanceof Bet bet) {
            int seat = bet.table.seatOf(player.getUniqueId());
            if (seat < 0 || !bet.table.canBet(seat)) {
                player.closeInventory();
                return;
            }
            long chips = bet.table.seat(seat).chips;
            long min = bet.table.settings().rules().minBet();
            long max = Math.min(bet.table.settings().rules().maxBet(), chips);
            for (int i = 0; i < CHIP_SLOTS.length; i++) {
                if (slot == CHIP_SLOTS[i]) {
                    long step = event.getClick() == ClickType.RIGHT ? -CHIP_VALUES[i] : CHIP_VALUES[i];
                    bet.amount = Math.max(min, Math.min(max, bet.amount + step));
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.6f);
                }
            }
            if (slot == CLEAR) {
                bet.amount = Math.min(min, chips);
            } else if (slot == CONFIRM) {
                player.closeInventory();
                bet.table.confirmBet(player, bet.amount);
                return;
            } else if (slot == LEAVE) {
                player.closeInventory();
                bet.table.leave(player.getUniqueId());
                return;
            }
            render(bet, player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof BuyIn || holder instanceof Bet) {
            event.setCancelled(true);
        }
    }

    static ItemStack named(ItemStack item, Component name, List<Component> lore) {
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        });
        return item;
    }
}
