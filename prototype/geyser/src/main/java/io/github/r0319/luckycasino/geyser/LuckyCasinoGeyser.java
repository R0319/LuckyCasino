package io.github.r0319.luckycasino.geyser;

import io.github.r0319.luckycasino.bridge.BridgeProtocol;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.api.entity.property.BatchPropertyUpdater;
import org.geysermc.geyser.api.entity.property.type.GeyserBooleanEntityProperty;
import org.geysermc.geyser.api.entity.property.type.GeyserIntEntityProperty;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * 統合版プレイヤーに、Paper が出した「運び役」の TextDisplay を独自のカードエンティティとして見せる Geyser 拡張。
 * Java 版プレイヤーには運び役は何も描かれない（文字も背景も空）ので、Java 側の見た目は ItemDisplay だけになる。
 */
public final class LuckyCasinoGeyser implements Extension, BridgeServer.Listener {

    private static final String PACK_FILE = "luckycasino.mcpack";

    private final CardRegistry registry = new CardRegistry();
    private Identifier textDisplay;
    private CustomEntityDefinition cardEntity;
    private GeyserIntEntityProperty cardProperty;
    private GeyserBooleanEntityProperty faceUpProperty;
    private GeyserBooleanEntityProperty settledProperty;
    private Properties config;
    private BridgeServer server;

    @Subscribe
    public void onPreInitialize(GeyserPreInitializeEvent event) {
        try {
            config = loadConfig();
        } catch (IOException e) {
            logger().error("config.properties を読み込めませんでした", e);
        }
    }

    @Subscribe
    public void onDefineEntities(GeyserDefineEntitiesEvent event) {
        textDisplay = Identifier.of("minecraft:text_display");
        cardEntity = CustomEntityDefinition.of("luckycasino:card");
        event.register(cardEntity);
    }

    @Subscribe
    public void onDefineProperties(GeyserDefineEntityPropertiesEvent event) {
        Identifier card = cardEntity.identifier();
        cardProperty = event.registerIntegerProperty(card, Identifier.of("luckycasino:card"), 0, 52, 0);
        faceUpProperty = event.registerBooleanProperty(card, Identifier.of("luckycasino:face_up"), false);
        settledProperty = event.registerBooleanProperty(card, Identifier.of("luckycasino:settled"), false);
    }

    @Subscribe
    public void onDefineResourcePacks(GeyserDefineResourcePacksEvent event) {
        Path pack = dataFolder().resolve(PACK_FILE);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(PACK_FILE)) {
            if (in == null) {
                logger().error(PACK_FILE + " が jar に含まれていません");
                return;
            }
            Files.createDirectories(dataFolder());
            Files.copy(in, pack, StandardCopyOption.REPLACE_EXISTING);
            event.register(ResourcePack.create(PackCodec.path(pack)));
        } catch (IOException e) {
            logger().error("統合版リソースパックを登録できませんでした", e);
        }
    }

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        if (config == null) {
            logger().error("設定が無いため Paper との通信を開始しません");
            return;
        }
        try {
            InetAddress bind = InetAddress.getByName(config.getProperty("bind-address"));
            int port = Integer.parseInt(config.getProperty("port"));
            server = new BridgeServer(logger(), registry, this, config.getProperty("key"), bind, port);
            server.start();
        } catch (IOException | IllegalArgumentException e) {
            logger().error("Paper との通信を開始できませんでした（config.properties の bind-address / port を確認）", e);
        }
    }

    @Subscribe
    public void onShutdown(GeyserShutdownEvent event) {
        if (server != null) {
            server.stop();
        }
    }

    @Subscribe
    public void onSpawn(ServerSpawnEntityEvent event) {
        if (cardEntity == null || !event.entityType().is(textDisplay)) {
            return;
        }
        CardRegistry.CardState state = registry.get(event.uuid());
        if (state == null) {
            return; // 他のプラグインの TextDisplay はそのまま
        }
        event.definition(cardEntity);
        event.preSpawnConsumer(entity -> {
            // TextDisplay は名前タグ表示のため大きさ 0 にされるので戻す
            entity.override(GeyserEntityDataTypes.SCALE, 1.0f);
            entity.override(GeyserEntityDataTypes.HITBOXES, List.of());
            entity.updatePropertiesBatched(updater -> apply(updater, state), false);
        });
    }

    @Override
    public void onCard(UUID carrier, CardRegistry.CardState state) {
        for (GeyserConnection connection : GeyserApi.api().onlineConnections()) {
            GeyserEntity entity = connection.entities().byUuid(carrier);
            if (entity != null && entity.definition().equals(cardEntity)) {
                entity.updatePropertiesBatched(updater -> apply(updater, state), true);
            }
        }
    }

    @Override
    public void onRemove(UUID carrier) {
        // 運び役が消えれば Geyser がエンティティを消すので、ここでは何もしない
    }

    private void apply(BatchPropertyUpdater updater, CardRegistry.CardState state) {
        updater.update(cardProperty, state.code());
        updater.update(faceUpProperty, state.faceUp());
        updater.update(settledProperty, state.settled());
    }

    private Properties loadConfig() throws IOException {
        Path file = dataFolder().resolve("config.properties");
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        }
        boolean changed = false;
        if (props.getProperty("bind-address") == null) {
            props.setProperty("bind-address", "0.0.0.0");
            changed = true;
        }
        if (props.getProperty("port") == null) {
            props.setProperty("port", String.valueOf(BridgeProtocol.DEFAULT_PORT));
            changed = true;
        }
        String key = props.getProperty("key", "");
        if (key.length() < BridgeProtocol.MIN_KEY_LENGTH) {
            props.setProperty("key", BridgeProtocol.newKey());
            changed = true;
            logger().info("共有鍵を新しく作り config.properties に保存しました。同じ値を Paper 側の config.yml の bridge.key に設定してください");
        }
        if (changed) {
            Files.createDirectories(dataFolder());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                props.store(writer, "LuckyCasino Geyser extension. key must match bridge.key in the Paper plugin's config.yml");
            }
        }
        return props;
    }
}
