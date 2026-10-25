package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.slot.Symbol;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * リソースパックのフォント（tools/gen_font.py）の文字。ルールブックの絵とメニューの背景に使う。
 * パックを読み込めた Java 版の人だけに出し、統合版やパックを断った人には今までどおり文字だけで出す。
 */
final class Glyphs {

    private static final Key ICONS = Key.key("luckycasino", "icons");
    private static final Key MENU = Key.key("luckycasino", "menu");
    private static final char ICON_SLOT = '';
    private static final char ICON_CARD = '';
    private static final char MENU_27 = '';
    private static final char MENU_54 = '';
    /** 文字送りを左へ 8 戻す（タイトルの書き始めからメニューの左端へ）。 */
    private static final char SPACE_TO_EDGE = '';
    /** 背景を描いたあと、タイトルの書き始めへ戻す。 */
    private static final char SPACE_BACK = '';

    /** ResourcePackListener が、パックを読み込めた人を入れる。 */
    private static final Set<UUID> LOADED = ConcurrentHashMap.newKeySet();

    private Glyphs() {
    }

    static void packLoaded(UUID player, boolean loaded) {
        if (loaded) {
            LOADED.add(player);
        } else {
            LOADED.remove(player);
        }
    }

    /** この人に絵の文字を出せるか（パックを読み込めた Java 版の人）。 */
    static boolean enabled(Player player) {
        return LOADED.contains(player.getUniqueId()) && !Bedrock.is(player);
    }

    /** メニューのタイトル。絵を出せる人には背景を重ね、文字は背景の札の上に書く。 */
    static Component menuTitle(Player player, int size, Component title) {
        if (!enabled(player) || (size != 27 && size != 54)) {
            return title;
        }
        String bg = "" + SPACE_TO_EDGE + (size == 27 ? MENU_27 : MENU_54) + SPACE_BACK;
        // 背景は白で描く（文字の色がそのまま掛かるため）。タイトルは子にするとフォントを受け継ぐので並べる
        return Component.textOfChildren(Component.text(bg, NamedTextColor.WHITE).font(MENU), title);
    }

    static Component slot(Symbol symbol) {
        return icon((char) (ICON_SLOT + symbol.ordinal()));
    }

    static Component card(Card card) {
        return icon((char) (ICON_CARD + card.code() - 1));
    }

    private static Component icon(char c) {
        return Component.text(String.valueOf(c), NamedTextColor.WHITE).font(ICONS);
    }
}
