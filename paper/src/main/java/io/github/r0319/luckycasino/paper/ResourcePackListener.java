package io.github.r0319.luckycasino.paper;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Java 版プレイヤーにカード用リソースパックを送る（統合版は Geyser 拡張が配る）。
 * 参加の前（configuration フェーズの読み込み画面）に送り、読み込みが終わるまで参加を待たせるので、
 * プレイ中に画面が止まらない。読み込めたかどうかを Glyphs に伝え、読み込めた人にだけフォントの絵を出す。
 */
final class ResourcePackListener implements Listener {

    private static final UUID PACK_ID = UUID.fromString("6c1b6f0e-6a55-4d0b-9d63-3f7c2b1f4c11");
    /** 返事が来ないときに参加を待たせる上限（受け入れの確認画面を開いたままの人もいるので長め）。 */
    private static final long WAIT_SECONDS = 60;

    private final ResourcePackRequest request;

    ResourcePackListener(String url, String sha1) {
        this.request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), sha1))
                .required(false)
                .prompt(L10n.tr("LuckyCasino のカード表示用リソースパックです"))
                .build();
    }

    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        UUID player = connection.getProfile().getId();
        // Floodgate の UUID は上位 64 ビットが 0（参加前は Floodgate の API がまだ知らないことがある）
        if (player == null || player.getMostSignificantBits() == 0 || Bedrock.is(player)) {
            return;
        }
        Glyphs.packLoaded(player, false);
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        connection.getAudience().sendResourcePacks(request.callback((id, status, audience) -> {
            if (PACK_ID.equals(id) && !status.intermediate()) {
                done.complete(status == ResourcePackStatus.SUCCESSFULLY_LOADED);
            }
        }));
        // このイベントが終わるまで参加は進まない（非同期のスレッドなので待ってよい）
        try {
            Glyphs.packLoaded(player, done.get(WAIT_SECONDS, TimeUnit.SECONDS));
        } catch (TimeoutException | ExecutionException e) {
            // 返事がまだ無い。あとで届けば onStatus が直す
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 参加したあとに届いた返事（待ち切れなかったとき・読み込み直しで外れたとき）。 */
    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> Glyphs.packLoaded(event.getPlayer().getUniqueId(), true);
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> Glyphs.packLoaded(event.getPlayer().getUniqueId(), false);
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Glyphs.packLoaded(event.getPlayer().getUniqueId(), false);
    }
}
