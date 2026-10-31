package io.github.r0319.luckycasino.paper;

import com.google.gson.JsonObject;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * カジノの実績。進捗の画面に「LuckyCasino」のタブを足す。中身はプラグインが起動のたびに読み込み、
 * ディスクには書かない（/minecraft:reload で消えるので、読み込み直しのあとにもう一度読み込む）。
 * 解除はプラグインから grant で行う（条件は minecraft:impossible なので、ほかの方法では解除されない）。
 */
final class Achievements implements Listener {

    enum Frame {
        TASK, GOAL, CHALLENGE
    }

    /** 実績の一覧。parent が null のものがタブの根。 */
    enum Achievement {
        FIRST_PLAY("first_play", null, "achievement/first_play", "カジノデビュー", "初めてカジノで勝負をする", Frame.TASK),

        BLACKJACK("blackjack/natural", FIRST_PLAY, "achievement/blackjack", "ブラックジャック！", "最初の 2 枚で 21 を作って勝つ", Frame.TASK),
        TRIPLE_SEVEN_21("blackjack/triple_seven", BLACKJACK, "achievement/triple_seven", "セブン・セブン・セブン", "7 を 3 枚だけで 21 を作る", Frame.CHALLENGE),
        FIVE_CARD_21("blackjack/five_card", BLACKJACK, "achievement/five_card", "ちりも積もれば 21", "5 枚以上のカードで 21 を作って勝つ", Frame.GOAL),

        STRAIGHT_UP("roulette/straight_up", FIRST_PLAY, "achievement/straight_up", "狙い撃ち", "ルーレットで数字 1 つだけの賭け（ストレートアップ）を当てる", Frame.TASK),
        GREEN_ZERO("roulette/zero", STRAIGHT_UP, "achievement/green_zero", "緑の奇跡", "ルーレットで 0 に賭けて当てる", Frame.GOAL),

        FOUR_OF_A_KIND("poker/four_of_a_kind", FIRST_PLAY, "achievement/four_of_a_kind", "フォーカード", "ポーカーのショーダウンでフォーカードを見せる", Frame.GOAL),
        STRAIGHT_FLUSH("poker/straight_flush", FOUR_OF_A_KIND, "achievement/straight_flush", "ストレートフラッシュ", "ポーカーのショーダウンでストレートフラッシュを見せる", Frame.CHALLENGE),
        ROYAL_FLUSH("poker/royal_flush", STRAIGHT_FLUSH, "achievement/royal_flush", "ロイヤルストレートフラッシュ", "ポーカーのショーダウンでロイヤルストレートフラッシュを見せる", Frame.CHALLENGE),

        SLOT_BARS("slot/bars", FIRST_PLAY, "achievement/slot_bars", "BAR そろい", "スロットで BAR を 3 つそろえる", Frame.GOAL),
        SLOT_SEVENS("slot/sevens", SLOT_BARS, "achievement/slot_sevens", "ラッキー 777", "スロットで 7 を 3 つそろえる", Frame.CHALLENGE),

        BACCARAT_TIE("baccarat/tie", FIRST_PLAY, "achievement/baccarat_tie", "痛み分け", "バカラでタイ（引き分け）に賭けて当てる", Frame.GOAL),
        BACCARAT_PAIR("baccarat/pair", BACCARAT_TIE, "achievement/baccarat_pair", "ペアの予感", "バカラでペアに賭けて当てる", Frame.GOAL),

        BIGWHEEL_STAR("bigwheel/star", FIRST_PLAY, "achievement/bigwheel_star", "1/54 の奇跡", "ビッグホイールでジョーカーかロゴに賭けて当てる", Frame.CHALLENGE),

        CHINCHIRO_PINZORO("chinchiro/pinzoro", FIRST_PLAY, "achievement/chinchiro_pinzoro", "ピンゾロ", "チンチロで自分が振ってピンゾロ（1・1・1）を出す", Frame.CHALLENGE),

