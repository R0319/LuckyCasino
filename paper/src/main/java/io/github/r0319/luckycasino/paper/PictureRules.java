package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bigwheel.BigWheel.Segment;
import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Rank;
import io.github.r0319.luckycasino.card.Suit;
import io.github.r0319.luckycasino.paper.Glyphs.Pic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;

import static io.github.r0319.luckycasino.paper.Glyphs.die;
import static io.github.r0319.luckycasino.paper.Glyphs.pic;
import static io.github.r0319.luckycasino.paper.Glyphs.silk;
import static io.github.r0319.luckycasino.paper.Glyphs.wheel;

/**
 * 絵を出せる人（パックを読み込めた Java 版）向けのルールブックのページ。絵を主にして、文字は短くする。
 * 本の 1 ページは 14 行、1 行は 114 px（かな・漢字はおよそ 12 文字）。カードとルーレットの盤は 1 行より背が高いので、
 * その行の下は 1 行あける（BLANK）。見本は tools/preview/rulebook.py で描ける。
 */
final class PictureRules {

    static final Component BLANK = Component.empty();

    private PictureRules() {
    }

    /** 1 行ずつ並べたページ。BLANK は空行。 */
    static Component page(String title, Component... lines) {
        Component page = Component.text(title, NamedTextColor.DARK_BLUE).decorate(TextDecoration.BOLD);
        for (Component line : lines) {
            page = page.append(Component.newline()).append(line.decoration(TextDecoration.BOLD, false));
        }
        return page;
    }

    /** 絵と文字を 1 行に並べる。文字は黒、" " で始まる文字の前には空白が入る。 */
    static Component row(Object... parts) {
        Component row = Component.empty();
        for (Object part : parts) {
            row = row.append(part instanceof Component c ? c : Component.text(part.toString(), NamedTextColor.BLACK));
        }
        return row;
    }

    static Component note(String text) {
        return Component.text(text, NamedTextColor.DARK_GRAY);
    }

    static Component label(String text) {
        return Component.text(text, NamedTextColor.DARK_BLUE);
    }

    /** "AS KH TD" のようなカード（ランク + スート S/H/D/C、?? は裏）。 */
    static Component cards(String codes) {
        Component row = Component.empty();
        for (String code : codes.split(" ")) {
            if (code.equals("??")) {
                row = row.append(pic(Pic.CARD_BACK));
                continue;
            }
            Rank rank = Rank.values()["A23456789TJQK".indexOf(code.charAt(0))];
            Suit suit = Suit.values()["SHDC".indexOf(code.charAt(1))];
            row = row.append(Glyphs.card(new Card(suit, rank)));
        }
        return row;
    }

    static Component dice(int a, int b, int c) {
        return row(die(a), die(b), die(c));
    }

    static Component seats() {
        return page("席とチップ",
                row(pic(Pic.CHAIR), " 椅子をクリックで座る"),
                row(pic(Pic.ARROW), pic(Pic.CHIP), " チップに両替"),
                BLANK,
                row(pic(Pic.SNEAK), " スニークで席を立つ"),
                row(pic(Pic.ARROW), pic(Pic.COIN), " 残りはお金に戻る"),
                BLANK,
                note("勝負の途中で立ったときは、勝負が終わってから精算します（手番は自動で進みます）。"));
    }

    /** 賭け場所を選ぶゲームの共通のページ。 */
    private static Component standing() {
        return page("賭け方",
                row(pic(Pic.CLICK), " メニューで場所を"),
                row("   クリックして置く"),
                row(pic(Pic.CHIP), " 何か所にも置ける"),
                row(pic(Pic.CLOCK), " 締め切りで勝負"),
                BLANK,
                row(pic(Pic.SNEAK), " 賭けたあとは立って"),
                row("   歩いても精算しない"),
                row(pic(Pic.CHAIR), " 椅子のクリックで"),
                row("   座り直す"),
                BLANK,
                note("テーブルのクリックで賭けのメニューが開きます。"));
    }

