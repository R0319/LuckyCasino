package io.github.r0319.luckycasino.holdem;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Shoe;
import io.github.r0319.luckycasino.poker.HandEvaluator;
import io.github.r0319.luckycasino.poker.HandValue;
import io.github.r0319.luckycasino.poker.HandValue.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * カジノホールデム（1 人ずつディーラーと勝負するホールデム）の 1 回分。
 * 参加料（アンティ）を賭けると手札 2 枚と場の 3 枚が配られ、降りる（アンティを失う）か、アンティの 2 倍をコールする。
 * コールすると場の残り 2 枚とディーラーの 2 枚を開け、7 枚の中の一番強い 5 枚で比べる。
 * ディーラーが 4 のワンペア以上でなければ（不成立）、アンティは配当表どおりに払い、コールは戻す。
 * 成立して勝てばアンティは配当表どおり・コールは 1:1、負ければ両方を失い、引き分けは両方戻す。
 * 席が何人いても場とディーラーの手は共通で、手札だけが席ごとに違う。
 */
public final class CasinoHoldem {

    /** コールの額はアンティの何倍か。 */
    public static final int CALL_MULTIPLE = 2;

    public enum Outcome {
        FOLD("フォールド"),
        NOT_QUALIFIED("ディーラー不成立"),
        WIN("勝ち"),
        LOSE("負け"),
        TIE("引き分け");

        private final String displayName;

        Outcome(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final List<List<Card>> hole;
    private final List<Card> board;
    private final List<Card> dealer;
    private final HandValue dealerBest;

    private CasinoHoldem(List<List<Card>> hole, List<Card> board, List<Card> dealer) {
        this.hole = hole;
        this.board = board;
        this.dealer = dealer;
        this.dealerBest = HandEvaluator.best(join(dealer, board));
    }

    /** players 人分の手札・場の 5 枚・ディーラーの 2 枚を配る（手札 → 場 → ディーラーの順に引く）。 */
    public static CasinoHoldem deal(Shoe shoe, int players) {
        List<List<Card>> hole = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            hole.add(List.of(shoe.draw(), shoe.draw()));
        }
        List<Card> board = List.of(shoe.draw(), shoe.draw(), shoe.draw(), shoe.draw(), shoe.draw());
        List<Card> dealer = List.of(shoe.draw(), shoe.draw());
        return new CasinoHoldem(List.copyOf(hole), board, dealer);
    }

    /** 決まったカードで作る（テスト用）。 */
    public static CasinoHoldem of(List<List<Card>> hole, List<Card> board, List<Card> dealer) {
        return new CasinoHoldem(List.copyOf(hole), List.copyOf(board), List.copyOf(dealer));
    }

    private static List<Card> join(List<Card> a, List<Card> b) {
        List<Card> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    public int players() {
        return hole.size();
    }

    public List<Card> hole(int player) {
        return hole.get(player);
    }

    public List<Card> board() {
        return board;
    }

    /** コールを決める前に見える場の 3 枚。 */
    public List<Card> flop() {
        return board.subList(0, 3);
    }

    public List<Card> dealer() {
        return dealer;
    }

    public HandValue playerBest(int player) {
        return HandEvaluator.best(join(hole.get(player), board));
    }

    public HandValue dealerBest() {
        return dealerBest;
    }

    /** ディーラーの手が 4 のワンペア以上か。 */
    public boolean dealerQualifies() {
        return qualifies(dealerBest);
    }

    static boolean qualifies(HandValue value) {
        if (value.category().compareTo(Category.ONE_PAIR) > 0) {
            return true;
        }
        // ワンペアの score は、ペアの数字が 16 ビット目から入っている
        return value.category() == Category.ONE_PAIR && ((value.score() >> 16) & 0xF) >= 4;
    }

    /** アンティの配当（賭け金の何倍を足して払うか）。ストレート以下は 1 倍。 */
    public static int anteMultiplier(HandValue value) {
        if (value.royal()) {
            return 100;
        }
        return switch (value.category()) {
            case STRAIGHT_FLUSH -> 20;
            case FOUR_OF_A_KIND -> 10;
            case FULL_HOUSE -> 3;
            case FLUSH -> 2;
            default -> 1;
        };
    }

    public Outcome outcome(int player, boolean called) {
        if (!called) {
            return Outcome.FOLD;
        }
        if (!dealerQualifies()) {
            return Outcome.NOT_QUALIFIED;
        }
        int cmp = playerBest(player).compareTo(dealerBest);
        return cmp > 0 ? Outcome.WIN : cmp < 0 ? Outcome.LOSE : Outcome.TIE;
    }

    /** アンティ ante に戻るチップ（賭け金を含む。負けは 0）。 */
    public long antePayout(int player, boolean called, long ante) {
        return switch (outcome(player, called)) {
            case FOLD, LOSE -> 0;
            case TIE -> ante;
            case NOT_QUALIFIED, WIN -> ante * (1 + anteMultiplier(playerBest(player)));
        };
    }

    /** コール call に戻るチップ（賭け金を含む）。 */
    public long callPayout(int player, long call) {
        return switch (outcome(player, true)) {
            case FOLD, LOSE -> 0;
            case NOT_QUALIFIED, TIE -> call;
            case WIN -> call * 2;
        };
    }

    /**
     * 手札と場の 3 枚から、コールするかの目安（ボットと還元率の試算に使う。完全な戦略より 1% ほど損）。
     * 手札を使ったワンペア以上、フラッシュかストレートまであと 1 枚、手札に 10 以上か場のどれより高い札があればコール。
     * 場だけのペアはディーラーにも付くので数えない。
     */
    public static boolean suggestCall(List<Card> hole, List<Card> flop) {
        List<Card> five = join(hole, flop);
        int a = HandEvaluator.value(hole.get(0).rank());
        int b = HandEvaluator.value(hole.get(1).rank());
        boolean holePaired = a == b;
        int boardHigh = 0;
        for (Card card : flop) {
            int v = HandEvaluator.value(card.rank());
            holePaired |= v == a || v == b;
            boardHigh = Math.max(boardHigh, v);
        }
        if (holePaired) {
            return true;
        }
        int[] suits = new int[4];
        boolean[] ranks = new boolean[15];
        for (Card card : five) {
            suits[card.suit().ordinal()]++;
            int v = HandEvaluator.value(card.rank());
            ranks[v] = true;
            if (v == 14) {
                ranks[1] = true;
            }
        }
        for (int s : suits) {
            if (s >= 4) {
                return true;
            }
        }
        // 5 つ続きの数字のうち 4 つがある（内側が抜けていてもよい）
        for (int low = 1; low <= 10; low++) {
            int have = 0;
            for (int k = 0; k < 5; k++) {
                if (ranks[low + k]) {
                    have++;
                }
            }
            if (have >= 4) {
                return true;
            }
        }
        int high = Math.max(a, b);
        return high >= 10 || high > boardHigh;
    }
}
