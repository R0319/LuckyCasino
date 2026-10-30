package io.github.r0319.luckycasino.roulette;

import java.util.Set;

/** ヨーロピアンルーレット（0 が 1 つ、37 ポケット）。 */
public final class Wheel {

    public static final int POCKETS = 37;

    /** ホイール上の並び（時計回り）。 */
    private static final int[] ORDER = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24,
            16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};

    private static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private Wheel() {
    }

    /** ホイール上の index 番目のポケットの数字。 */
    public static int numberAt(int index) {
        return ORDER[Math.floorMod(index, POCKETS)];
    }

    /** 数字がホイール上の何番目か。 */
    public static int indexOf(int number) {
        for (int i = 0; i < POCKETS; i++) {
            if (ORDER[i] == number) {
                return i;
            }
        }
        throw new IllegalArgumentException("invalid number: " + number);
    }

    public static boolean red(int number) {
        return RED.contains(number);
    }

    public static boolean black(int number) {
        return number != 0 && !RED.contains(number);
    }
}
