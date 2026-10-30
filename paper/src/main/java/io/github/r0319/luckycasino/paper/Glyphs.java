package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bigwheel.BigWheel;
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

    /** ルールブックの小さな絵（tools/gen_rule_art.py）。 */
    enum Pic {
        BOWL(0xE016), R_STRAIGHT(0xE040), R_SPLIT(0xE041), R_STREET(0xE042), R_CORNER(0xE043), R_LINE(0xE044),
        R_DOZEN(0xE045), R_COLUMN(0xE046), R_RED(0xE047), R_ZERO(0xE048), PLAYER(0xE050), BANKER(0xE051), TIE(0xE052),
        HIGH(0xE058), LOW(0xE059), SAME(0xE05A), CHAIR(0xE060), CHIP(0xE061), COIN(0xE062), SNEAK(0xE063),
        CLICK(0xE064), CLOCK(0xE065), HIT(0xE066), STAND(0xE067), DOUBLE(0xE068), SPLIT(0xE069), ARROW(0xE06A),
        CARD_BACK(0xE140);

        private final char code;

        Pic(int code) {
            this.code = (char) code;
        }
    }

    static Component pic(Pic pic) {
        return icon(pic.code);
    }

    /** サイコロの目（1〜6）。 */
    static Component die(int face) {
        return icon((char) (0xE010 + face - 1));
    }

    static Component wheel(BigWheel.Segment segment) {
        return icon((char) (0xE020 + segment.ordinal()));
    }

    /** 競馬の枠（0〜5）。 */
    static Component silk(int horse) {
        return icon((char) (0xE030 + horse));
    }

    private static Component icon(char c) {
        return Component.text(String.valueOf(c), NamedTextColor.WHITE).font(ICONS);
    }
}
