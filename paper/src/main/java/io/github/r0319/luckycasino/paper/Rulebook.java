package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.slot.Symbol;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** ゲームごとのルールブック（本を開いて読む）。1 ページは短めに。 */
final class Rulebook {

    private Rulebook() {
    }

    static void open(Player player, GameType type) {
        player.openBook(Book.book(Component.text(type.displayName() + "のルール"), Component.text("LuckyCasino"),
                pages(type, Glyphs.enabled(player))));
    }

    /** ルールブックのページ。pictures はリソースパックの絵を出せる人（PictureRules）。 */
    static List<Component> pages(GameType type, boolean pictures) {
        List<Component> pages = new ArrayList<>();
        pages.add(pictures ? PictureRules.seats() : common());
        pages.addAll(switch (type) {
            case BLACKJACK -> pictures ? PictureRules.blackjack() : blackjack();
            case ROULETTE -> pictures ? PictureRules.roulette() : roulette();
            case POKER -> poker(pictures);
            case SLOT -> slot(pictures);
            case BACCARAT -> pictures ? PictureRules.baccarat() : GameRules.baccarat();
            case BIGWHEEL -> pictures ? PictureRules.bigWheel() : GameRules.bigWheel();
            case CHINCHIRO -> pictures ? PictureRules.chinchiro() : GameRules.chinchiro();
            case HIGHLOW -> pictures ? PictureRules.highLow() : GameRules.highLow();
            case HORSERACE -> pictures ? PictureRules.horseRace() : GameRules.horseRace();
        });
        return pages;
    }

    static Component page(String title, String... lines) {
        Component page = Component.text(title, NamedTextColor.DARK_BLUE).decorate(TextDecoration.BOLD);
        for (String line : lines) {
            page = page.append(Component.newline()).append(Component.text(line, NamedTextColor.BLACK).decoration(TextDecoration.BOLD, false));
        }
        return page;
    }

    private static Component common() {
        return page("席とチップ",
                "椅子をクリックして座り、チップに両替します。",
                "",
                "スニークで降りても席とチップは残り、8 ブロック以内なら歩けます。椅子のクリックで座り直し。",
                "",
                "メニューの「席を立つ」でお金に戻ります（勝負の途中なら終わってから）。");
    }

    private static List<Component> blackjack() {
        return List.of(
                page("ブラックジャック",
                        "ディーラーより 21 に近づけたら勝ち。21 を超えたらバースト（負け）。",
                        "",
                        "2〜10 はその数、J・Q・K は 10、A は 1 か 11。"),
                page("行動",
                        "ヒット: もう 1 枚",
                        "スタンド: 止める",
                        "ダブルダウン: 賭け金を倍にして 1 枚だけ引く",
                        "スプリット: 同じ数の 2 枚を 2 つの手に分ける（同額を追加）"),
                page("ディーラーと配当",
                        "ディーラーは 17 以上になるまで引きます。",
                        "",
                        "勝ち: 2 倍戻る",
                        "ブラックジャック（最初の 2 枚で 21）: 2.5 倍戻る",
                        "引き分け: 賭け金が戻る"));
    }

    private static List<Component> roulette() {
        return List.of(
                page("ルーレット",
                        "0〜36 のどこにボールが止まるかを当てます（ヨーロピアン、0 は 1 つ）。",
                        "",
                        "メニューで赤黒・ダズンなどをクリックして賭け、締め切りで回ります。本のアイコンで数字にも賭けられます。何か所にも置けます。"),
                page("立って歩く",
                        "スニークで立っても精算しません。卓から 8 ブロック以内なら歩いてホイールを見られます。",
                        "",
                        "テーブルのクリックで賭けのメニュー、自分の椅子のクリックで座り直し。離れすぎると精算します。"),
                page("配当（賭け金は別に戻る）",
                        "1 つ: 35 倍",
                        "2 つ（スプリット）: 17 倍",
                        "3 つ（ストリート）: 11 倍",
                        "4 つ（コーナー）: 8 倍",
                        "6 つ（ライン）: 5 倍",
                        "ダズン・縦の段: 2 倍",
                        "赤黒・偶奇・前半後半: 1 倍"),
                page("0 のとき",
                        "0 に止まると、赤黒・偶奇・前半後半・ダズン・縦の段はすべて負けです。",
                        "",
                        "0 を含む賭け（0 そのもの、0 と 1〜3）は当たりです。"));
    }

