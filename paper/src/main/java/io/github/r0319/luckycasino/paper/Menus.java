package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.poker.Legal;
import io.github.r0319.luckycasino.roulette.BetType;
import io.github.r0319.luckycasino.roulette.RouletteBet;
import io.github.r0319.luckycasino.roulette.Wheel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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

/** 両替・賭け金・ルーレットの盤・ポーカーのレイズのチェスト GUI（Java 版向け）。 */
final class Menus implements Listener {

    private static final long[] CHIP_VALUES = {10, 50, 100, 500, 1000};

    private final LuckyCasinoPaper plugin;

    Menus(LuckyCasinoPaper plugin) {
        this.plugin = plugin;
    }

    /** このプラグインのメニューの共通部分。 */
    private abstract static class Menu implements InventoryHolder {
        final CasinoTable table;
        Inventory inventory;

        Menu(CasinoTable table) {
            this.table = table;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---- 両替 ----

    private static final class BuyIn extends Menu {
        final int seat;
        final List<Long> options = new ArrayList<>();
        int first;

        BuyIn(CasinoTable table, int seat) {
            super(table);
            this.seat = seat;
        }
    }

    void openBuyIn(Player player, CasinoTable table, int seat) {
        BuyIn holder = new BuyIn(table, seat);
        Bank bank = table.bank();
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text(table.type().displayName() + "：" + (bank.practice() ? "練習用チップを受け取る" : "チップに両替する")));
        holder.inventory = inventory;
        long min = table.minChips();
        if (bank.practice()) {
            long chips = bank.affordable(player);
            holder.options.add(chips);
            holder.first = 13;
            inventory.setItem(13, named(Items.model("chip/c100_5"), Component.text("チップ " + chips + " 枚を受け取る", NamedTextColor.GOLD),
                    List.of(Component.text("練習用なのでお金は使いません", NamedTextColor.GRAY),
                            Component.text("席を立つと回収します", NamedTextColor.GRAY))));
        } else {
            long affordable = bank.affordable(player);
            List<Long> buyIns = table.buyIns();
            int count = Math.min(buyIns.size(), 5);
            holder.first = 13 - count / 2;
            for (int i = 0; i < count; i++) {
                long chips = buyIns.get(i);
                boolean ok = chips <= affordable && chips >= min;
                holder.options.add(chips);
                inventory.setItem(holder.first + i, named(
                        ok ? Items.model("chip/" + icon(chips)) : ItemStack.of(Material.GRAY_DYE),
                        Component.text("チップ " + chips + " 枚", ok ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY),
                        List.of(Component.text(ok ? "クリックで両替して座る" : chips < min ? "この卓には少なすぎます" : "お金が足りません",
                                        ok ? NamedTextColor.GREEN : NamedTextColor.RED),
                                Component.text("両替できる上限: " + affordable + " 枚", NamedTextColor.GRAY))));
            }
        }
        inventory.setItem(26, rulesItem());
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

    // ---- ブラックジャックの賭け金 ----

    private static final int[] BET_CHIP_SLOTS = {10, 11, 12, 13, 14};
    private static final int BET_INFO = 4;
    private static final int BET_CLEAR = 16;
    private static final int BET_CONFIRM = 22;
    private static final int BET_RULES = 18;
    private static final int LEAVE_27 = 26;

    private static final class Bet extends Menu {
        long amount;

        Bet(BlackjackTable table) {
            super(table);
        }

        BlackjackTable blackjack() {
            return (BlackjackTable) table;
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
        for (int i = 0; i < BET_CHIP_SLOTS.length; i++) {
            inventory.setItem(BET_CHIP_SLOTS[i], named(Items.model("chip/c" + CHIP_VALUES[i] + "_1"),
                    Component.text(CHIP_VALUES[i] + " 枚チップ", NamedTextColor.GOLD),
                    List.of(Component.text("左クリック: 足す / 右クリック: 減らす", NamedTextColor.GRAY))));
        }
        inventory.setItem(BET_CLEAR, named(ItemStack.of(Material.BARRIER), Component.text("最低額に戻す", NamedTextColor.RED), List.of()));
        inventory.setItem(BET_RULES, rulesItem());
        inventory.setItem(LEAVE_27, leaveItem());
        renderBet(holder, player);
        player.openInventory(inventory);
        player.sendMessage(Component.text("閉じてしまったら ", NamedTextColor.GRAY)
                .append(Component.text("[賭ける]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/casino bet")))
                .append(Component.text(" で開き直せます", NamedTextColor.GRAY)));
    }

    private void renderBet(Bet holder, Player player) {
        BlackjackTable table = holder.blackjack();
        int seat = table.seatOf(player.getUniqueId());
        long chips = seat < 0 ? 0 : table.seat(seat).chips;
        holder.inventory.setItem(BET_INFO, named(ItemStack.of(Material.PAPER), Component.text("賭け金 " + holder.amount + " 枚", NamedTextColor.YELLOW),
                List.of(Component.text("手元のチップ: " + chips + " 枚", NamedTextColor.GRAY),
                        Component.text("この卓の賭け金: " + table.settings().rules().minBet() + "〜" + table.settings().rules().maxBet() + " 枚", NamedTextColor.GRAY))));
        holder.inventory.setItem(BET_CONFIRM, named(ItemStack.of(Material.LIME_WOOL), Component.text(holder.amount + " 枚で決定", NamedTextColor.GREEN),
                List.of(Component.text("全員が決めるか、締め切りで配り始めます", NamedTextColor.GRAY))));
    }

    private void clickBet(Bet bet, Player player, int slot, ClickType click) {
        BlackjackTable table = bet.blackjack();
        int seat = table.seatOf(player.getUniqueId());
        if (slot == LEAVE_27) {
            player.closeInventory();
            table.leave(player.getUniqueId());
            return;
        }
        if (slot == BET_RULES) {
            player.closeInventory();
            Rulebook.open(player, GameType.BLACKJACK);
            return;
        }
        if (seat < 0 || !table.canBet(seat)) {
            player.closeInventory();
            return;
        }
        long chips = table.seat(seat).chips;
        long min = table.settings().rules().minBet();
        long max = Math.min(table.settings().rules().maxBet(), chips);
        for (int i = 0; i < BET_CHIP_SLOTS.length; i++) {
            if (slot == BET_CHIP_SLOTS[i]) {
                long step = click == ClickType.RIGHT ? -CHIP_VALUES[i] : CHIP_VALUES[i];
                bet.amount = Math.max(min, Math.min(max, bet.amount + step));
                player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.6f);
            }
        }
        if (slot == BET_CLEAR) {
            bet.amount = Math.min(min, chips);
        } else if (slot == BET_CONFIRM) {
            player.closeInventory();
            table.confirmBet(player, bet.amount);
            return;
        }
        renderBet(bet, player);
    }

