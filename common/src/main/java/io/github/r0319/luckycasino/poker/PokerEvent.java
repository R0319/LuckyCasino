package io.github.r0319.luckycasino.poker;

import io.github.r0319.luckycasino.card.Card;

import java.util.List;

/**
 * 1 回の勝負の中で起きたこと。表示側はこれを順に再生する。HoleCards は本人にだけ見せること。
 */
public sealed interface PokerEvent {

    record ButtonMoved(int seat) implements PokerEvent {
    }

    record Blind(int seat, long amount, boolean big) implements PokerEvent {
    }

    /** 手札。本人以外には中身を見せない。 */
    record HoleCards(int seat, Card first, Card second) implements PokerEvent {
    }

    /** 行動を選ぶ番が来た。 */
    record Turn(int seat, Legal legal) implements PokerEvent {
    }

    /**
     * 行動した。added はこのとき出したチップ、streetBet はこのラウンドで出した合計。
     */
    record Acted(int seat, PokerAction action, long added, long streetBet, boolean allIn) implements PokerEvent {
    }

    /** ラウンドの賭けをポットに集めた。pot は集めた後のポットの合計。 */
    record BetsCollected(long pot) implements PokerEvent {
    }

    /** 共有カードを公開した（フロップ 3 枚、ターン 1 枚、リバー 1 枚）。 */
    record Board(Street street, List<Card> cards) implements PokerEvent {
    }

    /** 誰も合わせられなかった分を返した。 */
    record Refund(int seat, long amount) implements PokerEvent {
    }

    /** ショーダウンで手札を見せた。 */
    record Shown(int seat, Card first, Card second, HandValue value) implements PokerEvent {
    }

    /**
     * ポットを獲得した。value はショーダウンでの役（全員降りて勝ったときは null）。
     *
     * @param pot 0 がメインポット、1 以降がサイドポット
     */
    record Won(int seat, long amount, int pot, HandValue value) implements PokerEvent {
    }

    record HandOver() implements PokerEvent {
    }
}
