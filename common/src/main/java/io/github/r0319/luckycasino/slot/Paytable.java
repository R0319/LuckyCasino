package io.github.r0319.luckycasino.slot;

/**
 * 配当表。どれも賭け金の何倍が戻るか（賭け金を含む）。中央の 1 ラインだけを見る。
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

    /** 還元率 96% 前後、当たる確率 22% 前後。 */
    public static final Paytable DEFAULT = new Paytable(300, 50, 20, 15, 10, 1, 4, 20);

    public Paytable {
        for (long n : new long[]{sevens, bars, bells, melons, lemons, cherry1, cherry2, cherry3}) {
            if (n < 0) {
                throw new IllegalArgumentException("payout must not be negative");
            }
        }
    }

    /** 中央のラインの絵柄から、何倍戻るかと役の名前。外れは倍率 0。 */
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
            return new Win(times, "チェリー " + count + " つ", count == 3);
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
            return new Win(times, a.displayName() + " が 3 つ", a == Symbol.SEVEN || a == Symbol.BAR);
        }
        return Win.NONE;
    }

    /** 全部の止まり方を数えた還元率（戻る額の期待値 ÷ 賭け金）。 */
    public double returnRate() {
        return expected(true);
    }

    /** 何か戻ってくる確率。 */
    public double hitRate() {
        return expected(false);
    }

    private double expected(boolean weighted) {
        double sum = 0;
        Symbol[] line = new Symbol[Reels.REELS];
        for (int i = 0; i < Reels.STOPS; i++) {
            for (int j = 0; j < Reels.STOPS; j++) {
                for (int k = 0; k < Reels.STOPS; k++) {
                    line[0] = Reels.at(0, i);
                    line[1] = Reels.at(1, j);
                    line[2] = Reels.at(2, k);
                    long times = evaluate(line).times();
                    sum += weighted ? times : (times > 0 ? 1 : 0);
                }
            }
        }
        return sum / (Reels.STOPS * Reels.STOPS * Reels.STOPS);
    }

    /**
     * 役。
     *
     * @param times 賭け金の何倍が戻るか（0 なら外れ）
     * @param name  役の名前
     * @param big   大当たり（演出を大きくする）
     */
    public record Win(long times, String name, boolean big) {
        public static final Win NONE = new Win(0, "", false);
    }
}
