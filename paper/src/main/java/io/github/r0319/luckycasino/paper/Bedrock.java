package io.github.r0319.luckycasino.paper;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 統合版（Geyser / Floodgate）の人かどうか。Floodgate がこのサーバーに入っていればその API で調べ、
 * 無ければ（プロキシにだけ入っているとき）Floodgate が作る UUID の形（上位 64 ビットが 0）で見分ける。
 */
final class Bedrock {

    private static final Object API;
    private static final Method IS_FLOODGATE;

    static {
        Object api = null;
        Method method = null;
        try {
            Class<?> type = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            api = type.getMethod("getInstance").invoke(null);
            method = type.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            // Floodgate が無い
        }
        API = api;
        IS_FLOODGATE = method;
    }

    private Bedrock() {
    }

    static boolean is(Player player) {
        return is(player.getUniqueId());
    }

    static boolean is(UUID id) {
        if (API != null) {
            try {
                return (boolean) IS_FLOODGATE.invoke(API, id);
            } catch (ReflectiveOperationException e) {
                // 下の見分け方にまかせる
            }
        }
        return id.getMostSignificantBits() == 0;
    }
}
