package io.github.r0319.luckycasino.poker;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Rank;
import io.github.r0319.luckycasino.card.Suit;
import io.github.r0319.luckycasino.poker.HandValue.Category;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandEvaluatorTest {

    /** "As" "Td" "9h" のような表記（T = 10、s/h/d/c）。 */
    static Card c(String s) {
        Rank rank = switch (s.charAt(0)) {
            case 'A' -> Rank.ACE;
            case 'K' -> Rank.KING;
            case 'Q' -> Rank.QUEEN;
            case 'J' -> Rank.JACK;
            case 'T' -> Rank.TEN;
            default -> Rank.values()[s.charAt(0) - '1'];
        };
        Suit suit = switch (s.charAt(1)) {
            case 's' -> Suit.SPADE;
            case 'h' -> Suit.HEART;
            case 'd' -> Suit.DIAMOND;
            default -> Suit.CLUB;
        };
        return new Card(suit, rank);
    }

    static List<Card> cards(String text) {
        return Arrays.stream(text.split(" ")).map(HandEvaluatorTest::c).toList();
    }

    static HandValue best(String text) {
        return HandEvaluator.best(cards(text));
    }

    @Test
    void categories() {
        assertEquals(Category.STRAIGHT_FLUSH, best("As Ks Qs Js Ts 2d 3c").category());
        assertEquals("ロイヤルフラッシュ", best("As Ks Qs Js Ts 2d 3c").displayName());
        assertEquals("ストレートフラッシュ", best("9h 8h 7h 6h 5h Ad Ac").displayName());
        assertTrue(best("As Ks Qs Js Ts 2d 3c").royal());
        assertFalse(best("9h 8h 7h 6h 5h Ad Ac").royal());
        assertFalse(best("5d 4d 3d 2d Ad Kc Kh").royal());
        assertEquals(Category.FOUR_OF_A_KIND, best("7s 7h 7d 7c Kd 2c 3c").category());
        assertEquals(Category.FULL_HOUSE, best("Qs Qh Qd 4c 4d 2c 9s").category());
        assertEquals(Category.FLUSH, best("Kd 9d 6d 4d 2d As Ac").category());
        assertEquals(Category.STRAIGHT, best("8s 7h 6d 5c 4d Kc Kd").category());
        assertEquals(Category.THREE_OF_A_KIND, best("5s 5h 5d Kc 2d 9c Jd").category());
        assertEquals(Category.TWO_PAIR, best("Js Jh 3d 3c Ad 8c 2s").category());
        assertEquals(Category.ONE_PAIR, best("Ts Th Kd 6c 3d 8c 2s").category());
        assertEquals(Category.HIGH_CARD, best("As Jh 8d 6c 2d 4c 9s").category());
    }

    @Test
    void wheelIsTheLowestStraight() {
        HandValue wheel = best("As 2h 3d 4c 5d Kc Kd");
        HandValue six = best("2h 3d 4c 5d 6s Kc Kd");
        assertEquals(Category.STRAIGHT, wheel.category());
        assertTrue(six.score() > wheel.score());
    }

    @Test
    void kickersBreakTies() {
        assertTrue(best("As Ah Kd 7c 3d 2c 4s").score() > best("As Ah Qd Jc Td 2c 4s").score());
        assertTrue(best("Ks Kh 9d 9c Ad 2c 3s").score() > best("Ks Kh 9d 9c Qd 2c 3s").score());
        assertTrue(best("Qs Qh Qd 2c 2d").score() > best("Js Jh Jd Ac Ad").score());
        assertTrue(best("Kd 9d 6d 4d 3d").score() > best("Kc 9c 6c 4c 2c").score());
    }

    @Test
    void identicalStrengthTies() {
        // 共有カードのストレートを両者が使う
        assertEquals(best("Ts 9h 8d 7c 6d 2c 3s").score(), best("Ts 9h 8d 7c 6d 2h 4s").score());
        // フラッシュのスートが違っても同じ強さ
        assertEquals(best("Kd 9d 6d 4d 2d").score(), best("Kh 9h 6h 4h 2h").score());
    }

    @Test
    void picksBestFiveOfSeven() {
        // 7 枚の中にフラッシュとストレートの両方があればフラッシュ
        assertEquals(Category.FLUSH, best("9h 8h 7h 6d 5h 2h Kc").category());
        // ツーペアが 3 組なら上の 2 組 + 一番高い残り
        HandValue value = best("As Ah Ks Kh 2s 2h Qd");
        assertEquals(Category.TWO_PAIR, value.category());
        assertTrue(value.score() > best("As Ah Ks Kh Jd 2s 2h").score());
    }
}
