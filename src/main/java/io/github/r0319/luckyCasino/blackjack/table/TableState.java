package io.github.r0319.luckyCasino.blackjack.table;

public enum TableState {
    /** Accepting new players. */
    WAITING,
    /** Players are placing bets. */
    BETTING,
    /** Initial cards are being dealt. */
    DEALING,
    /** Each player takes their action in turn. */
    PLAYER_TURN,
    /** Dealer reveals and draws. */
    DEALER_TURN,
    /** Payouts are calculated and distributed. */
    SETTLEMENT
}
