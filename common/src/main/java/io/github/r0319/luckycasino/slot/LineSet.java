package io.github.r0319.luckycasino.slot;

import java.util.List;

/**
 * 賭けるラインの組み合わせ。どのラインも絵柄の出方は同じなので、どれを選んでも還元率は変わらない
 * （変わるのは 1 回の賭け金と、何か戻る確率）。
 */
public enum LineSet {
    ALL("5 ライン全部", List.of(Line.values())),
    ROWS("横 3 本", List.of(Line.TOP, Line.MIDDLE, Line.BOTTOM)),
    DIAGONALS("斜め 2 本", List.of(Line.DOWN, Line.UP)),
    CENTER("中央 1 本", List.of(Line.MIDDLE));

    private final String displayName;
    private final List<Line> lines;

    LineSet(String displayName, List<Line> lines) {
        this.displayName = displayName;
        this.lines = lines;
    }

    public String displayName() {
        return displayName;
    }

    /** 見るライン（Line の順）。 */
    public List<Line> lines() {
        return lines;
    }

    public int size() {
        return lines.size();
    }

    /** 次の組み合わせ（メニューのボタンで順に切り替える）。 */
    public LineSet next() {
        LineSet[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