    // ---- ルーレットの盤 ----

    /** 数字のマスをクリックしたときの賭け方。 */
    enum RouletteMode {
        STRAIGHT("ストレートアップ（1 つ）", "その数字だけ。35 倍", Material.WHITE_BANNER),
        SPLIT_NEXT("スプリット（右隣と）", "その数字と +3 の 2 つ。17 倍", Material.LIGHT_BLUE_BANNER),
        SPLIT_UP("スプリット（上隣と）", "その数字と +1 の 2 つ（同じ列）。17 倍", Material.CYAN_BANNER),
        STREET("ストリート（横 3 つ）", "その数字の列の 3 つ。11 倍", Material.YELLOW_BANNER),
        CORNER("コーナー（4 つ）", "その数字・+1・+3・+4 の 4 つ。8 倍（0 なら 0〜3）", Material.ORANGE_BANNER),
        LINE("ライン（6 つ）", "その数字の列と次の列の 6 つ。5 倍", Material.MAGENTA_BANNER);

        final String label;
        final String help;
        final Material icon;

        RouletteMode(String label, String help, Material icon) {
            this.label = label;
            this.help = help;
            this.icon = icon;
        }
    }

    private static final int R_ZERO = 36;
    private static final int R_MODE_FIRST = 38;
    private static final int R_PAGE = 44;
    private static final int R_CHIP_FIRST = 45;
    private static final int R_INFO = 50;
    private static final int R_CLEAR = 51;
    private static final int R_RULES = 52;
    private static final int R_LEAVE = 53;
    /** 外側の賭けのページの並び。 */
    private static final int[] DOZEN_SLOTS = {2, 4, 6};
    private static final int[] COLUMN_SLOTS = {11, 13, 15};
    private static final int[] OUTSIDE_SLOTS = {19, 20, 21, 23, 24, 25};

    private static final class RouletteMenu extends Menu {
        RouletteMode mode = RouletteMode.STRAIGHT;
        long chip;
        boolean outside;

        RouletteMenu(RouletteTable table) {
            super(table);
        }

