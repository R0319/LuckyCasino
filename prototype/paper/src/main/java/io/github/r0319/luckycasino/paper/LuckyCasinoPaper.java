package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bridge.BridgeProtocol;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** カード 1 枚の表示試作。/lccard で配る・めくる・片付けるを試す。 */
public final class LuckyCasinoPaper extends JavaPlugin {

    /** プレイヤーごとに 1 枚だけ持てる。メインスレッドからだけ触る。 */
    private final Map<UUID, PrototypeCard> cards = new HashMap<>();
    private BridgeClient bridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String host = getConfig().getString("bridge.host", "127.0.0.1");
        int port = getConfig().getInt("bridge.port", BridgeProtocol.DEFAULT_PORT);
        String key = getConfig().getString("bridge.key", "");

        bridge = new BridgeClient(getLogger(), host, port, key, this::stateLines);
        if (!getConfig().getBoolean("bridge.enabled", true)) {
            getLogger().info("bridge.enabled が false のため Geyser 拡張とは通信しません");
        } else if (key.length() < BridgeProtocol.MIN_KEY_LENGTH) {
            getLogger().warning("bridge.key が未設定です。Geyser 拡張の config.properties の key を config.yml に設定してください");
        } else {
            bridge.start();
        }

        registerCommand("lccard", "カード表示の試作", new CardCommand(this));
    }

    @Override
    public void onDisable() {
        cards.values().forEach(PrototypeCard::remove);
        cards.clear();
        if (bridge != null) {
            bridge.stop();
        }
    }

    BridgeClient bridge() {
        return bridge;
    }

    Map<UUID, PrototypeCard> cards() {
        return cards;
    }

    /** 通信スレッドから呼ばれるので、メインスレッドで今のカード一覧を作って返す。 */
    private List<String> stateLines() {
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        getServer().getScheduler().runTask(this, () -> {
            List<String> lines = new ArrayList<>();
            cards.values().forEach(card -> lines.add(card.stateLine()));
            future.complete(lines);
        });
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return List.of();
        }
    }
}
