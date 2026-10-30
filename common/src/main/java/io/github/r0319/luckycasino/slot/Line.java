package io.github.r0319.luckycasino.slot;

/**
 * 当たりを見るライン（横 3 本と斜め 2 本の 5 本、いつも全部を見る）。窓にはリールごとに 3 コマ見えていて、
 * 中央の段が止まったコマ、上の段がその次のコマ（+1）、下の段が前のコマ（−1）。
 */
public enum Line {
    TOP("上の段", 1, 1, 1),
    MIDDLE("中央の段", 0, 0, 0),
    BOTTOM("下の段", -1, -1, -1),
    DOWN("右下がりの斜め", 1, 0, -1),
    UP("右上がりの斜め", -1, 0, 1);

    public static final int COUNT = values().length;

    private final String displayName;
    private final int[] rows;

    Line(String displayName, int... rows) {
        this.displayName = displayName;
        this.rows = rows;
    }

    public String displayName() {
        return displayName;
    }

    /** リール reel の段（上 +1、中央 0、下 −1）。 */
    public int row(int reel) {
        return rows[reel];
    }

    /** 止まったコマ（中央の段）から、このラインの絵柄を左から並べる。 */
    public Symbol[] symbols(int[] stops) {
        Symbol[] line = new Symbol[Reels.REELS];
        for (int i = 0; i < line.length; i++) {
            line[i] = Reels.at(i, stops[i] + rows[i]);
        }
        return line;
    }
}
