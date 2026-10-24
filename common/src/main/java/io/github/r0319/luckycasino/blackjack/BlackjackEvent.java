package io.github.r0319.luckycasino.blackjack;

import io.github.r0319.luckycasino.card.Card;

import java.util.List;

/**
 * 勝負の中で起きたこと。表示側はこれを順に再生する（配る動き、めくる動き、チップの移動など）。
 * 裏向きのカードは card を null にして、中身を表示側に渡さない。
 */
public sealed interface BlackjackEvent {

    /** 席に配られたカード。 */
    record PlayerCard(int seat, int hand, Card card) implements BlackjackEvent {
    }

    /** ディーラーに配られたカード。hidden のとき card は null。 */
    record DealerCard(Card card, boolean hidden) implements BlackjackEvent {
    }

    /** ディーラーが伏せていたカードをめくった。 */
    record HoleCardRevealed(Card card) implements BlackjackEvent {
    }

    /** スプリットで手が 2 つに分かれた。2 枚目は newHand へ移る。 */
    record Split(int seat, int hand, int newHand) implements BlackjackEvent {
    }

    /** ダブルダウンで賭け金が倍になった。 */
    record Doubled(int seat, int hand, long bet) implements BlackjackEvent {
    }

    /** 行動を選ぶ番が来た。 */
    record Turn(int seat, int hand, List<Action> actions) implements BlackjackEvent {
    }

    /** 手が終わった（スタンド、バースト、21、ダブルダウン後など）。 */
    record HandDone(int seat, int hand, int total, boolean bust) implements BlackjackEvent {
    }

    /** ディーラーの番が終わった。 */
    record DealerDone(int total, boolean bust) implements BlackjackEvent {
    }

    /** 1 つの手の精算結果。payout は戻ってくるチップ（賭け金を含む）。 */
    record Settled(int seat, int hand, Outcome outcome, long bet, long payout) implements BlackjackEvent {
    }

    /** 勝負が終わった。 */
    record RoundOver() implements BlackjackEvent {
    }
}
