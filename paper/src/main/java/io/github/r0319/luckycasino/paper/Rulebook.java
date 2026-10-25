package io.github.r0319.luckycasino.paper;

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
        List<Component> pages = new ArrayList<>(common());
        pages.addAll(switch (type) {
            case BLACKJACK -> blackjack();
            case ROULETTE -> roulette();
            case POKER -> poker();
        });
        player.openBook(Book.book(Component.text(type.displayName() + "のルール"), Component.text("LuckyCasino"), pages));
    }

    private static Component page(String title, String... lines) {
        Component page = Component.text(title, NamedTextColor.DARK_BLUE).decorate(TextDecoration.BOLD);
        for (String line : lines) {
            page = page.append(Component.newline()).append(Component.text(line, NamedTextColor.BLACK).decoration(TextDecoration.BOLD, false));
        }
        return page;
    }

    private static List<Component> common() {
        return List.of(page("席とチップ",
                "椅子をクリックして座り、チップに両替します。",
                "",
                "スニークで席を立つと、残りのチップはお金に戻ります。",
                "",
                "勝負の途中で立った場合は、その勝負が終わってから精算します（手番は自動で進みます）。"));
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
                page("回っている間",
                        "「ノーモアベット」から払い戻しまでは、スニークで立っても精算しません。歩いてホイールの近くで見られます。",
                        "",
                        "自分の椅子かテーブルをクリックすると座り直せます。払い戻しのあと 30 秒戻らないか、16 ブロック以上離れると精算します。"),
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

    private static List<Component> poker() {
        return List.of(
                page("ポーカー",
                        "テキサスホールデム（ノーリミット）。ディーラーは配るだけで、チップはプレイヤーの間で動きます。",
                        "",
                        "手札 2 枚と場の 5 枚から、一番強い 5 枚で勝負します。"),
                page("流れ",
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
                        "自分の番になるとチャットにボタンが出ます。"),
                page("役（強い順）",
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
    }
}