    static List<Component> blackjack() {
        return List.of(
                page("ブラックジャック",
                        row("21 に近いほうが勝ち"),
                        row(cards("TS 9H"), " 19"),
                        BLANK,
                        label("数え方"),
                        row(cards("AH"), " 1 か 11"),
                        BLANK,
                        row(cards("TC JD QS KH"), " 10"),
                        BLANK,
                        row(cards("KS 8D 5C"), " 23 負け"),
                        BLANK,
                        note("21 を超えたらバースト")),
                page("行動",
                        row(pic(Pic.HIT), " ヒット もう 1 枚"),
                        row(pic(Pic.STAND), " スタンド 止める"),
                        row(pic(Pic.DOUBLE), " ダブルダウン"),
                        note("   賭けを 2 倍に、1 枚だけ"),
                        row(pic(Pic.SPLIT), " スプリット"),
                        note("   同じ数の 2 枚を分ける"),
                        BLANK,
                        label("ディーラー"),
                        row(cards("TD 6S"), " 16 は引く"),
                        BLANK,
                        row(cards("TD 7S"), " 17 で止まる"),
                        BLANK),
                page("配当（戻る額）",
                        row("ブラックジャック 2.5 倍"),
                        row(cards("AS KH"), note(" 最初の 2 枚で 21")),
                        BLANK,
                        row("勝ち 2 倍"),
                        row(cards("TS 9H"), " ＞ ", cards("TC 7D")),
                        BLANK,
                        row("引き分け 賭け金が戻る"),
                        row(cards("TS 8H"), " ＝ ", cards("9C 9D")),
                        BLANK,
                        row("負け 戻らない"),
                        row(cards("TS 6H"), " ＜ ", cards("TC 8D")),
                        BLANK));
    }

    static List<Component> roulette() {
        return List.of(
                page("ルーレット",
                        row("0〜36 のどこにボールが止まるか当てます。"),
                        BLANK,
                        row(pic(Pic.CLICK), " メニューで赤黒や"),
                        row("   ダズンをクリック"),
                        row(pic(Pic.CHIP), " 何か所にも置ける"),
                        row(pic(Pic.CLOCK), " 締め切りで回る"),
                        BLANK,
                        row(pic(Pic.SNEAK), " 賭けたあとは立って"),
                        row("   ホイールを見に行ける"),
                        note("8 ブロック以内なら歩ける"),
                        note("椅子のクリックで座り直す")),
                page("配当 1（賭け金は別）",
                        row("1 つ（ストレート）"),
                        row(pic(Pic.R_STRAIGHT), " 35 倍"),
                        BLANK,
                        row("2 つ（スプリット）"),
                        row(pic(Pic.R_SPLIT), " 17 倍"),
                        BLANK,
                        row("3 つ（ストリート）"),
                        row(pic(Pic.R_STREET), " 11 倍"),
                        BLANK,
                        row("4 つ（コーナー）"),
                        row(pic(Pic.R_CORNER), " 8 倍"),
                        BLANK),
                page("配当 2（賭け金は別）",
                        row("6 つ（ライン）"),
                        row(pic(Pic.R_LINE), " 5 倍"),
                        BLANK,
                        row("12 個（ダズン）"),
                        row(pic(Pic.R_DOZEN), " 2 倍"),
                        BLANK,
                        row("12 個（縦の段）"),
                        row(pic(Pic.R_COLUMN), " 2 倍"),
                        BLANK,
                        row("赤黒・偶奇・前半後半"),
                        row(pic(Pic.R_RED), " 1 倍"),
                        BLANK),
                page("0 のとき",
                        row(pic(Pic.R_ZERO), " 0"),
                        BLANK,
                        row("赤黒・偶奇・前半後半・ダズン・縦の段はすべて負け。"),
                        BLANK,
                        row("0 に賭けたときと、"),
                        row("0 を含む賭けは当たり"),
                        BLANK,
                        note("ヨーロピアン（0 は 1 つ）")));
    }