    private static List<Component> slot(boolean pictures) {
        List<Component> pages = new ArrayList<>(List.of(
                page("スロット",
                        "1 人用。1 回の賭け金を決めて、台の右のレバーをクリックすると回ります。",
                        "",
                        "左のリールから順に止まり、窓に見える 3 段の絵柄で配当が決まります。"),
                page("5 ライン",
                        "横 3 本（上・中・下の段）と斜め 2 本（✕ の形）の 5 ラインを全部見ます。",
                        "",
                        "賭け金は 5 ラインに等分します（1 回 10 枚なら 1 ライン 2 枚）。",
                        "",
                        "当たったラインが光り、その分を合計して戻します。")));
        if (pictures) {
            pages.add(picturePage("配当（1 ラインの何倍か）",
                    slotRow(Symbol.SEVEN, Symbol.SEVEN, Symbol.SEVEN, "300 倍"),
                    slotRow(Symbol.BAR, Symbol.BAR, Symbol.BAR, "50 倍"),
                    slotRow(Symbol.BELL, Symbol.BELL, Symbol.BELL, "20 倍"),
                    slotRow(Symbol.MELON, Symbol.MELON, Symbol.MELON, "15 倍"),
                    slotRow(Symbol.LEMON, Symbol.LEMON, Symbol.LEMON, "10 倍")));
            pages.add(picturePage("チェリー（左から数える）",
                    slotRow(Symbol.CHERRY, Symbol.CHERRY, Symbol.CHERRY, "20 倍"),
                    slotRow(Symbol.CHERRY, Symbol.CHERRY, null, "4 倍"),
                    slotRow(Symbol.CHERRY, null, null, "1 倍"),
                    Component.text("？ は何でも", NamedTextColor.DARK_GRAY),
                    Component.text("（設定で変わります。今の配当は台のメニューの紙に出ます）", NamedTextColor.DARK_GRAY)));
            return pages;
        }
        pages.add(page("配当（1 ラインの何倍か）",
                        "7 7 7: 300 倍",
                        "BAR 3 つ: 50 倍",
                        "ベル 3 つ: 20 倍",
                        "スイカ 3 つ: 15 倍",
                        "レモン 3 つ: 10 倍",
                        "チェリー 3 つ: 20 倍",
                        "チェリー左から 2 つ: 4 倍",
                        "チェリー左に 1 つ: 1 倍",
                        "（設定で変わります。今の配当は台のメニューの紙に出ます）"));
        return pages;
    }

