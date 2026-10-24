package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.util.UUID;

/** 参加した Java 版プレイヤーにカード用リソースパックを送る（統合版は Geyser 拡張が配る）。 */
final class ResourcePackListener implements Listener {

    private static final UUID PACK_ID = UUID.fromString("6c1b6f0e-6a55-4d0b-9d63-3f7c2b1f4c11");

    private final ResourcePackRequest request;

    ResourcePackListener(String url, String sha1) {
        this.request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), sha1))
                .required(false)
                .prompt(Component.text("LuckyCasino のカード表示用リソースパックです"))
                .build();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendResourcePacks(request);
    }
}