    static List<Component> baccarat() {
        return List.of(
                page("バカラ",
                        row("9 に近いほうを当てる"),
                        row(pic(Pic.PLAYER), " プレイヤー"),
                        row(pic(Pic.BANKER), " バンカー"),
                        note("どちらも札の置き場所の名前"),
                        BLANK,
                        label("点の数え方"),
                        row(cards("AH 9S"), " 1 と 9"),
                        BLANK,
                        row(cards("TC JD QS KH"), " 0"),
                        BLANK,
                        row(cards("7S 8D"), " 15 → 5"),
                        BLANK,
                        note("1 の位だけを見ます")),
                page("配り方",
                        row("2 枚ずつ配る"),
                        row(cards("4S 5H"), " 9 なら終わり"),
                        BLANK,
                        note("8 か 9（ナチュラル）"),
                        BLANK,
                        row(pic(Pic.PLAYER), " 5 以下なら 3 枚目"),
                        row(cards("2S 3H ??")),
                        BLANK,
                        row(pic(Pic.BANKER), " 引くかはバンカーの"),
                        row("   点と相手の 3 枚目"),
                        note("ディーラーが自動で配ります")),
                standing(),
                page("配当（戻る額）",
                        row(pic(Pic.PLAYER), " プレイヤー 2 倍"),
                        row(pic(Pic.BANKER), " バンカー 1.95 倍"),
                        note("   （5% の手数料）"),
                        row(pic(Pic.TIE), " タイ 9 倍"),
                        BLANK,
                        row("ペア 12 倍"),
                        row(cards("8S 8H"), note(" 最初の 2 枚")),
                        BLANK,
                        note("タイのとき、プレイヤーとバンカーへの賭けはそのまま戻ります。")));
    }

    static List<Component> bigWheel() {
        return List.of(
                page("ビッグホイール",
                        row("上の針が指したマスの"),
                        row("絵柄を当てる"),
                        BLANK,
                        row(wheel(Segment.ONE), wheel(Segment.TWO), wheel(Segment.FIVE), wheel(Segment.TEN),
                                wheel(Segment.TWENTY), wheel(Segment.JOKER), wheel(Segment.LOGO)),
                        BLANK,
                        row("全部で 54 マス"),
                        row("絵柄ごとにマスの数と"),
                        row("配当が違う"),
                        BLANK,
                        note("針の上に、いま指しているマスの絵柄が出ます。")),
                standing(),
                page("配当（戻る額）",
                        row(wheel(Segment.ONE), " 24 マス  2 倍"),
                        row(wheel(Segment.TWO), " 15 マス  3 倍"),
                        row(wheel(Segment.FIVE), "  7 マス  6 倍"),
                        row(wheel(Segment.TEN), "  4 マス 11 倍"),
                        row(wheel(Segment.TWENTY), "  2 マス 21 倍"),
                        row(wheel(Segment.JOKER), "  1 マス 41 倍"),
                        row(wheel(Segment.LOGO), "  1 マス 41 倍"),
                        BLANK,
                        note("マスが少ないほど配当が大きい")));
    }