    private static List<Component> poker(boolean pictures) {
        List<Component> pages = new ArrayList<>(List.of(
                page("ポーカー",
                        "テキサスホールデム（ノーリミット）。ディーラーは配るだけで、チップはプレイヤーの間で動きます。",
                        "",
                        "手札 2 枚と場の 5 枚から、一番強い 5 枚で勝負します。"),
                pictures ? PictureRules.page("流れ（毎回賭ける）",
                        PictureRules.row("手札（自分だけ見える）"),
                        PictureRules.cards("AS KD"),
                        PictureRules.BLANK,
                        PictureRules.row("フロップ（場に 3 枚）"),
                        PictureRules.cards("QH 7C 2S"),
                        PictureRules.BLANK,
                        PictureRules.row("ターン（もう 1 枚）"),
                        PictureRules.cards("QH 7C 2S JD"),
                        PictureRules.BLANK,
                        PictureRules.row("リバー（最後の 1 枚）"),
                        PictureRules.cards("QH 7C 2S JD TH"),
                        PictureRules.BLANK,
                        PictureRules.note("強い役の人がポットを取る"))
                        : page("流れ",
                        "ブラインド → 手札 2 枚",
                        "→ フロップ（場に 3 枚）",
                        "→ ターン（1 枚）",
                        "→ リバー（1 枚）",
                        "→ ショーダウン",
                        "",
                        "各ラウンドで賭けます。最後まで残った人か、役が一番強い人がポットをもらいます。"),
                page("行動",
                        "チェック: 賭けずに回す",
                        "ベット/レイズ: 額を上げる",
                        "コール: 同じ額を出す",
                        "フォールド: 降りる",
                        "オールイン: 全部出す",
                        "",
                        "自分の番になるとチャットにボタンが出ます。")));
        if (pictures) {
            pages.add(picturePage("役（強い順）1",
                    hand("ロイヤルフラッシュ", "最強", "AS KS QS JS TS"),
                    hand("ストレートフラッシュ", "同マーク連番", "9H 8H 7H 6H 5H"),
                    hand("フォーカード", "同じ数4枚", "KS KH KD KC 3S"),
                    hand("フルハウス", "3枚＋2枚", "QS QH QD 7C 7S")));
            pages.add(picturePage("役（強い順）2",
                    hand("フラッシュ", "同マーク5枚", "AD JD 8D 6D 2D"),
                    hand("ストレート", "数が連番", "TC 9D 8S 7H 6C"),
                    hand("スリーカード", "同じ数3枚", "7S 7H 7D KC 2S"),
                    hand("ツーペア", "ペア2組", "JS JH 4D 4C 9S")));
            pages.add(picturePage("役（強い順）3",
                    hand("ワンペア", "ペア1組", "AH AC 9D 6S 3H"),
                    hand("ハイカード", "役なし", "AS QD 9C 6H 3S"),
                    Component.text("同じ役なら、数の大きいほうが勝ち（A が一番強い）", NamedTextColor.DARK_GRAY)));
            return pages;
        }
        pages.add(page("役（強い順）",
                        "ロイヤルフラッシュ",
                        "ストレートフラッシュ",
                        "フォーカード",
                        "フルハウス",
                        "フラッシュ",
                        "ストレート",
                        "スリーカード",
                        "ツーペア",
                        "ワンペア",
                        "ハイカード"));
        return pages;
    }

    /** 絵の入ったページ。絵は行より背が高いので、項目の間を 1 行あける。 */
    private static Component picturePage(String title, Component... items) {
        Component page = Component.text(title, NamedTextColor.DARK_BLUE).decorate(TextDecoration.BOLD);
        for (Component item : items) {
            page = page.append(Component.newline()).append(Component.newline()).append(item.decoration(TextDecoration.BOLD, false));
        }
        return page;
    }

    /** スロットの絵柄 3 つと配当。null は「何でも」。 */
    private static Component slotRow(Symbol a, Symbol b, Symbol c, String pay) {
        Component row = Component.empty();
        for (Symbol symbol : new Symbol[]{a, b, c}) {
            row = row.append(symbol == null ? Component.text(" ？ ", NamedTextColor.DARK_GRAY) : Glyphs.slot(symbol));
        }
        return row.append(Component.text(" " + pay, NamedTextColor.BLACK));
    }

    /** ポーカーの役の名前と、その例の 5 枚（"AS KH TD" のように、ランク + スート S/H/D/C）。 */
    private static Component hand(String name, String hint, String cards) {
        Component row = Component.text(name, NamedTextColor.BLACK).append(Component.newline()).append(PictureRules.cards(cards));
        // 本の 1 行は 114 px。カード 5 枚（1 枚 10 px）と短い説明が収まるように詰める
        return row.append(Component.text(" " + hint, NamedTextColor.DARK_GRAY));
    }
}
