package io.github.r0319.luckycasino.paper;

/** テーブルの種類。id は保存ファイルとアイテムの印に使う。 */
enum GameType {
    BLACKJACK("blackjack", "ブラックジャック", "ブラックジャックテーブル", false),
    ROULETTE("roulette", "ルーレット", "ルーレットテーブル", false),
    POKER("poker", "ポーカー", "ポーカーテーブル", false),
    SLOT("slot", "スロット", "スロットマシン", true),
    BACCARAT("baccarat", "バカラ", "バカラテーブル", false),
    BIGWHEEL("bigwheel", "ビッグホイール", "ビッグホイールテーブル", false),
    CHINCHIRO("chinchiro", "チンチロ", "チンチロの座敷", false),
    HIGHLOW("highlow", "ハイ＆ロー", "ハイ＆ローテーブル", false),
    HORSERACE("horserace", "競馬", "競馬テーブル", false),
    HOLDEM("holdem", "カジノホールデム", "カジノホールデムテーブル", false);

    private final String id;
    private final String displayName;
    private final String itemName;
    private final boolean singleBlock;

    GameType(String id, String displayName, String itemName, boolean singleBlock) {
        this.id = id;
        this.displayName = displayName;
        this.itemName = itemName;
        this.singleBlock = singleBlock;
    }

    /** 設置アイテムの名前。 */
    String itemName() {
        return itemName;
    }

    /** 賭け場所を選んで賭ける卓（SpotTable）か。設定は config.yml の同じ名前の節（SpotSettings）。 */
    boolean spotBets() {
        return this == BACCARAT || this == BIGWHEEL || this == CHINCHIRO || this == HIGHLOW || this == HORSERACE || this == HOLDEM;
    }

    /** 1 ブロックの台（ブロックの中心に置く）か。 */
    boolean singleBlock() {
        return singleBlock;
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