    static List<Component> chinchiro() {
        return List.of(
                page("チンチロ",
                        row(pic(Pic.BOWL), " どんぶりに 3 個振る"),
                        BLANK,
                        row("親（ディーラー）と子"),
                        row("（あなた）が役で勝負"),
                        BLANK,
                        row(pic(Pic.CHIP), " 子として張る"),
                        row(pic(Pic.ARROW), " まず親が振る"),
                        row(pic(Pic.ARROW), " 子が順に振る"),
                        BLANK,
                        row(pic(Pic.CLICK), " 自分の番は [振る]"),
                        row("   かどんぶりをクリック"),
                        note("3 回まで振れて、役が出たら"),
                        note("そこで止まる")),
                page("役（強い順）",
                        row(dice(1, 1, 1), " ピンゾロ 5 倍"),
                        row(dice(6, 6, 6), " ゾロ目 3 倍"),
                        row(dice(4, 5, 6), " シゴロ 2 倍"),
                        row(dice(2, 2, 6), " 6 の目 1 倍"),
                        row(dice(5, 5, 1), " 1 の目 1 倍"),
                        row(dice(2, 4, 6), " 目なし"),
                        row(dice(1, 2, 3), " ヒフミ 2 倍払う"),
                        BLANK,
                        note("2 個そろうと残りが「目」"),
                        note("数が大きいほど強い"),
                        note("3 回とも役なしは目なし")),
                page("勝負の決まり方",
                        label("親がこれなら子の負け"),
                        row(dice(4, 5, 6), " シゴロ以上"),
                        row(dice(3, 3, 6), " 6 の目"),
                        label("親がこれなら子の勝ち"),
                        row(dice(1, 2, 3), " ヒフミ 2 倍"),
                        row(dice(4, 4, 1), " 1 の目"),
                        row(dice(2, 4, 6), " 目なし"),
                        BLANK,
                        label("親が 2〜5 の目なら"),
                        row("子が振って比べる"),
                        row("強いほうの倍率で払う"),
                        note("同じなら引き分け")),
                page("張る額",
                        row(dice(1, 1, 1), " 親のピンゾロは"),
                        row("   賭け金の 5 倍を払う"),
                        BLANK,
                        row(pic(Pic.CHIP), " 張ったあとも賭け金の"),
                        row("   4 倍を手元に残す"),
                        BLANK,
                        note("勝負の間は席を立って歩けますが、自分の番は自動で振られます。")));
    }

    static List<Component> highLow() {
        return List.of(
                page("ハイ＆ロー",
                        row("次のカードを当てる"),
                        BLANK,
                        row(cards("7S"), " → ", cards("QH"), " ", pic(Pic.HIGH), " ハイ"),
                        BLANK,
                        row(cards("7S"), " → ", cards("3D"), " ", pic(Pic.LOW), " ロー"),
                        BLANK,
                        row(cards("7S"), " → ", cards("7C"), " ", pic(Pic.SAME), " 同じ"),
                        BLANK,
                        note("ハイとローは同じ数が出たら負け")),
                page("数え方",
                        row(cards("AS"), " 1 が一番小さい"),
                        BLANK,
                        row(cards("JH QD KC"), " 11 12 13"),
                        BLANK,
                        note("マークは関係ありません"),
                        BLANK,
                        row("めくったカードが"),
                        row("次の基準になる"),
                        note("奥に今までのカードが並ぶ")),
                standing(),
                page("配当（戻る額）",
                        row("当たりやすさで変わる"),
                        BLANK,
                        label("基準が 7 なら"),
                        row(pic(Pic.HIGH), " ハイ 2.05 倍"),
                        row(pic(Pic.LOW), " ロー 2.05 倍"),
                        row(pic(Pic.SAME), " 同じ 12.35 倍"),
                        BLANK,
                        row(cards("AS"), " ローは賭けられない"),
                        BLANK,
                        row(cards("KS"), " ハイは賭けられない"),
                        BLANK,
                        note("今の倍率は奥のオッズ板に出ます")));
    }

    static List<Component> horseRace() {
        return List.of(
                page("競馬",
                        row("1 着の馬を当てる（単勝）"),
                        BLANK,
                        row(silk(0), " 白  ", silk(1), " 黒  ", silk(2), " 赤"),
                        row(silk(3), " 青  ", silk(4), " 黄  ", silk(5), " 緑"),
                        BLANK,
                        row("6 頭が左のゲートから"),
                        row("右のゴールへ走る"),
                        BLANK,
                        row(pic(Pic.CHIP), " 賭けたチップは席の"),
                        row("   前のベット台に積む")),
                standing(),
                page("配当（戻る額）",
                        row("レースごとに馬の強さと"),
                        row("配当が変わる"),
                        BLANK,
                        label("例"),
                        row(silk(0), " 1.8 倍 強い"),
                        row(silk(3), " 4.5 倍"),
                        row(silk(5), " 12 倍 大穴"),
                        BLANK,
                        row("倍率が低いほど強い馬"),
                        BLANK,
                        note("今の倍率は奥のオッズ板に、"),
                        note("勝つ確率はメニューに出る")));
    }
}
