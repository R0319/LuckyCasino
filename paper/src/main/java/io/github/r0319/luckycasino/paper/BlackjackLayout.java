package io.github.r0319.luckycasino.paper;

/** ブラックジャックのテーブル上の位置。フェルトの絵（tools/gen_table.py）と数値を合わせること。 */
final class BlackjackLayout implements TableShape {

    static final int SEATS = 5;
    static final float CARD_SCALE = 0.4f;

    private static final double CARD_Z = 0.25;
    private static final double CHIP_Z = 0.68;
    private static final double SEAT_Z = 1.55;
    private static final double DEALER_Z = -1.55;
    private static final double DEALER_CARD_Z = -0.55;

    static double seatX(int seat) {
        return 1.6 - 0.8 * seat;
    }

    @Override
    public int seats() {
        return SEATS;
    }

    @Override
    public double[] seat(int seat) {
        return new double[]{seatX(seat), 0, SEAT_Z};
    }

    @Override
    public double[] dealer() {
        return new double[]{0, 0, DEALER_Z};
    }

    @Override
    public String model() {
        return "table/blackjack";
    }

    double[] shoe() {
        return new double[]{1.4, TOP + 0.05, -0.6};
    }

    /** 席の手札 hand（hands 個のうち）の index 枚目のカード。 */
    double[] playerCard(int seat, int hand, int hands, int index) {
        double base = seatX(seat) + (hand - (hands - 1) / 2.0) * 0.34;
        return new double[]{base + 0.06 * index, TOP + 0.004 + 0.003 * index, CARD_Z - 0.05 * index};
    }

    double[] dealerCard(int index) {
        return new double[]{-0.2 + 0.14 * index, TOP + 0.004 + 0.003 * index, DEALER_CARD_Z};
    }

    double[] chips(int seat) {
        return new double[]{seatX(seat), TOP + 0.001, CHIP_Z};
    }

    double[] dealerChips() {
        return new double[]{-0.9, TOP + 0.001, -0.6};
    }

    /** 手札の合計を表示する位置。 */
    double[] handLabel(int seat, int hand, int hands) {
        double[] card = playerCard(seat, hand, hands, 0);
        return new double[]{card[0], TOP + 0.45, card[2]};
    }

    double[] dealerLabel() {
        return new double[]{0, TOP + 0.7, DEALER_CARD_Z};
    }
}
