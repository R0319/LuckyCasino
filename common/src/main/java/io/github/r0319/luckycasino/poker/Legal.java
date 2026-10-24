package io.github.r0319.luckycasino.poker;

/**
 * 今の番で選べる行動。
 *
 * @param toCall     コールに必要な額（0 ならチェックできる）
 * @param canRaise   ベット・レイズできるか
 * @param minRaiseTo ベット・レイズの最低額（このラウンドで出す合計）。手持ちが足りなければ maxRaiseTo と同じ
 * @param maxRaiseTo オールインしたときのこのラウンドの合計
 * @param streetBet  このラウンドで既に出している額
 */
public record Legal(long toCall, boolean canRaise, long minRaiseTo, long maxRaiseTo, long streetBet) {

    public boolean canCheck() {
        return toCall == 0;
    }

    /** コールの額が手持ちを超える（コールするとオールインになる）。 */
    public boolean callIsAllIn() {
        return toCall >= maxRaiseTo - streetBet;
    }
}
