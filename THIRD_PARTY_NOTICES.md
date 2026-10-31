# Third-party notices / 第三者のライセンス表記

LuckyCasino itself is released under the MIT License (see `LICENSE`).
This file lists everything from other people that the project uses, and whether it is shipped in the release files.

LuckyCasino 本体は MIT License です（`LICENSE`）。ここには、ほかの人が作ったもので使っているものと、それを配布物に含めているかをまとめます。

## Release files / 配布物

| File | Contains |
| --- | --- |
| `LuckyCasino-Paper-<version>.jar` | LuckyCasino code only (`common` + `paper`) |
| `LuckyCasino-Geyser-<version>.jar` | LuckyCasino code only (`common` + `geyser`) and the Bedrock resource pack |
| `LuckyCasino-java-pack.zip` | LuckyCasino models, fonts and textures only |

No third-party code, font or texture is bundled in these files.
配布する 3 つのファイルには、ほかの人のコード・フォント・テクスチャは入っていません。

## Used at build time or run time, not bundled / 使うが同梱しないもの

| Name | Used for | License |
| --- | --- | --- |
| [Paper API](https://github.com/PaperMC/Paper) | Compile against the server API (`compileOnly`) | GPL-3.0 (parts MIT), see the Paper repository |
| [VaultAPI](https://github.com/MilkBowl/VaultAPI) | Optional economy hook (`compileOnly`) | LGPL-3.0 |
| [Geyser API](https://github.com/GeyserMC/Geyser) | Geyser extension (`compileOnly`) | MIT |
| [JUnit 5](https://junit.org/junit5/) | Unit tests only | EPL-2.0 |
| [Gradle Wrapper](https://gradle.org/) (`gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`) | Build | Apache-2.0 |
| [Pillow](https://python-pillow.org/) | `tools/*.py` texture generators (not needed to build) | MIT-CMU (HPND) |
| [GNU Unifont](https://unifoundry.com/unifont/) | `tools/preview/rulebook.py` draws sample pages with the font installed on the machine; the font itself is not in this repository | GPL-2.0-or-later with font embedding exception / OFL-1.1 |

## Loaded from a CDN by the preview pages / プレビューのページが CDN から読み込むもの

`dist/preview.html` and `dist/designs.html` are developer previews (like `dist/rulebook.html`) and are not part of the plugin.

| Name | License |
| --- | --- |
| [three.js r128](https://github.com/mrdoob/three.js) (with `OrbitControls`) | MIT |
| [DotGothic16](https://fonts.google.com/specimen/DotGothic16), [IBM Plex Sans JP](https://fonts.google.com/specimen/IBM+Plex+Sans+JP) (Google Fonts) | OFL-1.1 |

## Minecraft assets / Minecraft の素材

All textures in `resourcepacks/` are drawn by the scripts in `tools/`.
Some models point to vanilla textures by name (for example `minecraft:block/dark_oak_planks`); those textures are not copied into this project and come from the player's own game.

`resourcepacks/` のテクスチャはすべて `tools/` のスクリプトで描いたものです。一部のモデルはバニラのテクスチャを名前で参照していますが、テクスチャ自体はこのプロジェクトに含めておらず、プレイヤーのゲームにあるものを使います。

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
