package io.github.r0319.luckycasino.paper;

/**
 * カジノホールデムのテーブル上の位置。フェルトの絵（tools/gen_games.py の holdem_felt）と数値を合わせること。
 * 手前から席ごとの手札 2 枚、ANTE と CALL の丸。中央に場の 5 枚、奥にディーラーの 2 枚。
 */
final class HoldemLayout implements TableShape {

    static final int SEATS = 5;
    static final double HOLE_Z = 0.62;
    static final double BET_Z = 0.22;
    static final double BET_DX = 0.17;
    static final double BOARD_Z = -0.22;
    static final double DEALER_Z = -0.66;
    static final double CARD_STEP = 0.21;
    static final double BOARD_STEP = 0.27;

    static double seatX(int seat) {
        return 1.6 - 0.8 * seat;
    }

    @Override
    public double seatRise() {
        return RAISED_SEAT;
    }

    @Override
    public int seats() {
        return SEATS;
    }

    @Override
    public double[] seat(int index) {
        return new double[]{seatX(index), 0, 1.55};
    }

    @Override
    public double[] dealer() {
        return new double[]{0, 0, -1.55};
    }

    @Override
    public String model() {
        return "table/holdem";
    }

    /** 席 seat の手札 k 枚目（0 か 1）。 */
    double[] hole(int seat, int k) {
        return new double[]{seatX(seat) + (k - 0.5) * CARD_STEP, TOP + 0.004 + 0.003 * k, HOLE_Z};
    }

    double[] ante(int seat) {
        return new double[]{seatX(seat) - BET_DX, TOP + 0.001, BET_Z};
    }

    double[] call(int seat) {
        return new double[]{seatX(seat) + BET_DX, TOP + 0.001, BET_Z};
    }

    /** 場の index 枚目（0〜4）。 */
    double[] board(int index) {
        return new double[]{(index - 2) * BOARD_STEP, TOP + 0.004, BOARD_Z};
    }

    /** ディーラーの k 枚目。 */
    double[] dealerCard(int k) {
        return new double[]{(k - 0.5) * CARD_STEP, TOP + 0.004 + 0.003 * k, DEALER_Z};
    }

    /** 席の手の名前と勝ち負けを出す位置（手札の上）。 */
    double[] handLabel(int seat) {
        return new double[]{seatX(seat), TOP + 0.42, HOLE_Z};
    }

    double[] dealerLabel() {
        return new double[]{0, TOP + 0.45, DEALER_Z};
    }

    double[] deck() {
        return new double[]{1.5, TOP + 0.05, -0.75};
    }

    double[] croupierChips() {
        return new double[]{-1.35, TOP + 0.001, -0.6};
    }
}
