package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * ディーラーの見た目のメニュー（置いた人と管理者だけ）。手に持った物を枠に重ねてクリックすると、その写しを着せる
 * （手元の物は減らない。着せた物は見た目だけで取り出せないので、増えることもない）。空の手でクリックすると外す。
 * スキンと名前はチャットのボタンから /casino dealer で変える。
 */
final class DealerMenu implements Listener {

    private static final int INFO = 4;
    private static final int FIRST_SLOT = 10;
    private static final int SKIN = 20;
    private static final int NAME = 22;
    private static final int RESET = 24;

    private static final List<String> SLOT_NAMES = List.of("頭", "胴", "脚", "足", "利き手", "もう片方の手");
    private static final List<Material> SLOT_EMPTY = List.of(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, Material.STICK, Material.SHIELD);

    private static final class Holder implements InventoryHolder {
        final CasinoTable table;
        Inventory inventory;

        Holder(CasinoTable table) {
            this.table = table;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final LuckyCasinoPaper plugin;

    DealerMenu(LuckyCasinoPaper plugin) {
        this.plugin = plugin;
    }

    void open(Player player, CasinoTable table) {
        Holder holder = new Holder(table);
        holder.inventory = Bukkit.createInventory(holder, 36, L10n.tr("ディーラーの見た目"));
        fill(holder);
        player.openInventory(holder.inventory);
    }

    private void fill(Holder holder) {
        Inventory inventory = holder.inventory;
        DealerLook look = holder.table.dealerLook();
        inventory.setItem(INFO, Menus.named(ItemStack.of(Material.ARMOR_STAND), look.nameText(), List.of(
                L10n.tr("スキン: %s", look.skin() == null ? L10n.tr("既定") : look.skin()).color(NamedTextColor.GRAY),
                L10n.tr("手に持った物を下の枠に重ねてクリックすると着せます").color(NamedTextColor.GRAY),
                L10n.tr("（手元の物は減りません。空の手でクリックすると外します）").color(NamedTextColor.GRAY))));
        for (int i = 0; i < DealerLook.SLOTS.size(); i++) {
            ItemStack worn = look.equipment().get(DealerLook.SLOTS.get(i));
            Component name = L10n.tr(SLOT_NAMES.get(i)).color(NamedTextColor.YELLOW);
            if (worn == null) {
                inventory.setItem(FIRST_SLOT + i + (i >= 4 ? 1 : 0), Menus.named(ItemStack.of(SLOT_EMPTY.get(i)), name.append(
                        L10n.tr("（なし）").color(NamedTextColor.DARK_GRAY)), List.of(
                        L10n.tr("手に持った物を重ねてクリックで着せる").color(NamedTextColor.GRAY))));
            } else {
                ItemStack shown = worn.clone();
                shown.lore(List.of(name, L10n.tr("空の手でクリックで外す").color(NamedTextColor.GRAY)));
                inventory.setItem(FIRST_SLOT + i + (i >= 4 ? 1 : 0), shown);
            }
        }
        inventory.setItem(SKIN, Menus.named(ItemStack.of(Material.PLAYER_HEAD), L10n.tr("スキンを変える").color(NamedTextColor.GOLD),
                List.of(L10n.tr("プレイヤー名を指定します（チャットに入力欄が出ます）").color(NamedTextColor.GRAY))));
        inventory.setItem(NAME, Menus.named(ItemStack.of(Material.NAME_TAG), L10n.tr("名前を変える").color(NamedTextColor.GOLD),
                List.of(L10n.tr("頭の上に出る名前（%s 文字まで）", DealerLook.MAX_NAME).color(NamedTextColor.GRAY))));
        inventory.setItem(RESET, Menus.named(ItemStack.of(Material.BARRIER), L10n.tr("元に戻す").color(NamedTextColor.RED),
                List.of(L10n.tr("スキン・服装・名前を最初の状態にします").color(NamedTextColor.GRAY))));
    }

    private static EquipmentSlot slotAt(int raw) {
        int i = raw - FIRST_SLOT;
        if (i >= 5) {
            i--;
        }
        if (raw == FIRST_SLOT + 4 || i < 0 || i >= DealerLook.SLOTS.size()) {
            return null;
        }
        return DealerLook.SLOTS.get(i);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // 下の自分の持ち物は普通に触れる（手に取って重ねるため）。上の枠への出し入れと、まとめて動かす操作は止める
        boolean top = event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (!top) {
            switch (event.getClick()) {
                case LEFT, RIGHT -> {
                    return;
                }
                default -> {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        event.setCancelled(true);
        CasinoTable table = holder.table;
        if (!table.canEdit(player) || plugin.tables().table(table.id()) != table) {
            player.closeInventory();
            return;
        }
        int raw = event.getRawSlot();
        EquipmentSlot slot = slotAt(raw);
        if (slot != null) {
            ItemStack cursor = event.getCursor();
            plugin.tables().changeDealer(table, table.dealerLook().withItem(slot, cursor.isEmpty() ? null : cursor.asOne()));
            fill(holder);
            return;
        }
        switch (raw) {
            case SKIN -> suggest(player, "/casino dealer skin ", "スキンにするプレイヤー名を入力してください（英数字と _ の 3〜16 文字）");
            case NAME -> suggest(player, "/casino dealer name ", "頭の上に出す名前を入力してください");
            case RESET -> {
                plugin.tables().changeDealer(table, DealerLook.DEFAULT);
                fill(holder);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            int size = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(raw -> raw < size)) {
                event.setCancelled(true);
            }
        }
    }

    private static void suggest(Player player, String command, String help) {
        player.closeInventory();
        player.sendMessage(L10n.tr("[ここをクリックして入力] ").color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command)))
                .append(L10n.tr(help).color(NamedTextColor.GRAY)));
    }
}
