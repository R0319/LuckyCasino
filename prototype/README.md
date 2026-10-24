# カード 1 枚の表示試作（方式 C の検証）

Java 版は ItemDisplay + リソースパック、統合版は自作 Geyser 拡張で独自エンティティに差し替える方式が、実機で動くかを確かめるための試作です。リポジトリ直下の旧プラグイン（Paper 1.21.11）とは別のビルドで、旧コードには手を入れていません。

## しくみ

```
Paper（hub / survival）                         Velocity
 ├ ItemDisplay … Java 版に見えるカード            └ Geyser-Velocity
 └ TextDisplay（文字も背景も空）… 運び役              └ LuckyCasino 拡張
      Java 版では何も描かれない                          運び役を luckycasino:card に差し替え
        │                                                  ↑
        └── LAN 内 TCP（共有鍵で認証）: 「この運び役は♠A・表向き」 ──┘
```

- 運び役は、拡張が登録を受け取る（ACK）まで誰にも送りません。先に統合版へ届くと差し替えが間に合わないためです。
- 裏向きのカードは番号 0 で送るので、通信や統合版クライアントから伏せ札は読めません。Java 版も裏向きの間は裏面のモデルだけを持たせています。
- 配る動き・めくる動きは、Java 版は Display の補間、統合版はリソースパック内のアニメーション（エンティティプロパティで切り替え）で再生します。

## ビルド

Java 25 が必要です（Paper 26.2 の API が Java 25 向けのため）。

```
./gradlew -p prototype build
```

| 出力 | 置き場所 |
| --- | --- |
| `prototype/paper/build/libs/LuckyCasino-Paper-0.1.0-SNAPSHOT.jar` | Paper（Purpur）の `plugins/` |
| `prototype/geyser/build/libs/LuckyCasino-Geyser-0.1.0-SNAPSHOT.jar` | Velocity の `plugins/Geyser-Velocity/extensions/`（統合版パックを同梱、自動で配信） |
| `prototype/paper/build/resourcepack/LuckyCasino-java-pack.zip` | Java 版プレイヤーへ配信（`server.properties` の `resource-pack` など） |

## 設定

1. Geyser 拡張を入れて Velocity を起動すると `plugins/Geyser-Velocity/extensions/luckycasino/config.properties` ができ、共有鍵 `key` が自動生成されます。`bind-address`（既定 `0.0.0.0`）と `port`（既定 `25590`）もここで変えられます。
2. Paper 側の `plugins/LuckyCasino/config.yml` に、Velocity マシンの LAN 内 IP（`bridge.host`）、ポート、同じ `key` を書いて再起動します。
3. ポートは LAN 内からだけ届くようにしてください（インターネット側には開けない）。

Geyser は API 2.11.0 以上（Minecraft 26.2 対応版）が必要です。

## ブラックジャックの遊び方

1. テーブルを手に入れる：クラフト（緑のカーペット 3・板材 3・金インゴット 1）か、管理者が `/casino give`
2. ブロックの上面に右クリックで置く（4 x 2 ブロック、高さ 2 の空きが必要。手前に椅子、奥にディーラーが出る）
3. 椅子をクリック → チップに両替して座る（経済プラグインが無いサーバーでは練習用チップを無料で配る）
4. 賭け金メニューで額を決める。全員が決めるか、締め切り（15 秒）で配り始める
5. 自分の番になったらチャットのボタン（ヒット / スタンド / ダブルダウン / スプリット）を押す。20 秒で自動スタンド
6. スニークで席を立つとチップをお金に戻す（勝負中なら、勝負が終わってから精算）

置いた人はスニーク + 右クリックでテーブルを片付けられます（座っている人がいないときだけ）。

| コマンド | 内容 |
| --- | --- |
| `/casino hit` `stand` `double` `split` | 自分の番の行動（チャットのボタンと同じ） |
| `/casino bet` | 賭け金メニューを開き直す |
| `/casino leave` | 席を立つ |
| `/casino give [プレイヤー]` | テーブルを渡す（管理者） |
| `/casino remove` | 近くのテーブルを片付ける（管理者。座っている人は精算） |
| `/casino status` | 通貨と Geyser 拡張の状態（管理者） |

チップの控えを `plugins/LuckyCasino/chips.yml` に書いているので、勝負の途中でサーバーが落ちても、次の起動時に賭け金を含めてお金に戻します。

## 実機で確かめること

- 統合版でカードが表示され、配る・めくるのアニメーションが動くか（方式 C の成否）
- 統合版でカードの向き（エンティティの向きが反映されるか）、表面の絵柄が鏡写しになっていないか
- Java 版で表面の絵柄の向き（モデルの下面の UV）
- 統合版の配る方向が Java 版と同じ（奥から手前）か
- 後から近づいた統合版プレイヤーに、配る動きが再生されずに置かれた状態で見えるか

## テクスチャの作り直し

`prototype/tools/gen_cards.py`（Python 3 + Pillow）が 52 枚 + 裏面の画像、Java 版のモデル定義、統合版のエンティティ定義を生成します。生成物はコミット済みなので、ビルドに Python は不要です。

## Java 版リソースパックの配信

`config.yml` の `resource-pack.url` と `resource-pack.sha1` を設定すると、参加した Java 版プレイヤーにパックを送ります。試験用に、ビルド済みの zip を `prototype/dist/` に置いています（GitHub の raw URL をコミット固定で指定して配信）。パックを変えたら `./gradlew -p prototype build` のあと zip をここへコピーし、SHA-1 を更新してください。