        HIGHLOW_SAME("highlow/same", FIRST_PLAY, "achievement/highlow_same", "以心伝心", "ハイ＆ローで「同じ」に賭けて当てる", Frame.GOAL),

        HORSERACE_LONGSHOT("horserace/longshot", FIRST_PLAY, "achievement/horserace_longshot", "大穴的中", "競馬で 8 倍以上の馬の単勝を当てる", Frame.CHALLENGE),

        HOLDEM_FULL_HOUSE("holdem/full_house", FIRST_PLAY, "achievement/holdem_full_house", "満員御礼", "カジノホールデムでフルハウス以上の手でディーラーに勝つ", Frame.GOAL);

        final String path;
        final Achievement parent;
        final String icon;
        final String title;
        final String description;
        final Frame frame;

        Achievement(String path, Achievement parent, String icon, String title, String description, Frame frame) {
            this.path = path;
            this.parent = parent;
            this.icon = icon;
            this.title = title;
            this.description = description;
            this.frame = frame;
        }

        NamespacedKey key() {
            return new NamespacedKey("luckycasino", path);
        }
    }

    private static final String CRITERION = "unlocked";

    private final Logger logger;

    Achievements(Logger logger) {
        this.logger = logger;
    }

    /** まだ読み込まれていなければ読み込む。 */
    @SuppressWarnings("deprecation")
    void register() {
        Map<Key, String> missing = new LinkedHashMap<>();
        for (Achievement a : Achievement.values()) {
            if (Bukkit.getAdvancement(a.key()) == null) {
                missing.put(a.key(), json(a));
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        try {
            int loaded = Bukkit.getUnsafe().loadAdvancements(missing, false).size();
            if (loaded != missing.size()) {
                logger.warning(L10n.plain("実績を %s 個中 %s 個しか読み込めませんでした", missing.size(), loaded));
            }
        } catch (Exception e) {
            logger.warning(L10n.plain("実績を読み込めません: %s", e));
        }
    }

    @EventHandler
    public void onReload(ServerResourcesReloadedEvent event) {
        register();
    }

    /** 実績を解除する（親がまだならいっしょに）。もう解除していれば何もしない。 */
    void grant(Player player, Achievement achievement) {
        for (Achievement a = achievement; a != null; a = a.parent) {
            Advancement advancement = Bukkit.getAdvancement(a.key());
            if (advancement != null) {
                player.getAdvancementProgress(advancement).awardCriteria(CRITERION);
            }
        }
    }

    private static String json(Achievement a) {
        JsonObject icon = new JsonObject();
        icon.addProperty("id", "minecraft:paper");
        JsonObject components = new JsonObject();
        components.addProperty("minecraft:item_model", "luckycasino:" + a.icon);
        icon.add("components", components);

        JsonObject display = new JsonObject();
        display.add("icon", icon);
        display.add("title", text(a.title));
        display.add("description", text(a.description));
        display.addProperty("frame", a.frame.name().toLowerCase(java.util.Locale.ROOT));
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", true);
        display.addProperty("hidden", false);
        if (a.parent == null) {
            display.addProperty("background", "minecraft:block/green_wool");
        }

        JsonObject trigger = new JsonObject();
        trigger.addProperty("trigger", "minecraft:impossible");
        JsonObject criteria = new JsonObject();
        criteria.add(CRITERION, trigger);

        JsonObject root = new JsonObject();
        if (a.parent != null) {
            root.addProperty("parent", a.parent.key().toString());
        }
        root.add("display", display);
        root.add("criteria", criteria);
        return root.toString();
    }

    /** 見る人の言語で出る文（パックが無ければサーバーの既定の言語）。 */
    private static JsonObject text(String value) {
        JsonObject text = new JsonObject();
        text.addProperty("translate", value);
        text.addProperty("fallback", L10n.format(L10n.serverLanguage(), value));
        return text;
    }
}
