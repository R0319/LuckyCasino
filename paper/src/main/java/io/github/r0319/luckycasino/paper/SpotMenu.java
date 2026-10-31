package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 賭け場所を選んで賭ける卓（SpotTable）のメニュー。上 4 段に賭け場所、いちばん下の段に置くチップの額・合計・取り消し・
 * ルールブック・席を立つを並べる。
 */
final class SpotMenu implements Listener {

    private static final long[] CHIP_VALUES = {10, 50, 100, 500, 1000};
    private static final int CHIP_FIRST = 45;
    private static final int INFO = 50;
    private static final int CLEAR = 51;
    private static final int RULES = 52;
    private static final int LEAVE = 53;

    private static final class Holder implements InventoryHolder {
        final SpotTable table;
        long chip;
        Inventory inventory;

        Holder(SpotTable table) {
            this.table = table;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final LuckyCasinoPaper plugin;

    SpotMenu(LuckyCasinoPaper plugin) {
        this.plugin = plugin;
    }

    void open(Player player, SpotTable table) {
        if (table.seatOf(player.getUniqueId()) < 0) {
            return;
        }
        if (!table.canBet()) {
            player.sendMessage(L10n.tr("今の回が終わったら賭けられます").color(NamedTextColor.GRAY));
            return;
        }
        Holder holder = new Holder(table);
        holder.chip = table.settings().minBet();
        holder.inventory = Bukkit.createInventory(holder, 54,
                Glyphs.menuTitle(player, 54, L10n.tr("%s：賭ける場所を選ぶ", table.type().displayName())));
        render(holder, player);
        player.openInventory(holder.inventory);
        player.sendMessage(L10n.tr("閉じてしまったら ").color(NamedTextColor.GRAY)
                .append(L10n.tr("[賭ける]").color(NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/casino bet")))
                .append(L10n.tr(" で開き直せます").color(NamedTextColor.GRAY)));
    }

    /** 勝負が始まったら、この卓のメニューを閉じる。 */
    void close(SpotTable table) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Holder holder && holder.table == table) {
                player.closeInventory();
            }
        }
    }

    private void render(Holder holder, Player player) {
        SpotTable table = holder.table;
        int seat = table.seatOf(player.getUniqueId());
        Inventory inv = holder.inventory;
        inv.clear();
        var mine = seat < 0 ? java.util.Map.<String, Long>of() : table.bets(seat);
        for (SpotTable.Spot spot : table.spots()) {
            List<Component> lore = new ArrayList<>();
            lore.add(spot.help().color(NamedTextColor.GRAY));
            Long placed = mine.get(spot.id());
            if (placed != null) {
                lore.add(L10n.tr("ここに %s 枚賭けています", placed).color(NamedTextColor.GREEN));
            }
            lore.add(L10n.tr("クリックで %s 枚置く", holder.chip).color(NamedTextColor.YELLOW));
            ItemStack item = Menus.named(ItemStack.of(spot.icon()), spot.name().color(NamedTextColor.WHITE), lore);
            if (placed != null) {
                item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            inv.setItem(spot.slot(), item);
        }
        int slot = CHIP_FIRST;
        for (long value : chipValues(table)) {
            boolean selected = value == holder.chip;
            inv.setItem(slot++, Menus.named(Items.model("chip/c" + value + "_" + (selected ? 3 : 1)),
                    L10n.tr("%s 枚ずつ置く", value).color(selected ? NamedTextColor.GREEN : NamedTextColor.GOLD),
                    List.of(L10n.tr(selected ? "選択中" : "クリックで切り替え").color(NamedTextColor.GRAY))));
        }
        long chips = seat < 0 ? 0 : table.seat(seat).chips;
        long total = seat < 0 ? 0 : table.total(seat);
        SpotSettings s = table.settings();
        inv.setItem(INFO, Menus.named(ItemStack.of(Material.PAPER), L10n.tr("賭けた合計 %s 枚", total).color(NamedTextColor.YELLOW),
                List.of(L10n.tr("手元のチップ: %s 枚", chips).color(NamedTextColor.GRAY),
                        L10n.tr("1 か所 %s〜%s 枚、合計 %s 枚まで", s.minBet(), s.maxBet(), s.maxTotal()).color(NamedTextColor.GRAY),
                        L10n.tr("同じ場所をもう一度クリックすると積み増します").color(NamedTextColor.GRAY))));
        inv.setItem(CLEAR, Menus.named(ItemStack.of(Material.BARRIER), L10n.tr("賭けを全部取り消す").color(NamedTextColor.RED), List.of()));
        inv.setItem(RULES, Menus.named(ItemStack.of(Material.WRITTEN_BOOK), L10n.tr("ルールブック").color(NamedTextColor.AQUA),
                List.of(L10n.tr("遊び方と配当を読む").color(NamedTextColor.GRAY))));
        inv.setItem(LEAVE, Menus.named(ItemStack.of(Material.OAK_DOOR), L10n.tr("席を立つ").color(NamedTextColor.GRAY),
                List.of(L10n.tr("チップを精算して席を離れます").color(NamedTextColor.GRAY))));
    }

    private static List<Long> chipValues(SpotTable table) {
        List<Long> out = new ArrayList<>();
        for (long value : CHIP_VALUES) {
            if (value >= table.settings().minBet() && value <= table.settings().maxBet() && out.size() < INFO - CHIP_FIRST) {
                out.add(value);
            }
        }
        return out;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        SpotTable table = holder.table;
        int seat = table.seatOf(player.getUniqueId());
        if (seat < 0 || plugin.tables().table(table.id()) != table) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        switch (slot) {
            case LEAVE -> {
                player.closeInventory();
                table.leave(player.getUniqueId());
                return;
            }
            case RULES -> {
                player.closeInventory();
                Rulebook.open(player, table.type());
                return;
            }
            case CLEAR -> table.clearBets(seat);
            default -> {
                List<Long> values = chipValues(table);
                if (slot >= CHIP_FIRST && slot < CHIP_FIRST + values.size()) {
                    holder.chip = values.get(slot - CHIP_FIRST);
                } else {
                    for (SpotTable.Spot spot : table.spots()) {
                        if (spot.slot() == slot) {
                            Component error = table.place(seat, spot.id(), holder.chip);
                            if (error != null) {
                                player.sendMessage(error.color(NamedTextColor.RED));
                            }
                        }
                    }
                }
            }
        }
        if (!table.canBet()) {
            player.closeInventory();
            return;
        }
        render(holder, player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
