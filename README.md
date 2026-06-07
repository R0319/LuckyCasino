# LuckyCasino

Paper プラグイン — Minecraft サーバー向けブラックジャックカジノ。

## 必要環境

| 項目 | バージョン |
|------|-----------|
| Minecraft | 1.21.11 |
| サーバーソフト | [Paper](https://papermc.io/) 1.21.11+ |
| Java | 21 以上 |

## 依存関係

### 必須
なし — プラグイン単体で動作します（内蔵ウォレット使用）。

### ソフト依存（任意）

| プラグイン | 用途 |
|-----------|------|
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | 外部経済プラグインとのブリッジ API |
| [EssentialsX](https://essentialsx.net/) | Vault 経由で経済連携 |
| [ExcellentEconomy](https://github.com/nulli0n/ExcellentEconomy-spigot) | Vault 経由または直接 API で経済連携 |
| [DonutAuction](https://www.spigotmc.org/resources/donutauction.106843/) | Vault/ExcellentEconomy 経由で決済 |

> Vault がない場合は内蔵ウォレットが自動で有効になります（初期残高 1,000 コイン/プレイヤー）。

### ビルド依存

`build.gradle.kts` に定義されています。

```kotlin
dependencies {
    // Paper API (compileOnly — サーバーが提供)
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Vault API (compileOnly — サーバーが提供)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
}
```

ビルドツール: [Gradle](https://gradle.org/) 9.5.1  
ビルドプラグイン: [run-paper](https://github.com/jpenilla/run-task) 3.0.2（開発用サーバー起動）

## インストール

1. [Releases](../../releases) から最新の `LuckyCasino-x.x.x.jar` をダウンロード
2. サーバーの `plugins/` フォルダに配置
3. サーバーを起動（または `/reload confirm`）
4. 管理者として `/bj setdealer` → `/bj setplayer 1〜4` でテーブルを設定

## ビルド方法

```bash
git clone https://github.com/R0319/LuckyCasino.git
cd LuckyCasino
./gradlew build
# → build/libs/LuckyCasino-1.0-SNAPSHOT.jar
```

開発用サーバー起動（Paper 1.21.11 を自動ダウンロード）:

```bash
./gradlew runServer
```

## コマンド一覧

### プレイヤーコマンド

| コマンド | 説明 |
|---------|------|
| `/bj join` | テーブルに参加 |
| `/bj joindealer` | ディーラーポジションに参加 |
| `/bj leave` | テーブルを離れる |
| `/bj bet <金額>` | ベット（待機中は次ラウンドへの予約ベット） |
| `/bj hit` | カードを引く |
| `/bj stand` | スタンド |
| `/bj doubledown` | ダブルダウン（最初の2枚のみ） |
| `/bj info` | テーブル情報・スロット状況を表示 |
| `/wallet` | 残高確認 |
| `/pay <プレイヤー> <金額>` | プレイヤー間送金 |

### 管理者コマンド（`luckycasino.blackjack.admin` 権限 / OP）

| コマンド | 説明 |
|---------|------|
| `/bj setdealer [x y z [world]]` | ディーラー立ち位置を設定（省略時は現在地） |
| `/bj cleardealer` | ディーラー位置・NPC を削除 |
| `/bj setplayer <1-4> [x y z [world]]` | プレイヤースロット位置を設定 |
| `/bj clearplayer <1-4>` | プレイヤースロットを削除 |
| `/bj start` | ベットフェーズを強制開始 |
| `/bj testcard` | カードアニメーションのデバッグ表示 |

> 座標には `~` （相対指定）が使えます。例: `/bj setdealer ~ ~ ~`

## ゲームの流れ

```
参加 → 待機 → ベット(60秒) → ディール → プレイヤーターン → ディーラーターン → 精算 → 次ラウンド待機(10秒) → …
```

- **予約ベット**: 待機中に `/bj bet <金額>` で登録しておくと、ラウンド開始時に自動でベットされます
- **ディーラー NPC**: ディーラー位置が設定されている場合、人間ディーラーがいないときは NoAI のヴィレッジャーが自動スポーン
- **スロットマーカー**: 設定したスロット位置に 1/4 スケールの羊毛ブロックが表示されます

## 権限

| 権限ノード | 説明 | デフォルト |
|-----------|------|----------|
| `luckycasino.blackjack.player` | ゲームに参加・プレイできる | `true` |
| `luckycasino.blackjack.admin` | テーブルの設定・管理者操作 | OP |
| `luckycasino.wallet.admin` | `/wallet give/take/set` などの管理操作 | OP |

## ライセンス

[MIT License](LICENSE)
