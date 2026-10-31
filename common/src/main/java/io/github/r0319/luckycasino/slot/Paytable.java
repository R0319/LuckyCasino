package io.github.r0319.luckycasino.slot;

/**
 * 配当表。どれも 1 ラインの賭け金の何倍が戻るか（賭け金を含む）。5 本のラインをそれぞれこの表で見る。
 *
 * @param sevens  7 が 3 つ
 * @param bars    BAR が 3 つ
 * @param bells   ベルが 3 つ
 * @param melons  スイカが 3 つ
 * @param lemons  レモンが 3 つ
 * @param cherry1 左のリールだけチェリー
 * @param cherry2 左から 2 つチェリー
 * @param cherry3 チェリーが 3 つ
 */
public record Paytable(long sevens, long bars, long bells, long melons, long lemons, long cherry1, long cherry2, long cherry3) {

    /** 還元率 96% 前後、何か戻る確率 65% 前後（5 ライン）。 */
    public static final Paytable DEFAULT = new Paytable(300, 50, 20, 15, 10, 1, 4, 20);

    public Paytable {
        for (long n : new long[]{sevens, bars, bells, melons, lemons, cherry1, cherry2, cherry3}) {
            if (n < 0) {
                throw new IllegalArgumentException("payout must not be negative");
            }
        }
    }

    /** 1 本のラインの絵柄（左から）から、何倍戻るかと役の名前。外れは倍率 0。 */
    public Win evaluate(Symbol[] line) {
        Symbol a = line[0];
        Symbol b = line[1];
        Symbol c = line[2];
        if (a == Symbol.CHERRY) {
            int count = b == Symbol.CHERRY ? (c == Symbol.CHERRY ? 3 : 2) : 1;
            long times = switch (count) {
                case 3 -> cherry3;
                case 2 -> cherry2;
                default -> cherry1;
            };
            return new Win(times, Symbol.CHERRY, count, count == 3);
        }
        if (a == b && b == c) {
            long times = switch (a) {
                case SEVEN -> sevens;
                case BAR -> bars;
                case BELL -> bells;
                case MELON -> melons;
                case LEMON -> lemons;
                case CHERRY -> cherry3;
            };
            return new Win(times, a, 3, a == Symbol.SEVEN || a == Symbol.BAR);
        }
        return Win.NONE;
    }

    /**
     * 全部の止まり方を数えた還元率（戻る額の期待値 ÷ 賭け金）。どのラインも絵柄の出方は同じなので、
     * 1 ラインの還元率がそのまま 5 ライン全体の還元率になる。
     */
    public double returnRate() {
        double sum = 0;
        Symbol[] line = new Symbol[Reels.REELS];
        for (int i = 0; i < Reels.STOPS; i++) {
            for (int j = 0; j < Reels.STOPS; j++) {
                for (int k = 0; k < Reels.STOPS; k++) {
                    line[0] = Reels.at(0, i);
                    line[1] = Reels.at(1, j);
                    line[2] = Reels.at(2, k);
                    sum += evaluate(line).times();
                }
            }
        }
        return sum / (Reels.STOPS * Reels.STOPS * Reels.STOPS);
    }

    /** 5 本のどれかで何か戻ってくる確率（賭け金より少ない戻りも含む）。 */
    public double hitRate() {
        int hits = 0;
        int[] stops = new int[Reels.REELS];
        for (stops[0] = 0; stops[0] < Reels.STOPS; stops[0]++) {
            for (stops[1] = 0; stops[1] < Reels.STOPS; stops[1]++) {
                for (stops[2] = 0; stops[2] < Reels.STOPS; stops[2]++) {
                    for (Line l : Line.values()) {
                        if (evaluate(l.symbols(stops)).times() > 0) {
                            hits++;
                            break;
                        }
                    }
                }
            }
        }
        return (double) hits / (Reels.STOPS * Reels.STOPS * Reels.STOPS);
    }

    /**
     * 役。
     *
     * @param times 1 ラインの賭け金の何倍が戻るか（0 なら外れ）
     * @param name  役の名前
     * @param big   大当たり（演出を大きくする）
     */
    /** 当たり。symbol は左からそろった絵柄、count はその数（チェリー以外は 3）。 */
    public record Win(long times, Symbol symbol, int count, boolean big) {
        public static final Win NONE = new Win(0, null, 0, false);

        /** 役の名前（日本語）。 */
        public String name() {
            if (symbol == null) {
                return "";
            }
            return symbol == Symbol.CHERRY ? "チェリー " + count + " つ" : symbol.displayName() + " が 3 つ";
        }
    }
}