        RouletteTable roulette() {
            return (RouletteTable) table;
        }
    }

    void openRoulette(Player player, RouletteTable table) {
        if (table.seatOf(player.getUniqueId()) < 0) {
            return;
        }
        RouletteMenu holder = new RouletteMenu(table);
        holder.chip = table.settings().minBet();
        holder.inventory = Bukkit.createInventory(holder, 54, Component.text("ルーレット：賭ける場所を選ぶ"));
        renderRoulette(holder, player);
        player.openInventory(holder.inventory);
        player.sendMessage(Component.text("閉じてしまったら ", NamedTextColor.GRAY)
                .append(Component.text("[賭ける]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/casino bet")))
                .append(Component.text(" で開き直せます", NamedTextColor.GRAY)));
    }

    /** 回し始めたら、この卓のルーレットの盤を閉じる。 */
    void closeRoulette(RouletteTable table) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof RouletteMenu menu && menu.table == table) {
                player.closeInventory();
            }
        }
    }

    private void renderRoulette(RouletteMenu menu, Player player) {
        RouletteTable table = menu.roulette();
        int seat = table.seatOf(player.getUniqueId());
        Inventory inv = menu.inventory;
        inv.clear();
        List<RouletteBet> mine = seat < 0 ? List.of() : table.round().bets(seat);
        if (menu.outside) {
            for (int d = 1; d <= 3; d++) {
                RouletteBet bet = RouletteBet.dozen(d, menu.chip);
                inv.setItem(DOZEN_SLOTS[d - 1], betItem(Material.PURPLE_CONCRETE, (d - 1) * 12 + 1 + "〜" + d * 12 + "（ダズン）",
                        "12 個。2 倍", bet, mine, 1));
            }
            for (int c = 1; c <= 3; c++) {
                RouletteBet bet = RouletteBet.column(c, menu.chip);
                inv.setItem(COLUMN_SLOTS[c - 1], betItem(Material.BLUE_CONCRETE, "縦の段 " + c + "（" + c + ", " + (c + 3) + ", " + (c + 6) + " …）",
                        "12 個。2 倍", bet, mine, 1));
            }
            for (int i = 0; i < RouletteLayout.OUTSIDE.length; i++) {
                BetType type = RouletteLayout.OUTSIDE[i];
                Material icon = switch (type) {
                    case RED -> Material.RED_CONCRETE;
                    case BLACK -> Material.BLACK_CONCRETE;
                    default -> Material.LIGHT_GRAY_CONCRETE;
                };
                inv.setItem(OUTSIDE_SLOTS[i], betItem(icon, type.displayName(), "18 個。2 倍（0 は負け）",
                        RouletteBet.outside(type, menu.chip), mine, 1));
            }
        } else {
            for (int n = 1; n <= 36; n++) {
                inv.setItem(n - 1, numberItem(n, menu, mine));
            }
            inv.setItem(R_ZERO, numberItem(0, menu, mine));
            RouletteMode[] modes = RouletteMode.values();
            for (int i = 0; i < modes.length; i++) {
                RouletteMode mode = modes[i];
                ItemStack item = named(ItemStack.of(mode.icon), Component.text(mode.label, mode == menu.mode ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                        List.of(Component.text(mode.help, NamedTextColor.GRAY),
                                Component.text(mode == menu.mode ? "選択中" : "クリックで切り替え", mode == menu.mode ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
                if (mode == menu.mode) {
                    item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                }
                inv.setItem(R_MODE_FIRST + i, item);
            }
        }
        inv.setItem(R_PAGE, named(ItemStack.of(menu.outside ? Material.MAP : Material.BOOK),
                Component.text(menu.outside ? "数字の盤に戻る" : "外側の賭け（赤黒・偶奇・ダズンなど）", NamedTextColor.AQUA), List.of()));
        long maxBet = table.settings().maxBet();
        int slot = R_CHIP_FIRST;
        for (long value : CHIP_VALUES) {
            if (value < table.settings().minBet() || value > maxBet) {
                continue;
            }
            ItemStack item = named(Items.model("chip/c" + value + "_" + (value == menu.chip ? 3 : 1)),
                    Component.text(value + " 枚ずつ置く", value == menu.chip ? NamedTextColor.GREEN : NamedTextColor.GOLD),
                    List.of(Component.text(value == menu.chip ? "選択中" : "クリックで切り替え", NamedTextColor.GRAY)));
            inv.setItem(slot++, item);
            if (slot >= R_INFO) {
                break;
            }
        }
        long chips = seat < 0 ? 0 : table.seat(seat).chips;
        long total = seat < 0 ? 0 : table.round().total(seat);
        inv.setItem(R_INFO, named(ItemStack.of(Material.PAPER), Component.text("賭けた合計 " + total + " 枚", NamedTextColor.YELLOW),
                List.of(Component.text("手元のチップ: " + chips + " 枚", NamedTextColor.GRAY),
                        Component.text("1 か所 " + table.settings().minBet() + "〜" + maxBet + " 枚、合計 " + table.settings().maxTotal() + " 枚まで", NamedTextColor.GRAY),
                        Component.text("同じ場所をもう一度クリックすると積み増します", NamedTextColor.GRAY))));
        inv.setItem(R_CLEAR, named(ItemStack.of(Material.BARRIER), Component.text("賭けを全部取り消す", NamedTextColor.RED), List.of()));
        inv.setItem(R_RULES, rulesItem());
        inv.setItem(R_LEAVE, leaveItem());
    }

    private static ItemStack numberItem(int n, RouletteMenu menu, List<RouletteBet> mine) {
        Material material = n == 0 ? Material.LIME_CONCRETE : Wheel.red(n) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
        RouletteBet bet = rouletteBet(n, menu.mode, menu.chip);
        String covers = bet == null ? "この賭け方ではここに置けません" : bet.type().displayName() + "：" + numbers(bet);
        ItemStack item = betItem(material, n + " " + (n == 0 ? "緑" : Wheel.red(n) ? "赤" : "黒"), covers, bet, mine, Math.max(1, n));
        long onThis = 0;
        for (RouletteBet b : mine) {
            if (b.type() == BetType.STRAIGHT && b.anchor() == n) {
                onThis += b.amount();
            }
        }
        if (onThis > 0) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    private static ItemStack betItem(Material material, String name, String help, RouletteBet bet, List<RouletteBet> mine, int count) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(help, NamedTextColor.GRAY));
        if (bet != null) {
            for (RouletteBet b : mine) {
                if (b.samePlace(bet)) {
                    lore.add(Component.text("ここに " + b.amount() + " 枚賭けています", NamedTextColor.GREEN));
                }
            }
            lore.add(Component.text("クリックで " + bet.amount() + " 枚置く", NamedTextColor.YELLOW));
        }
        ItemStack item = named(ItemStack.of(material), Component.text(name, NamedTextColor.WHITE), lore);
        item.setAmount(Math.min(count, 64));
        return item;
    }

    private static String numbers(RouletteBet bet) {
        return String.join("・", bet.numbers().stream().sorted().map(String::valueOf).toList());
    }

    /** 数字のマスとモードから賭けを作る。置けない組み合わせなら null。 */
    static RouletteBet rouletteBet(int n, RouletteMode mode, long amount) {
        try {
            if (n == 0) {
                return mode == RouletteMode.CORNER ? RouletteBet.corner(0, amount) : RouletteBet.straight(0, amount);
            }
            int row = (n - 1) / 3 + 1;
            return switch (mode) {
                case STRAIGHT -> RouletteBet.straight(n, amount);
                case SPLIT_NEXT -> RouletteBet.split(n, n + 3, amount);
                case SPLIT_UP -> RouletteBet.split(n, n + 1, amount);
                case STREET -> RouletteBet.street(row, amount);
                case CORNER -> RouletteBet.corner(n, amount);
                case LINE -> RouletteBet.line(row, amount);
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void clickRoulette(RouletteMenu menu, Player player, int slot) {
        RouletteTable table = menu.roulette();
        int seat = table.seatOf(player.getUniqueId());
        if (seat < 0) {
            player.closeInventory();
            return;
        }
        RouletteBet bet = null;
        if (slot == R_LEAVE) {
            player.closeInventory();
            table.leave(player.getUniqueId());
            return;
        } else if (slot == R_RULES) {
            player.closeInventory();
            Rulebook.open(player, GameType.ROULETTE);
            return;
        } else if (slot == R_CLEAR) {
            table.clearBets(seat);
        } else if (slot == R_PAGE) {
            menu.outside = !menu.outside;
        } else if (slot >= R_CHIP_FIRST && slot < R_INFO) {
            int index = slot - R_CHIP_FIRST;
            int i = 0;
            for (long value : CHIP_VALUES) {
                if (value < table.settings().minBet() || value > table.settings().maxBet()) {
                    continue;
                }
                if (i++ == index) {
                    menu.chip = value;
                }
            }
        } else if (menu.outside) {
            for (int i = 0; i < 3; i++) {
                if (slot == DOZEN_SLOTS[i]) {
                    bet = RouletteBet.dozen(i + 1, menu.chip);
                } else if (slot == COLUMN_SLOTS[i]) {
                    bet = RouletteBet.column(i + 1, menu.chip);
                }
            }
            for (int i = 0; i < OUTSIDE_SLOTS.length; i++) {
                if (slot == OUTSIDE_SLOTS[i]) {
                    bet = RouletteBet.outside(RouletteLayout.OUTSIDE[i], menu.chip);
                }
            }
        } else if (slot < 36 || slot == R_ZERO) {
            int n = slot == R_ZERO ? 0 : slot + 1;
            bet = rouletteBet(n, menu.mode, menu.chip);
            if (bet == null) {
                player.sendMessage(Component.text("その数字には「" + menu.mode.label + "」で置けません", NamedTextColor.RED));
            }
        } else if (slot >= R_MODE_FIRST && slot < R_MODE_FIRST + RouletteMode.values().length) {
            menu.mode = RouletteMode.values()[slot - R_MODE_FIRST];
        }
        if (bet != null) {
            String error = table.place(seat, bet);
            if (error != null) {
                player.sendMessage(Component.text(error, NamedTextColor.RED));
            }
        }
        if (!table.canBet()) {
            player.closeInventory();
            return;
        }
        renderRoulette(menu, player);
    }

    // ---- ポーカーのレイズ ----

    private static final int[] RAISE_CHIP_SLOTS = {10, 11, 12, 13, 14};
    private static final int RAISE_INFO = 4;
    private static final int RAISE_MIN = 15;
    private static final int RAISE_POT = 16;
    private static final int RAISE_CONFIRM = 22;
    private static final int RAISE_ALLIN = 24;

    private static final class Raise extends Menu {
        long amount;

        Raise(PokerTable table) {
            super(table);
        }

        PokerTable poker() {
            return (PokerTable) table;
        }
    }

    void openRaise(Player player, PokerTable table) {
        Legal legal = table.legalFor(player);
        if (legal == null || !legal.canRaise()) {
            if (legal != null) {
                player.sendMessage(Component.text("今はレイズできません", NamedTextColor.RED));
            }
            return;
        }
        Raise holder = new Raise(table);
        holder.amount = legal.minRaiseTo();
        holder.inventory = Bukkit.createInventory(holder, 27, Component.text(legal.streetBet() + legal.toCall() == 0 ? "ベットする額" : "レイズする額"));
        for (int i = 0; i < RAISE_CHIP_SLOTS.length; i++) {
            holder.inventory.setItem(RAISE_CHIP_SLOTS[i], named(Items.model("chip/c" + CHIP_VALUES[i] + "_1"),
                    Component.text(CHIP_VALUES[i] + " 枚", NamedTextColor.GOLD),
                    List.of(Component.text("左クリック: 足す / 右クリック: 減らす", NamedTextColor.GRAY))));
        }
        holder.inventory.setItem(RAISE_MIN, named(ItemStack.of(Material.IRON_NUGGET), Component.text("最低額 " + legal.minRaiseTo(), NamedTextColor.WHITE), List.of()));
        holder.inventory.setItem(RAISE_POT, named(ItemStack.of(Material.GOLD_NUGGET), Component.text("ポット " + table.potRaise(legal), NamedTextColor.GOLD), List.of()));
        holder.inventory.setItem(RAISE_ALLIN, named(ItemStack.of(Material.TNT), Component.text("オールイン（" + (legal.maxRaiseTo() - legal.streetBet()) + " 枚）", NamedTextColor.DARK_RED), List.of()));
        holder.inventory.setItem(18, rulesItem());
        renderRaise(holder, legal, table);
        player.openInventory(holder.inventory);
    }

    private void renderRaise(Raise holder, Legal legal, PokerTable table) {
        holder.inventory.setItem(RAISE_INFO, named(ItemStack.of(Material.PAPER), Component.text("このラウンドの合計 " + holder.amount + " 枚", NamedTextColor.YELLOW),
                List.of(Component.text("今回出す分: " + (holder.amount - legal.streetBet()) + " 枚", NamedTextColor.GRAY),
                        Component.text("出せる範囲: " + legal.minRaiseTo() + "〜" + legal.maxRaiseTo(), NamedTextColor.GRAY),
                        Component.text("ポット: " + table.pot() + " 枚", NamedTextColor.GRAY))));
        holder.inventory.setItem(RAISE_CONFIRM, named(ItemStack.of(Material.LIME_WOOL), Component.text("合計 " + holder.amount + " 枚で決定", NamedTextColor.GREEN), List.of()));
    }

    private void clickRaise(Raise raise, Player player, int slot, ClickType click) {
        PokerTable table = raise.poker();
        Legal legal = table.legalFor(player);
        if (slot == 18) {
            player.closeInventory();
            Rulebook.open(player, GameType.POKER);
            return;
        }
        if (legal == null || !legal.canRaise()) {
            player.closeInventory();
            return;
        }
        for (int i = 0; i < RAISE_CHIP_SLOTS.length; i++) {
            if (slot == RAISE_CHIP_SLOTS[i]) {
                long step = click == ClickType.RIGHT ? -CHIP_VALUES[i] : CHIP_VALUES[i];
                raise.amount = Math.max(legal.minRaiseTo(), Math.min(legal.maxRaiseTo(), raise.amount + step));
                player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.6f);
            }
        }
        if (slot == RAISE_MIN) {
            raise.amount = legal.minRaiseTo();
        } else if (slot == RAISE_POT) {
            raise.amount = table.potRaise(legal);
        } else if (slot == RAISE_ALLIN) {
            player.closeInventory();
            table.allIn(player);
            return;
        } else if (slot == RAISE_CONFIRM) {
            player.closeInventory();
            table.raiseTo(player, raise.amount);
            return;
        }
        renderRaise(raise, legal, table);
    }

    // ---- ポーカーの席（自分の番ではないとき） ----

    private static final class SeatInfo extends Menu {
        SeatInfo(CasinoTable table) {
            super(table);
        }
    }

    void openSeatInfo(Player player, PokerTable table) {
        int seat = table.seatOf(player.getUniqueId());
        if (seat < 0) {
            return;
        }
        SeatInfo holder = new SeatInfo(table);
        holder.inventory = Bukkit.createInventory(holder, 27, Component.text("ポーカー"));
        CasinoTable.Seat s = table.seat(seat);
        holder.inventory.setItem(13, named(ItemStack.of(Material.PAPER), Component.text("手元のチップ " + (s.chips + s.atRisk) + " 枚", NamedTextColor.YELLOW),
                List.of(Component.text("ブラインド " + table.settings().smallBlind() + "/" + table.settings().bigBlind(), NamedTextColor.GRAY),
                        Component.text("自分の番になるとチャットにボタンが出ます", NamedTextColor.GRAY))));
        holder.inventory.setItem(18, rulesItem());
        holder.inventory.setItem(LEAVE_27, leaveItem());
        player.openInventory(holder.inventory);
    }

    // ---- クリック ----

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        switch (menu) {
            case BuyIn buyIn -> {
                if (slot == 26) {
                    player.closeInventory();
                    Rulebook.open(player, buyIn.table.type());
                    return;
                }
                int index = slot - buyIn.first;
                if (index < 0 || index >= buyIn.options.size()) {
                    return;
                }
                player.closeInventory();
                plugin.tables().sit(player, buyIn.table, buyIn.seat, buyIn.options.get(index));
            }
            case Bet bet -> clickBet(bet, player, slot, event.getClick());
            case RouletteMenu roulette -> clickRoulette(roulette, player, slot);
            case Raise raise -> clickRaise(raise, player, slot, event.getClick());
            case SeatInfo info -> {
                if (slot == 18) {
                    player.closeInventory();
                    Rulebook.open(player, GameType.POKER);
                } else if (slot == LEAVE_27) {
                    player.closeInventory();
                    info.table.leave(player.getUniqueId());
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    private static ItemStack rulesItem() {
        return named(ItemStack.of(Material.WRITTEN_BOOK), Component.text("ルールブック", NamedTextColor.AQUA),
                List.of(Component.text("遊び方と配当を読む", NamedTextColor.GRAY)));
    }

    private static ItemStack leaveItem() {
        return named(ItemStack.of(Material.OAK_DOOR), Component.text("席を立つ", NamedTextColor.GRAY),
                List.of(Component.text("チップを精算して席を離れます", NamedTextColor.GRAY)));
    }

    static ItemStack named(ItemStack item, Component name, List<Component> lore) {
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        });
        return item;
    }
}
