# Hangar に載せるときの入力内容

Hangar（https://hangar.papermc.io ）でプロジェクトを作るときに入れる値の控えです。ガイドライン（https://hangar.papermc.io/guidelines 、2025-12-30 更新）に合わせています。

| 項目 | 入れる値 | メモ |
| --- | --- | --- |
| Name | `LuckyCasino` | 名前に「Paper」や版、宣伝文句を入れてはいけない |
| Summary | `Casino tables with animated cards, chips and wheels: blackjack, poker, roulette, slots and five more games.` | |
| Category | Games | いちばん近い分類を選ぶ決まり |
| License | MIT | `LICENSE` と同じにする |
| Source | https://github.com/R0319/LuckyCasino | |
| Platform | Paper 26.2 | 選んだ platform で実際に読み込める必要がある。Velocity は選ばない（Geyser 拡張は Velocity プラグインではない） |
| Dependencies | Vault（任意） | 経済プラグインが無くても動く |
| Files | `LuckyCasino-Paper-<版>.jar` を Hangar へ直接アップロード | 外部リンクより直接アップロードが推奨。難読化は禁止（していない） |

リソースパック（`LuckyCasino-java-pack.zip`）と Geyser 拡張（`LuckyCasino-Geyser-<版>.jar`）は Paper プラグインではないので、GitHub Releases に置いて説明文からリンクします。

## 説明文（Description、そのまま貼れます）

```markdown
LuckyCasino adds casino tables you can place and play in survival or on a hub.
Cards, chips, wheels and reels are real 3D models (ItemDisplay + resource pack), and every deal, spin and payout is animated.

> **All in-game text (menus, chat, rule books) is currently Japanese only.**

## Games
- Blackjack (5 seats, 6 decks, dealer stands on soft 17, 3:2)
- Texas hold'em poker (6 seats, no-limit, side pots)
- European roulette (5 seats, inside and outside bets)
- Slot machine (3 reels, 5 fixed lines)
- Baccarat, big wheel, chinchiro (Japanese dice), high & low, horse racing

## Features
- Uses your server economy through Vault / VaultUnlocked: players buy chips when they sit down and cash out when they leave. Without an economy plugin, free practice chips are used. Only in-game money is involved.
- Chip balances are journaled, so players get their chips back exactly once even after a crash.
- Every bet has a return to player below 100%, shoes are reshuffled every round, and table chunks stay loaded while players are seated.
- Craftable tables (configurable recipes), picture rule books, an achievements tab, and a dealer whose look can be changed per table.
- Respects protection plugins: tables cannot be placed where the player cannot build.

## Requirements
- Paper or Purpur 26.2
- Optional: Vault or VaultUnlocked with an economy plugin
- The resource pack (download from GitHub Releases) must be sent to Java players with `resource-pack.url` and `resource-pack.sha1` in `config.yml`.

## Links
- Source, setup guide and resource pack: https://github.com/R0319/LuckyCasino

## Use of generative AI
Most of the code, and the scripts that draw the textures, were written by an AI coding assistant (Claude) under the author's direction. The author chose the games, rules, payouts and look, and tested everything on their own server. No AI image generator was used.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
```

## 画像

アイコンとギャラリーには、自分のサーバーで撮ったスクリーンショットを使います（Modrinth に出す場合も、生成 AI の画像は使えないため）。

## 出す前に確かめること

- Hangar の Platform の版の一覧に 26.2 があるか（まだ確かめていません）
- アップロードする jar が GitHub Releases と同じ版か（今は v0.2.0）
