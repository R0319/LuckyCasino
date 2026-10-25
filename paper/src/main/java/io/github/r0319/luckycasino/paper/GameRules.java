package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.text.Component;

import java.util.List;

import static io.github.r0319.luckycasino.paper.Rulebook.page;

/** 賭け場所を選んで賭けるゲーム（バカラなど）のルールブックのページ。 */
final class GameRules {

    private GameRules() {
    }

    private static Component standing() {
        return page("賭け方と立って歩く",
                "メニューで賭ける場所をクリックしてチップを置きます。何か所にも置け、締め切りで勝負が始まります。",
                "",
                "賭けたあとと勝負の間は、スニークで立っても精算しません。テーブルのクリックで賭けのメニュー、自分の椅子のクリックで座り直し。");
    }

    static List<Component> baccarat() {
        return List.of(
                page("バカラ",
                        "プレイヤーとバンカー、どちらの点が 9 に近いかを当てます。プレイヤーもバンカーも、遊ぶ人ではなく札の置き場所の名前です。",
                        "",
                        "点は A が 1、2〜9 はその数、10 と絵札は 0。合計の 1 の位だけを見ます（7 + 8 = 15 → 5）。"),
                page("配り方",
                        "両方に 2 枚ずつ配ります。どちらかが 8 か 9（ナチュラル）なら終わり。",
                        "",
                        "プレイヤーは 5 以下なら 3 枚目を引きます。バンカーが引くかは、バンカーの点とプレイヤーの 3 枚目で決まります。ディーラーが自動で配ります。"),
                standing(),
                page("配当（戻る額）",
                        "プレイヤー: 2 倍",
                        "バンカー: 2 倍から 5% の手数料（100 枚なら 195 枚）",
                        "タイ: 9 倍",
                        "ペア（最初の 2 枚が同じ数）: 12 倍",
                        "",
                        "タイのとき、プレイヤーとバンカーへの賭けはそのまま戻ります。"));
    }
}
