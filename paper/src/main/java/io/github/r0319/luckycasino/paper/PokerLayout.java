package io.github.r0319.luckycasino.paper;

/**
 * ポーカーのテーブル上の位置。席は右端（席 1）から手前を通って左端（席 6）まで、上から見て時計回り。
 * 手札の枠はフェルトの絵（tools/gen_table.py の POKER_SEATS、poker_card_spot）と合わせること。
 */
final class PokerLayout implements TableShape {

    static final int SEATS = 6;
    private static final double[][] SEAT = {
            {2.55, -0.2}, {1.2, 1.55}, {0.4, 1.55}, {-0.4, 1.55}, {-1.2, 1.55}, {-2.55, -0.2}};
    private static final double BOARD_Z = -0.2;

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
        return new double[]{SEAT[index][0], 0, SEAT[index][1]};
    }

    @Override
    public double[] dealer() {
        return new double[]{0, 0, -1.55};
    }

    @Override
    public String model() {
        return "table/poker";
    }

    /** 端の席のカードは、その席の方を向くようにテーブルから回す角度。 */
    static float yawOffset(int seat) {
        return seat == 0 ? -90f : seat == SEATS - 1 ? 90f : 0f;
    }

    private static boolean end(int seat) {
        return seat == 0 || seat == SEATS - 1;
    }

    /** 席の手札 k 枚目（0 か 1）。2 枚を少し重ねて並べる。 */
    double[] holeCard(int seat, int k) {
        double spread = (k - 0.5) * 0.12;
        double y = TOP + 0.004 + 0.003 * k;
        if (seat == 0) {
            return new double[]{1.55, y, BOARD_Z - spread};
        }
        if (seat == SEATS - 1) {
            return new double[]{-1.55, y, BOARD_Z + spread};
        }
        return new double[]{SEAT[seat][0] + spread, y, 0.45};
    }

    /** 席の前に出す、このラウンドの賭け金。 */
    double[] bet(int seat) {
        if (end(seat)) {
            return new double[]{seat == 0 ? 1.15 : -1.15, TOP + 0.001, 0.25};
        }
        return new double[]{SEAT[seat][0], TOP + 0.001, 0.1};
    }

    /** ディーラーボタンの置き場所（手札の横）。 */
    double[] button(int seat) {
        if (end(seat)) {
            return new double[]{seat == 0 ? 1.6 : -1.6, TOP + 0.01, 0.2};
        }
        return new double[]{SEAT[seat][0] + 0.26, TOP + 0.01, 0.6};
    }

    double[] board(int index) {
        return new double[]{-0.6 + 0.3 * index, TOP + 0.004, BOARD_Z};
    }

    double[] pot() {
        return new double[]{0, TOP + 0.001, 0.2};
    }

    /** 名前・チップ・直前の行動を出す位置（席の上）。 */
    double[] seatLabel(int seat) {
        double[] p = seat(seat);
        return new double[]{p[0], 1.75 + seatRise(), p[2]};
    }

    /** カードを配り出す位置（ディーラーの手元）。 */
    double[] deck() {
        return new double[]{0.3, TOP + 0.05, -0.75};
    }

    double[] potLabel() {
        return new double[]{0, TOP + 0.45, 0.2};
    }
}
