package io.github.r0319.luckycasino.paper;

/** テーブルの種類。id は保存ファイルとアイテムの印に使う。 */
enum GameType {
    BLACKJACK("blackjack", "ブラックジャック"),
    ROULETTE("roulette", "ルーレット"),
    POKER("poker", "ポーカー");

    private final String id;
    private final String displayName;

    GameType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    static GameType byId(String id) {
        for (GameType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
