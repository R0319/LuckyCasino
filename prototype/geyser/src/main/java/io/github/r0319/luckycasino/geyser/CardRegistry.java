package io.github.r0319.luckycasino.geyser;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Paper から知らされた「どの運び役がどのカードか」の一覧。 */
final class CardRegistry {

    static final int MAX_CARDS = 4096;

    record CardState(int code, boolean faceUp, boolean settled, long owner) {
    }

    private final Map<UUID, CardState> cards = new ConcurrentHashMap<>();

    /** 上限を超える新規登録は拒否して false を返す。 */
    boolean put(UUID carrier, CardState state) {
        if (!cards.containsKey(carrier) && cards.size() >= MAX_CARDS) {
            return false;
        }
        cards.put(carrier, state);
        return true;
    }

    CardState get(UUID carrier) {
        return cards.get(carrier);
    }

    void remove(UUID carrier, long owner) {
        cards.computeIfPresent(carrier, (uuid, state) -> state.owner() == owner ? null : state);
    }

    /** Paper との接続が切れたら、その接続が登録したカードを捨てる（再接続時に送り直される）。 */
    void removeOwner(long owner) {
        cards.values().removeIf(state -> state.owner() == owner);
    }
}
